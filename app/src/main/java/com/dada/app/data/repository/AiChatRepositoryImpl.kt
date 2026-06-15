package com.dada.app.data.repository

import com.dada.app.BuildConfig
import com.dada.core.common.utils.LogUtil
import com.dada.core.database.dao.ChatMessageDao
import com.dada.core.database.entity.ChatMessageEntity
import com.dada.domain.aichat.repository.AiChatRepository
import com.dada.domain.aichat.repository.AiStreamChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AiChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
) : AiChatRepository {

    companion object {
        private const val TAG = "AiChat"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 用于在 OkHttp 回调线程外执行 Room 写入。
     * 不能用 runBlocking 直接阻塞 OkHttp 调度线程（会拖慢其他网络请求）。
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentCall: Call? = null

    // ============================== Room ==============================

    override fun observeMessages(): Flow<List<ChatMessageEntity>> = dao.getAllMessages()

    override suspend fun insertMessage(message: ChatMessageEntity) = dao.insertMessage(message)

    override suspend fun updateMessage(message: ChatMessageEntity) = dao.updateMessage(message)

    // ============================== 网络 + 持久化 ==============================

    override fun sendMessage(content: String): Flow<AiStreamChunk> = callbackFlow {
        // 1. 持久化用户消息
        val userEntity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            role = "USER",
            type = "TEXT",
            text = content,
        )
        dao.insertMessage(userEntity)

        // 2. 构建 API 上下文（从 Room 加载历史）
        val history = dao.getAllMessages().first()
        val apiMessages = JSONArray()
        history.forEach { entity ->
            apiMessages.put(JSONObject().apply {
                put("role", if (entity.role == "USER") "user" else "assistant")
                put("content", entity.text)
            })
        }

        // 3. 创建 AI 占位消息（流式过程中持续更新）
        val aiEntityId = UUID.randomUUID().toString()
        dao.insertMessage(ChatMessageEntity(
            id = aiEntityId,
            role = "AI",
            type = "TEXT",
            text = "",
        ))

        // 4. 发起 SSE 请求
        val body = JSONObject().apply {
            put("model", BuildConfig.AI_MODEL)
            put("messages", apiMessages)
            put("stream", true)
        }

        val request = Request.Builder()
            .url(BuildConfig.AI_API_URL)
            .addHeader("api-key", BuildConfig.AI_API_TOKEN)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val call = client.newCall(request)
        currentCall = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LogUtil.e(TAG, "<<< 请求失败: ${e.message}", e)
                trySend(AiStreamChunk(content = "请求失败: ${e.message}"))
                close()
            }

            @Suppress("BlockingMethodInNonBlockingContext")
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "无响应体"
                    LogUtil.e(TAG, "<<< 错误: ${response.code} $errorBody")
                    trySend(AiStreamChunk(content = "请求失败: HTTP ${response.code}"))
                    close()
                    return
                }

                val responseBody = response.body
                if (responseBody == null) {
                    LogUtil.e(TAG, "<<< 错误: 响应体为空")
                    trySend(AiStreamChunk(content = "请求失败: 响应为空"))
                    close()
                    return
                }
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                val fullContent = StringBuilder()
                val fullReasoning = StringBuilder()

                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (call.isCanceled()) break

                        val trimmed = line?.trim() ?: continue
                        if (trimmed.isEmpty()) continue
                        if (!trimmed.startsWith("data:")) continue

                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break

                        try {
                            val json = JSONObject(data)
                            val choices = json.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).getJSONObject("delta")

                                if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                                    fullReasoning.append(delta.getString("reasoning_content"))
                                }
                                if (delta.has("content") && !delta.isNull("content")) {
                                    fullContent.append(delta.getString("content"))
                                }

                                if (fullContent.isNotEmpty() || fullReasoning.isNotEmpty()) {
                                    trySend(AiStreamChunk(
                                        content = fullContent.toString(),
                                        reasoning = fullReasoning.toString(),
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "<<< 解析失败: $data", e)
                        }
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "<<< 流读取异常: ${e.message}", e)
                    if (fullContent.isEmpty()) {
                        trySend(AiStreamChunk(content = "读取响应失败: ${e.message}"))
                    }
                } finally {
                    reader.close()
                    response.close()
                }

                // 流结束：持久化最终 AI 回复
                // 不在 OkHttp 回调线程内 runBlocking 写 Room（会阻塞调度器线程，影响其他网络请求）
                val finalContent = fullContent.toString()
                if (finalContent.isNotEmpty()) {
                    ioScope.launch {
                        dao.updateMessage(ChatMessageEntity(
                            id = aiEntityId,
                            role = "AI",
                            type = "TEXT",
                            text = finalContent,
                        ))
                    }
                }

                close()
            }
        })

        awaitClose { call.cancel() }
    }

    override fun cancelRequest() {
        currentCall?.cancel()
        currentCall = null
    }
}
