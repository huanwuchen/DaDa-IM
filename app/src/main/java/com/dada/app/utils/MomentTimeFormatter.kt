package com.dada.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 朋友圈时间格式化
 *
 * 服务端返回 ISO 字符串：2026-05-31T10:00:00
 * 展示规则（跟微信对齐）：
 *  - <1 分钟       -> 刚刚
 *  - <1 小时       -> X 分钟前
 *  - <24 小时      -> X 小时前
 *  - 昨天          -> 昨天 HH:mm
 *  - 今年          -> M月D日
 *  - 去年及以前    -> YYYY年M月D日
 */
object MomentTimeFormatter {

    private val ISO_FORMAT = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
    ).apply { timeZone = TimeZone.getDefault() }

    private val HOUR_MIN = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val MONTH_DAY = SimpleDateFormat("M月d日", Locale.getDefault())
    private val FULL_DATE = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())

    fun format(isoTime: String?): String {
        if (isoTime.isNullOrBlank()) return ""
        val date = parse(isoTime) ?: return isoTime
        val now = System.currentTimeMillis()
        val diff = now - date.time
        return when {
            diff < ONE_MINUTE -> "刚刚"
            diff < ONE_HOUR -> "${diff / ONE_MINUTE}分钟前"
            diff < ONE_DAY -> "${diff / ONE_HOUR}小时前"
            isYesterday(date) -> "昨天 ${HOUR_MIN.format(date)}"
            isThisYear(date) -> MONTH_DAY.format(date)
            else -> FULL_DATE.format(date)
        }
    }

    private fun parse(time: String): Date? {
        // 截掉 .SSS 毫秒部分（如果有）
        val clean = time.substringBefore('.')
        return runCatching { ISO_FORMAT.parse(clean) }.getOrNull()
    }

    private fun isYesterday(date: Date): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        now.add(Calendar.DAY_OF_YEAR, -1)
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(date: Date): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    }

    private const val ONE_MINUTE = 60_000L
    private const val ONE_HOUR = 60 * ONE_MINUTE
    private const val ONE_DAY = 24 * ONE_HOUR
}