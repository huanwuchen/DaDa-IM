package com.dada.core.common.utils.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.dada.core.common.utils.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 图片压缩工具
 *
 * 实现策略：
 *  1. 先把 Uri 内容拷贝到本地临时文件（一次读取，避免 Photo Picker URI 二次读取失败）
 *  2. 用 inSampleSize 进行下采样，避免 OOM
 *  3. 限制最大边长（默认 1280px），缩放到目标尺寸
 *  4. 处理 EXIF 旋转信息
 *  5. JPEG 80% 质量输出到 cache 目录
 *
 * 输出文件位于 `context.cacheDir/im_compressed/`，使用前请确保业务调用完成后清理
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"
    private const val MAX_EDGE = 1280
    private const val JPEG_QUALITY = 80
    private const val CACHE_DIR_NAME = "im_compressed"

    /**
     * 压缩图片
     *
     * @param context 用于读取 Uri 和访问 cacheDir
     * @param uri     图片 Uri（来自相册/相机/Photo Picker）
     * @param maxEdge 最大边长（像素），默认 1280
     * @param quality JPEG 质量（0-100），默认 80
     * @return 压缩后文件；失败抛 [IOException]
     */
    suspend fun compress(
        context: Context,
        uri: Uri,
        maxEdge: Int = MAX_EDGE,
        quality: Int = JPEG_QUALITY,
    ): File = withContext(Dispatchers.IO) {
        LogUtil.d(TAG, "开始压缩图片: $uri")

        // 1. 把 Uri 拷贝到临时文件（关键：只读取 Uri 一次）
        val src = copyUriToTempFile(context, uri)
        try {
            compressFile(context, src, maxEdge, quality)
        } finally {
            if (!src.delete()) src.deleteOnExit()
        }
    }

    /**
     * 把 Uri 内容写到 cacheDir 临时文件
     */
    private fun copyUriToTempFile(context: Context, uri: Uri): File {
        val cache = ensureCacheDir(context)
        val tmp = File(cache, "src_${UUID.randomUUID()}.bin")

        try {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: SecurityException) {
                throw IOException("无权限读取图片，请重试或重新授权: ${e.message}", e)
            } ?: throw IOException("ContentResolver 打开 Uri 失败: $uri")

            input.use { i ->
                tmp.outputStream().use { o -> i.copyTo(o) }
            }
        } catch (e: IOException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            throw IOException("读取图片失败: ${e.message}", e)
        }

        if (tmp.length() <= 0) {
            tmp.delete()
            throw IOException("读到的图片为空（请重新选择）")
        }
        return tmp
    }

    /**
     * 基于本地文件做解码 + 缩放 + 旋转 + 输出
     */
    private fun compressFile(
        context: Context,
        src: File,
        maxEdge: Int,
        quality: Int,
    ): File {
        // 1. 读边界
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("不是有效图片或文件已损坏")
        }
        LogUtil.d(TAG, "原始尺寸: ${bounds.outWidth}x${bounds.outHeight}")

        // 2. 下采样解码
        val opts = BitmapFactory.Options().apply {
            this.inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(src.absolutePath, opts)
            ?: throw IOException("解码图片失败")

        // 3. 缩放 + EXIF 旋转
        val scale = (maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val rotation = readExifRotationFromFile(src)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val finalBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        ).also {
            if (it !== bitmap) bitmap.recycle()
        }

        // 4. 输出到缓存目录
        val outFile = File(ensureCacheDir(context), "img_${UUID.randomUUID()}.jpg")
        FileOutputStream(outFile).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        finalBitmap.recycle()

        LogUtil.d(
            TAG,
            "压缩完成: ${bounds.outWidth}x${bounds.outHeight} -> ${outFile.length()} bytes"
        )
        return outFile
    }

    /**
     * 计算 inSampleSize，使解码后图片每条边都不超过 [maxEdge] 的 2 倍。
     * 之后再用 Matrix 精确缩放到目标尺寸。
     */
    private fun calcInSampleSize(srcW: Int, srcH: Int, maxEdge: Int): Int {
        var sample = 1
        var w = srcW
        var h = srcH
        while (w / 2 >= maxEdge && h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * 读 EXIF 中的旋转角度（基于本地文件，不再二次读 Uri）
     */
    private fun readExifRotationFromFile(file: File): Int {
        return try {
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "读 EXIF 失败: ${e.message}")
            0
        }
    }

    /**
     * 确保缓存目录存在
     */
    private fun ensureCacheDir(context: Context): File {
        return File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }
}
