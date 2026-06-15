package com.dada.app.widget.emoji

import android.content.Context
import android.text.Editable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Emoji 表情面板
 *
 * 嵌在聊天页输入区上方。功能：
 *  - 8 列网格展示 emoji
 *  - 点击 emoji 插入到当前 EditText 光标位置
 *  - 右下角「⌫」退格按钮删除一个 emoji（按字符串删除，支持代理对）
 *
 * 使用方式：
 *   panel.bindEditText(binding.etInput)
 *   panel.visibility = View.VISIBLE / GONE
 */
class EmojiPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var targetEditText: EditText? = null
    private val recyclerView: RecyclerView
    private val backspaceButton: TextView

    init {
        // 背景色
        setBackgroundColor(0xFFF7F7F7.toInt())
        val padDp = (resources.displayMetrics.density * 8).toInt()
        setPadding(padDp, padDp, padDp, padDp)

        // 网格
        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 8)
            adapter = EmojiAdapter { emoji -> insertEmoji(emoji) }
            overScrollMode = OVER_SCROLL_NEVER
        }
        addView(
            recyclerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                bottomMargin = (resources.displayMetrics.density * 56).toInt()
            }
        )

        // 退格按钮（右下角）
        backspaceButton = TextView(context).apply {
            text = "⌫"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(padDp * 2, padDp, padDp * 2, padDp)
            setBackgroundResource(android.R.drawable.btn_default)
            setOnClickListener { deleteOneEmoji() }
        }
        addView(
            backspaceButton,
            LayoutParams(
                (resources.displayMetrics.density * 64).toInt(),
                (resources.displayMetrics.density * 48).toInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        )
    }

    /**
     * 绑定目标输入框
     */
    fun bindEditText(editText: EditText) {
        this.targetEditText = editText
    }

    /**
     * 在光标位置插入 emoji；没有焦点时追加到末尾
     */
    private fun insertEmoji(emoji: String) {
        val edit = targetEditText ?: return
        val editable: Editable = edit.editableText
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        editable.replace(start, end, emoji)
    }

    /**
     * 模拟按下退格键，删除一个字符（系统会处理代理对）
     */
    private fun deleteOneEmoji() {
        val edit = targetEditText ?: return
        edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }
}
