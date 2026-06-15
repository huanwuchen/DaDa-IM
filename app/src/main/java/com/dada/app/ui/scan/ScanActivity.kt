package com.dada.app.ui.scan

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityScanBinding
import com.dada.app.ui.common.WebViewActivity
import com.dada.app.widget.imageviewer.StfalconImageViewer
import com.dada.app.widget.video.FullscreenVideoPlayerActivity
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 扫一扫页面（基于 ZXing）
 *
 * 扫码结果分发逻辑：
 *  - 图片链接 → StfalconImageViewer 全屏查看
 *  - 视频链接 → FullscreenVideoPlayerActivity
 *  - HTTP/HTTPS → WebViewActivity
 *  - 纯文本 → AlertDialog 显示 + 复制
 */
@AndroidEntryPoint
class ScanActivity : BaseActivity<ActivityScanBinding>() {

    @Inject lateinit var imageLoader: ImageLoader

    private var isFlashOn = false
    private var hasScanned = false

    override fun inflateBinding() = ActivityScanBinding.inflate(layoutInflater)

    override fun initView() {
        setupFullScreen()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnFlashlight.setOnClickListener {
            isFlashOn = !isFlashOn
            if (isFlashOn) {
                binding.barcodeScanner.setTorchOn()
            } else {
                binding.barcodeScanner.setTorchOff()
            }
        }

        // 相机权限
        if (hasCameraPermission()) {
            startScanner()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA
            )
        }
    }

    override fun initData() {}


    /**
     * 全屏 + 锁屏显示 + 屏幕常亮（通话过程中需要）
     */
    private fun setupFullScreen() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            binding.barcodeScanner.resume()
        }
        hasScanned = false
    }

    override fun onPause() {
        binding.barcodeScanner.pause()
        super.onPause()
    }

    private fun handleScanResult(raw: String) {
        when (val action = resolveScanResult(raw)) {
            is ScanAction.Image -> {
                binding.barcodeScanner.pause()
                StfalconImageViewer.Builder(this, listOf(action.url)) { imageView, url ->
                    imageView.loadImage(url, imageLoader)
                }
                    .withHiddenStatusBar(true)
                    .allowSwipeToDismiss(true)
                    .allowZooming(true)
                    .show()
                hasScanned = false
            }
            is ScanAction.Video -> {
                FullscreenVideoPlayerActivity.start(this, action.url, title = "扫码视频")
                hasScanned = false
            }
            is ScanAction.Url -> {
                WebViewActivity.start(this, action.url)
                hasScanned = false
            }
            is ScanAction.Text -> {
                showTextDialog(action.text)
            }
        }
    }

    private fun showTextDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle("扫码结果")
            .setMessage(text)
            .setPositiveButton("复制") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("scan_result", text))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                hasScanned = false
            }
            .setNegativeButton("关闭") { _, _ ->
                hasScanned = false
            }
            .setOnDismissListener {
                hasScanned = false
            }
            .show()
    }

    // ============================== 权限 ==============================

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startScanner() {
        binding.barcodeScanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val raw = result?.text ?: return
                if (hasScanned) return
                hasScanned = true
                handleScanResult(raw)
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
                binding.barcodeScanner.resume()
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ============================== 结果类型判断 ==============================

    sealed class ScanAction {
        data class Image(val url: String) : ScanAction()
        data class Video(val url: String) : ScanAction()
        data class Url(val url: String) : ScanAction()
        data class Text(val text: String) : ScanAction()
    }

    companion object {
        private const val REQUEST_CAMERA = 0x3001

        private val IMAGE_EXTENSIONS = listOf(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg"
        )
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mov", ".avi", ".mkv", ".webm", ".3gp", ".flv"
        )

        fun resolveScanResult(raw: String): ScanAction {
            val lower = raw.lowercase().trim()
            return when {
                IMAGE_EXTENSIONS.any { lower.endsWith(it) } ||
                        (lower.startsWith("http") && lower.contains("image")) ->
                    ScanAction.Image(raw)

                VIDEO_EXTENSIONS.any { lower.endsWith(it) } ||
                        (lower.startsWith("http") && lower.contains("video")) ->
                    ScanAction.Video(raw)

                lower.startsWith("http://") || lower.startsWith("https://") ->
                    ScanAction.Url(raw)

                else -> ScanAction.Text(raw)
            }
        }
    }
}
