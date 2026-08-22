package org.shirakawatyu.yamibo.novel.parser

import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.SignCalendarDay
import org.shirakawatyu.yamibo.novel.bean.SignPageData
import org.shirakawatyu.yamibo.novel.bean.SignRecord

object SignPageParser {
    private const val ORIGIN = "https://bbs.yamibo.com"
    private val monthPattern = Regex("(\\d{4})年\\s*(\\d{1,2})月")

    fun parse(html: String): SignPageData {
        val document = Jsoup.parse(html, ORIGIN)
        val monthMatch = monthPattern.find(
            document.selectFirst("#tablehead th")?.text().orEmpty()
        ) ?: throw IllegalStateException("签到月历内容为空")
        val year = monthMatch.groupValues[1].toInt()
        val month = monthMatch.groupValues[2].toInt()
        val signButton = document.selectFirst(".signbtn a")
        val statusText = signButton?.text()?.trim().orEmpty()
        val signedToday = statusText.contains("已打卡") || statusText.contains("已签到")
        val announcementTitle = document.select(".hui-common-title-txt")
            .firstOrNull { it.text().trim() == "打卡公告" }
        val announcement = announcementTitle
            ?.parent()
            ?.nextElementSibling()
            ?.text()
            ?.trim()
            .orEmpty()
        val calendar = document.select("#tablebody tbody").firstOrNull()
            ?.select("tr > td")
            ?.map { cell ->
                val dayElement = cell.selectFirst(".day") ?: return@map null
                val day = dayElement.text().trim().toIntOrNull() ?: return@map null
                SignCalendarDay(
                    day = day,
                    signed = dayElement.hasClass("on"),
                    today = dayElement.hasClass("today"),
                    holiday = cell.selectFirst(".holiday")?.text()?.trim().orEmpty()
                )
            }
            .orEmpty()
        val records = document.select("#tblist .hui-media-content").mapNotNull { item ->
            val author = item.selectFirst("a[href*='uid=']") ?: return@mapNotNull null
            val lines = item.select("p").map { it.text().trim() }
            fun value(label: String): String = lines.firstOrNull { it.startsWith(label) }
                ?.substringAfter("：")
                ?.substringBefore("今日已打卡")
                ?.trim()
                .orEmpty()
            SignRecord(
                uid = Regex("[?&]uid=(\\d+)").find(author.attr("href"))
                    ?.groupValues?.getOrNull(1).orEmpty(),
                username = author.text().trim(),
                level = value("打卡等级"),
                totalDays = value("总天数"),
                monthDays = value("月天数"),
                totalReward = value("总奖励"),
                lastReward = value("上次奖励"),
                lastSignTime = value("上次打卡时间")
            )
        }
        val statsTitle = document.select(".hui-common-title-txt")
            .firstOrNull { it.text().trim() == "我的打卡动态" }
        val myStats = statsTitle
            ?.parent()
            ?.nextElementSibling()
            ?.select(".hui-list-text")
            ?.map { it.text().trim() }
            ?.filter(String::isNotBlank)
            .orEmpty()
        return SignPageData(
            year = year,
            month = month,
            statusText = statusText.ifBlank { if (signedToday) "今日已打卡" else "点击打卡" },
            signedToday = signedToday,
            actionUrl = signButton?.absUrl("href").orEmpty(),
            announcement = announcement,
            calendar = calendar,
            records = records,
            myStats = myStats
        )
    }
}
