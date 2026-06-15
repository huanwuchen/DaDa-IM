package com.dada.app.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import com.dada.app.R
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityWebViewBinding

/**
 * 通用 WebView 页面
 *
 * 用法：WebViewActivity.start(context, "https://example.com")
 */
class WebViewActivity : BaseActivity<ActivityWebViewBinding>() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, url: String, title: String? = null) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun inflateBinding() = ActivityWebViewBinding.inflate(layoutInflater)

    override fun initView() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupStatusBar()
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = android.view.View.GONE
                if (binding.toolbar.title.isNullOrBlank() || binding.toolbar.title == "网页") {
                    view?.title?.let { binding.toolbar.title = it }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return true
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
            }
        }
    }

    override fun initData() {
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (!title.isNullOrBlank()) {
            binding.toolbar.title = title
        }
        binding.webView.loadUrl(url)
    }

    /**
     * 设置状态栏样式
     *
     * - 背景色：白色
     * - 图标/文字：深色（适配浅色背景）
     * - 应用最低 SDK 版本为 24，无需版本检查
     */
    private fun setupStatusBar() {
        window.apply {
            // 设置状态栏背景色为白色
            statusBarColor = ContextCompat.getColor(this@WebViewActivity, R.color.white)

            // 设置状态栏图标为深色（浅色背景适配）
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }


    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
