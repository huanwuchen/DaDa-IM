package com.dada.core.network.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dada.core.common.utils.LogUtil
import android.webkit.MimeTypeMap
import com.dada.core.network.api.ImApiService
import com.dada.core.network.model.UploadResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体上传器
 *
 * 对接服务端 `POST /api/upload`：
 *  - 字段名：file
 *  - 服务端按扩展名识别类型，因此 multipart 的 filename 必须保留原始文件名/扩展名
 *  - 返回 [UploadResponse]，url 是相对路径（/uploads/...），UI 展示前需 [Constants.resolveUrl] 拼接 host
 */
@Singleton
class MediaUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imApiService: ImApiService,
) {

    /**
     * 上传一个本地文件
     *
     * @param file     已存在的本地文件（一般是 ImageCompressor 输出 / 录音文件）
     * @param mimeType 可选，未提供时按文件扩展名推断
     */
    suspend fun upload(
        file: File,
        mimeType: String? = null,
    ): UploadResponse = withContext(Dispatchers.IO) {
        val mime = mimeType ?: guessMime(file.name) ?: DEFAULT_MIME
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, body)

        val response = imApiService.uploadFile(part)
        if (response.isSuccess && response.data != null) {
            response.data
        } else {
            throw IllegalStateException("上传失败: ${response.message}")
        }
    }

    /**
     * 上传一个 Uri（从相册选择 / 拍照拍摄）
     *
     * 实现要点：
     *  1. 用 ContentResolver 把流复制到 cacheDir 临时文件
     *  2. multipart 的 filename 字段使用原始文件名，让服务端按扩展名识别 image/video/audio/file
     *  3. 失败时给出具体原因（区分「Uri 不可读」和「IO 异常」）
     */
    suspend fun upload(uri: Uri): UploadResponse = withContext(Dispatchers.IO) {
        LogUtil.d(TAG, "开始上传 uri=$uri scheme=${uri.scheme}")

        // 原始文件名（含扩展名），用于 multipart filename
        val displayName = readDisplayName(uri)
            ?: ("upload_${UUID.randomUUID()}" + extensionWithDot(uri))

        // mime：先问 ContentResolver，再按文件名推断
        val mime = readMime(uri) ?: guessMime(displayName) ?: DEFAULT_MIME
        LogUtil.d(TAG, "displayName=$displayName, mime=$mime")

        // 临时文件，仅用于 OkHttp 流式读取；命名不影响服务端识别
        val tmp = File(context.cacheDir, "upload_${UUID.randomUUID()}").apply {
            parentFile?.mkdirs()
        }

        try {
            // 把 Uri 内容复制到临时文件
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("ContentResolver.openInputStream 返回 null")
            } catch (e: SecurityException) {
                throw IOException("无权限访问该文件，请重新选择: ${e.message}", e)
            }

            if (tmp.length() <= 0) {
                throw IOException("读取到的文件为空（可能权限失效，请重新选择）")
            }

            LogUtil.d(TAG, "临时文件准备完成: ${tmp.absolutePath}, size=${tmp.length()}")

            val body = tmp.asRequestBody(mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", displayName, body)

            val localSize = tmp.length()
            val response = imApiService.uploadFile(part)
            if (response.isSuccess && response.data != null) {
                LogUtil.d(TAG, "上传成功: ${response.data.url}")
                // 服务端未返回 size 时用本地文件大小兜底
                if (response.data.size <= 0 && localSize > 0) {
                    response.data.copy(size = localSize)
                } else {
                    response.data
                }
            } else {
                throw IllegalStateException("上传失败: ${response.message}")
            }
        } finally {
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }

    /**
     * 读取 Uri 的 MIME 类型
     */
    private fun readMime(uri: Uri): String? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> context.contentResolver.getType(uri)
            else -> guessMime(uri.lastPathSegment ?: "")
        }
    }

    /**
     * 拿带点扩展名（如 ".jpg"），失败时返回空字符串
     */
    private fun extensionWithDot(uri: Uri): String {
        val mime = readMime(uri)
        if (!mime.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return ".$it" }
        }
        val name = uri.lastPathSegment.orEmpty()
        val ext = name.substringAfterLast('.', "")
        return if (ext.isNotBlank()) ".$ext" else ""
    }

    private fun guessMime(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    /**
     * 读取文件原始名（包含扩展名）
     */
    fun readDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "读取 displayName 失败: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "MediaUploader"
        private const val DEFAULT_MIME = "application/octet-stream"
    }
}
