package com.dada.app.push

import android.content.Context
import com.dada.core.common.utils.LogUtil
import cn.jpush.android.api.JPushInterface
import com.dada.core.network.api.ImApiService
import com.dada.core.database.UserPreferences
import com.dada.core.network.model.PushTokenRequest
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 极光推送管理器
 *
 * 职责：
 *  1. App 启动时初始化 JPush
 *  2. 登录成功后绑定别名（alias = userId）
 *  3. 拿到 RegistrationID 后上报后端，绑定到当前用户
 *  4. 退出登录时解绑
 *
 * RegistrationID 在 [JPushReceiver.onRegister] 中拿到后会保存到 MMKV，
 * 并在登录态下立即上报后端；如果初始化时已经有 ID，登录后也会主动上报一次。
 */
@Singleton
class PushManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imApiService: ImApiService,
    private val userPreferences: UserPreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    /**
     * 在 Application.onCreate 中调用
     */
    fun init() {
        // 调试期开启日志，正式发布建议关掉
        JPushInterface.setDebugMode(true)
        JPushInterface.init(context)
        LogUtil.d(TAG, "JPush 初始化")
    }

    /**
     * 登录成功后调用：绑定别名 + 上报 RegistrationID
     */
    fun onUserLogin(userId: Long) {
        if (userId <= 0L) return

        // 设置别名（服务端可通过 alias = userId 推送给指定用户）
        JPushInterface.setAlias(context, ALIAS_SEQUENCE, userId.toString())
        LogUtil.d(TAG, "JPush 设置别名: $userId")

        // 如果 RegistrationID 已经准备好，直接上报；否则等 Receiver 触发
        val registrationId = JPushInterface.getRegistrationID(context)
        if (!registrationId.isNullOrEmpty()) {
            uploadRegistrationId(userId, registrationId)
        }
    }

    /**
     * Receiver 拿到 RegistrationID 时回调
     */
    fun onRegistrationIdReceived(registrationId: String) {
        LogUtil.d(TAG, "RegistrationID 就绪: $registrationId")
        mmkv.encode(KEY_REGISTRATION_ID, registrationId)

        val userId = userPreferences.getUserId()
        if (userId > 0L) uploadRegistrationId(userId, registrationId)
    }

    /**
     * 退出登录：清除别名，避免被推送
     */
    fun onUserLogout() {
        JPushInterface.deleteAlias(context, ALIAS_SEQUENCE + 1)
        LogUtil.d(TAG, "JPush 解绑别名")
    }

    /**
     * 上报 RegistrationID 到后端
     */
    private fun uploadRegistrationId(userId: Long, registrationId: String) {
        scope.launch {
            try {
                val response = imApiService.reportPushToken(
                    PushTokenRequest(
                        userId = userId,
                        registrationId = registrationId,
                    )
                )
                if (response.isSuccess) {
                    LogUtil.d(TAG, "上报 RegistrationID 成功")
                } else {
                    LogUtil.w(TAG, "上报 RegistrationID 失败: ${response.message}")
                }
            } catch (e: Exception) {
                // 上报失败不影响主流程，等下次登录或重连时再重试
                LogUtil.e(TAG, "上报 RegistrationID 异常: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "PushManager"
        private const val ALIAS_SEQUENCE = 1
        private const val KEY_REGISTRATION_ID = "jpush_registration_id"
    }
}
