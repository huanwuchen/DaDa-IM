/*
 * Copyright 2018 stfalcon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dada.app.widget.imageviewer.viewer.dialog

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dada.app.R
import com.dada.app.widget.imageviewer.viewer.builder.BuilderData
import com.dada.app.widget.imageviewer.viewer.view.ImageViewerView

internal class ImageViewerDialog<T>(
    context: Context,
    private val builderData: BuilderData<T>
) {

    private val dialog: AlertDialog
    private val viewerView: ImageViewerView<T> = ImageViewerView(context)
    private var animateOpen = true

    private val dialogStyle: Int
        get() = if (builderData.shouldStatusBarHide)
            R.style.ImageViewerDialog_NoStatusBar
        else
            R.style.ImageViewerDialog_Default

    init {
        setupViewerView()
        dialog = AlertDialog
            .Builder(context, dialogStyle)
            .setView(viewerView)
            .setOnKeyListener { _, keyCode, event -> onDialogKeyEvent(keyCode, event) }
            .create()
            .apply {
                setOnShowListener { viewerView.open(builderData.transitionView, animateOpen) }
                setOnDismissListener { builderData.onDismissListener?.onDismiss() }
            }
    }

    fun show(animate: Boolean) {
        animateOpen = animate
        // show() 之前禁用获取焦点，避免状态栏抖动
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        dialog.show()
        // show() 之后恢复焦点 + 设置全屏
        dialog.window?.let { window ->
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    fun close() {
        viewerView.close()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun updateImages(images: List<T>) {
        viewerView.updateImages(images)
    }

    fun getCurrentPosition(): Int =
        viewerView.currentPosition

    fun setCurrentPosition(position: Int): Int {
        viewerView.currentPosition = position
        return viewerView.currentPosition
    }

    fun updateTransitionImage(imageView: ImageView?) {
        viewerView.updateTransitionImage(imageView)
    }

    private fun onDialogKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            !event.isCanceled
        ) {
            if (viewerView.isScaled) {
                viewerView.resetScale()
            } else {
                viewerView.close()
            }
            return true
        }
        return false
    }

    private fun setupViewerView() {
        viewerView.apply {
            isZoomingAllowed = builderData.isZoomingAllowed
            isSwipeToDismissAllowed = builderData.isSwipeToDismissAllowed

            containerPadding = builderData.containerPaddingPixels
            imagesMargin = builderData.imageMarginPixels
            overlayView = builderData.overlayView

            setBackgroundColor(builderData.backgroundColor)
            setImages(builderData.images, builderData.startPosition, builderData.imageLoader)

            onPageChange = { position -> builderData.imageChangeListener?.onImageChange(position) }
            onDismiss = { dialog.dismiss() }

            // 点击图片关闭
            setOnClickListener { close() }
        }
    }
}