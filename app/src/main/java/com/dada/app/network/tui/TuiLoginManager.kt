package com.dada.app.network.tui

import android.content.Context
import com.dada.app.BuildConfig
import com.dada.core.common.utils.LogUtil
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.interfaces.TUICallback
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.debug.GenerateTestUserSig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 腾讯 TUICallKit 登录封装
 *
 * 把 [TUICallKit.createInstance] / [TUILogin.login] 与 [GenerateTestUserSig] 等
 * 第三方 SDK 调用从 Application / ViewModel 中抽离，集中在这里。
 *
 * TODO: 生产环境应由服务端下发 UserSig，移除客户端 [BuildConfig.TUI_SECRET_KEY]。
 */
@Singleton
class TuiLoginManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    /**
     * 创建 TUICallKit 实例（仅主进程需要）。重复调用安全。
     */
    fun ensureInstance() {
        if (BuildConfig.TUI_SDK_APP_ID <= 0) return
        TUICallKit.createInstance(appContext)
    }

    /**
     * 用本地生成的 UserSig 登录 TUICallKit；失败仅记日志，不抛异常。
     */
    fun login(userId: Long) {
        if (BuildConfig.TUI_SDK_APP_ID <= 0) return
        val userIdStr = userId.toString()
        val userSig = GenerateTestUserSig.genTestUserSig(
            userIdStr,
            BuildConfig.TUI_SDK_APP_ID,
            BuildConfig.TUI_SECRET_KEY,
        )
        TUILogin.login(
            appContext,
            BuildConfig.TUI_SDK_APP_ID,
            userIdStr,
            userSig,
            object : TUICallback() {
                override fun onSuccess() {
                    LogUtil.d(TAG, "TUICallKit 登录成功: userId=$userIdStr")
                }

                override fun onError(code: Int, message: String?) {
                    LogUtil.w(TAG, "TUICallKit 登录失败: code=$code, msg=$message（不影响主流程）")
                }
            },
        )
    }

    companion object {
        private const val TAG = "TuiLoginManager"
    }
}
