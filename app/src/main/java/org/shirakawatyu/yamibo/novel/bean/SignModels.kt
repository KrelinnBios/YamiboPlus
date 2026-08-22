package org.shirakawatyu.yamibo.novel.bean

data class SignCalendarDay(
    val day: Int,
    val signed: Boolean,
    val today: Boolean,
    val holiday: String = ""
)

data class SignRecord(
    val uid: String,
    val username: String,
    val level: String,
    val totalDays: String,
    val monthDays: String,
    val totalReward: String,
    val lastReward: String,
    val lastSignTime: String
)

data class SignPageData(
    val year: Int,
    val month: Int,
    val statusText: String,
    val signedToday: Boolean,
    val actionUrl: String,
    val announcement: String,
    val calendar: List<SignCalendarDay?>,
    val records: List<SignRecord>,
    val myStats: List<String>
)
