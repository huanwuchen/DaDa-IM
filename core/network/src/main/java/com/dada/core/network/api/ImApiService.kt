package com.dada.core.network.api

import com.dada.core.network.model.ApiResponse
import com.dada.core.network.model.ImUser
import com.dada.core.network.model.OnlineUsersResponse
import com.dada.core.network.model.PushTokenRequest
import com.dada.core.network.model.RegisterRequest
import com.dada.core.network.model.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * IM API 服务
 */
interface ImApiService {

    /**
     * 用户注册/登录
     */
    @POST("/api/user/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<ImUser>

    /**
     * 获取在线用户列表
     */
    @GET("/api/user/online")
    suspend fun getOnlineUsers(): ApiResponse<OnlineUsersResponse>

    /**
     * 上报极光 RegistrationID（用于服务端推送时定位设备）
     */
    @POST("/api/user/push-register")
    suspend fun reportPushToken(@Body request: PushTokenRequest): ApiResponse<Unit>

    /**
     * 通用 multipart 文件上传（图片/视频/音频/文件）
     *
     * 服务端约定：
     *  - URL: POST /api/upload
     *  - 字段名: file
     *  - 服务端会根据扩展名识别类型，返回 [UploadResponse]：
     *    {url: "/uploads/2026/05/30/abc.jpg", type: "image", fileName: "...", size: 123}
     *  - url 是相对路径，使用前需要拼接 [com.dada.app.utils.media.Constants.BASE_URL]
     */
    @Multipart
    @POST("/api/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
    ): ApiResponse<UploadResponse>

    /**
     * 批量上传（最多 9 个文件）
     *
     * 字段名：files（多文件）
     */
    @Multipart
    @POST("/api/upload/batch")
    suspend fun uploadFiles(
        @Part files: List<MultipartBody.Part>,
    ): ApiResponse<List<UploadResponse>>
}
