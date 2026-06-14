package com.dada.app.widget.video

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * 16:9 比例的视频容器
 * 用于确保视频播放器保持 16:9 的宽高比
 */
class AspectRatioLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var aspectRatio: Float = 16f / 9f

    /**
     * 设置宽高比
     * @param ratio 宽高比，例如 16f/9f
     */
    fun setAspectRatio(ratio: Float) {
        if (aspectRatio != ratio) {
            aspectRatio = ratio
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / aspectRatio).toInt()

        val newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }
}
