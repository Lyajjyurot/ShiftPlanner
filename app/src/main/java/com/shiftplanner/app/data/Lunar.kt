package com.shiftplanner.app.data

import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId

/**
 * 农历显示工具：基于 Android 平台内置 ICU（android.icu.util.ChineseCalendar），
 * 系统级权威农历数据，无需第三方库、无需自带数据表。
 *
 * 显示规则：初一显示月名（正月/二月/…/腊月，闰月带"闰"前缀），其余显示日名（初二/…/三十）。
 */
object Lunar {

    private val MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    private val DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /** 返回农历文本（如"正月"、"初十"、"闰四月"），异常时返回空串（调用方不渲染） */
    fun lunarText(date: LocalDate): String = runCatching {
        val cal = ChineseCalendar()
        cal.timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val month = cal.get(ChineseCalendar.MONTH)           // 0-based
        val isLeap = cal.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        val day = cal.get(ChineseCalendar.DAY_OF_MONTH)        // 1..30
        if (day == 1) {
            (if (isLeap) "闰" else "") + MONTH_NAMES[month]
        } else {
            DAY_NAMES[day - 1]
        }
    }.getOrDefault("")
}
