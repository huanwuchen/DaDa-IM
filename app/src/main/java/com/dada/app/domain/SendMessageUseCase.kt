package com.dada.app.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.dada.core.database.UserPreferences
import com.dada.core.common.utils.media.ImageCompressor
import com.dada.core.network.media.MediaUploader
import com.dada.domain.chat.model.ChatPeer
import com.dada.domain.chat.model.MessageType
import com.dada.domain.chat.model.SendMessageResult
import com.dada.domain.chat.repository.ImChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 发送消息 UseCase
 *
 * 职责：
 *  - 输入校验
 *  - 媒体文件处理（压缩、上传、时长读取）
 *  - 文件内容编解码
 *  - 调用 [ImChatRepository] 接口持久化 + 推送
 *
 * 注意：依赖的是 [ImChatRepository] **接口**（位于 :domain），不依赖具体实现类。
 */
@Singleton
class SendMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ImChatRepository,
    private val mediaUploader: MediaUploader,
    private val userPreferences: UserPreferences,
) {

    suspend fun sendText(
        myUserId: Long,
        peer: ChatPeer,
        content: String,
    ): Boolean {
        if (content.isBlank()) return false
        return chatRepository.sendTextMessage(myUserId, peer, content)
    }

    suspend fun sendImage(
        myUserId: Long,
        peer: ChatPeer,
        sourceUri: Uri,
    ): SendMessageResult {
        val compressed = ImageCompressor.compress(context, sourceUri)
        val response = try {
            mediaUploader.upload(compressed, "image/jpeg")
        } finally {
            compressed.delete()
        }
        return chatRepository.persistAndPushMedia(
            myUserId, peer, MessageType.IMAGE, response, "[图片]"
        )
    }

    suspend fun sendVideo(
        myUserId: Long,
        peer: ChatPeer,
        sourceUri: Uri,
    ): SendMessageResult {
        val response = mediaUploader.upload(sourceUri)
        val finalResp = if (response.duration <= 0) response.copy(
            duration = readVideoDuration(sourceUri),
        ) else response
        return chatRepository.persistAndPushMedia(
            myUserId, peer, MessageType.VIDEO, finalResp, "[视频]"
        )
    }

    suspend fun sendVoice(
        myUserId: Long,
        peer: ChatPeer,
        voiceFile: File,
        durationMs: Long,
    ): SendMessageResult {
        val response = try {
            mediaUploader.upload(voiceFile, "audio/aac")
        } finally {
            voiceFile.delete()
        }
        val finalResp = if (response.duration <= 0) response.copy(duration = durationMs) else response
        return chatRepository.persistAndPushMedia(
            myUserId, peer, MessageType.AUDIO, finalResp, "[语音]"
        )
    }

    suspend fun sendFile(
        myUserId: Long,
        peer: ChatPeer,
        sourceUri: Uri,
    ): SendMessageResult {
        val response = mediaUploader.upload(sourceUri)
        val displayName = response.fileName ?: mediaUploader.readDisplayName(sourceUri)
        val finalResp = response.copy(fileName = displayName)
        val wsContent = encodeFileContent(finalResp.url, finalResp.size, displayName)
        return chatRepository.persistAndPushMedia(
            myUserId, peer, MessageType.FILE, finalResp,
            "[文件] ${displayName.orEmpty()}", wsContent
        )
    }

    private fun readVideoDuration(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.use {
                it.setDataSource(context, uri)
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun encodeFileContent(url: String, size: Long, fileName: String?): String {
        return "$url|$size|${fileName.orEmpty()}"
    }
}
