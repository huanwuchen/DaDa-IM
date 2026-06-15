package com.dada.app.push

import cn.jpush.android.service.JCommonService

/**
 * JCore 通用服务
 *
 * 自 JCore 2.0.0 起需要业务方继承 [JCommonService] 并在 Manifest 注册，
 * 极光推送通道在更多手机平台上能保持得更稳定。
 *
 * 内部不需要写任何业务，仅作为占位。
 */
class JCoreService : JCommonService()
