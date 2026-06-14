package com.dada.core.network.model

import com.google.gson.annotations.SerializedName

/**
 * 用户注册请求
 */
data class RegisterRequest(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("avatar")
    val avatar: String? = null
)

/**
 * IM 用户信息
 */
data class ImUser(
    @SerializedName("id")
    val id: Long,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("avatar")
    val avatar: String? = null,

    @SerializedName("coverImage")
    val coverImage: String? = null,

    @SerializedName("createTime")
    val createTime: String? = null,

    @SerializedName("online")
    val online: Boolean = false
)

/**
 * API 响应基类
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: T?
) {
    val isSuccess get() = code == SUCCESS_CODE

    companion object {
        const val SUCCESS_CODE = 200
    }
}

/**
 * 在线用户列表响应
 */
data class OnlineUsersResponse(
    @SerializedName("count")
    val count: Int,

    @SerializedName("users")
    val users: List<ImUser>
)

/**
 * 上报极光 RegistrationID 请求
 *
 * @property userId         用户 ID
 * @property platform       平台标识：android / ios
 * @property registrationId JPush RegistrationID（服务端推送时使用）
 */
data class PushTokenRequest(
    @SerializedName("userId")
    val userId: Long,

    @SerializedName("platform")
    val platform: String = "android",

    @SerializedName("registrationId")
    val registrationId: String,
)

/**
 * 文件上传响应
 *
 * 服务端 `POST /api/upload` 返回的元信息。
 *
 * @property url       文件相对路径（如 `/uploads/2026/05/30/abc.jpg`）
 *                     客户端展示前需用 [com.dada.app.utils.media.Constants.resolveUrl] 拼接 host
 * @property type      服务端识别的类型：image / video / audio / file
 * @property fileName  原始文件名
 * @property size      文件字节数
 * @property width     图片/视频宽度（像素，可选）
 * @property height    图片/视频高度（像素，可选）
 * @property duration  音频/视频时长（毫秒，可选）
 * @property thumbUrl  缩略图相对路径（可选）
 */
data class UploadResponse(
    @SerializedName("url")
    val url: String,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("fileName")
    val fileName: String? = null,

    @SerializedName("size")
    val size: Long = 0,

    @SerializedName("width")
    val width: Int = 0,

    @SerializedName("height")
    val height: Int = 0,

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("thumbUrl")
    val thumbUrl: String? = null,
)
