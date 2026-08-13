package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alibaba.fastjson2.JSON
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.FavoriteApi
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import retrofit2.http.GET
import retrofit2.http.Url
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private interface SignApi {
    @GET
    suspend fun fetchHtml(@Url url: String): ResponseBody
}

enum class SignTrigger {
    LAUNCH, RESUME
}

enum class TodaySignStatus {
    SIGNED,
    NOT_SIGNED,
    UNKNOWN
}

internal object TodaySignStatusCache {
    fun encode(date: String, status: TodaySignStatus): String = "$date:${status.name}"

    fun decode(value: String, today: String): TodaySignStatus? {
        val parts = value.split(":", limit = 2)
        if (parts.size != 2 || parts[0] != today) return null
        return runCatching { TodaySignStatus.valueOf(parts[1]) }
            .getOrNull()
            ?.takeUnless { it == TodaySignStatus.UNKNOWN }
    }
}

/**
 * 后台自动签到
 */
object AutoSignManager {
    private const val BASE_URL = "https://bbs.yamibo.com/"
    private const val MAX_DAILY_RETRIES = 2

    fun getServerTodayPublic(): String = getServerToday()
    private fun getServerToday(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+08:00")
        }
        return sdf.format(Date())
    }

    private fun getCurrentAccountHash(): Int? {
        val cookie = GlobalData.currentCookie
        val authMatch = Regex("EeqY_2132_auth=([^;]+)").find(cookie)
        return authMatch?.groupValues?.get(1)?.hashCode()
    }

    private fun getQuotaKey(hash: Int) = stringPreferencesKey("sign_quota_v3_$hash")

    private fun getSignStatusKey(hash: Int) = stringPreferencesKey("sign_status_v1_$hash")

    private suspend fun getCurrentQuota(hash: Int): Triple<String, Int, Int> {
        val prefs = GlobalData.dataStore?.data?.first()
        val rawData = prefs?.get(getQuotaKey(hash)) ?: ""
        val parts = rawData.split(":")
        val date = if (parts.isNotEmpty()) parts[0] else ""
        val launchCount = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        val resumeCount = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
        return Triple(date, launchCount, resumeCount)
    }

    private suspend fun updateQuota(hash: Int, date: String, launchCount: Int, resumeCount: Int) {
        GlobalData.dataStore?.edit { it[getQuotaKey(hash)] = "$date:$launchCount:$resumeCount" }
    }

    private suspend fun getCachedTodaySignStatus(hash: Int, today: String): TodaySignStatus? {
        val rawStatus = GlobalData.dataStore?.data?.first()?.get(getSignStatusKey(hash))
        return rawStatus?.let { TodaySignStatusCache.decode(it, today) }
    }

    private suspend fun saveTodaySignStatus(hash: Int, date: String, status: TodaySignStatus) {
        if (status == TodaySignStatus.UNKNOWN) return
        GlobalData.dataStore?.edit {
            it[getSignStatusKey(hash)] = TodaySignStatusCache.encode(date, status)
        }
    }

    suspend fun resetQuota(hash: Int? = getCurrentAccountHash()) {
        if (hash == null) return
        val today = getServerToday()
        updateQuota(hash, today, 0, 0)
    }

    suspend fun needsSignIn(trigger: SignTrigger = SignTrigger.LAUNCH): Boolean {
        val accountHash = getCurrentAccountHash() ?: return false
        val today = getServerToday()
        val (savedDate, launchCount, resumeCount) = getCurrentQuota(accountHash)

        if (savedDate != today) return true

        return when (trigger) {
            SignTrigger.LAUNCH -> launchCount < MAX_DAILY_RETRIES
            SignTrigger.RESUME -> resumeCount < MAX_DAILY_RETRIES
        }
    }

    suspend fun getTodaySignStatus(forceRefresh: Boolean = false): TodaySignStatus = withContext(Dispatchers.IO) {
        val accountHash = getCurrentAccountHash() ?: return@withContext TodaySignStatus.UNKNOWN
        val today = getServerToday()
        if (!forceRefresh) {
            getCachedTodaySignStatus(accountHash, today)?.let { return@withContext it }
        }

        val status = runCatching {
            val html = YamiboRetrofit.getInstance()
                .create(SignApi::class.java)
                .fetchHtml("${BASE_URL}plugin.php?id=zqlj_sign")
                .string()
            val document = Jsoup.parse(html)
            val pageText = document.text()
            if (listOf("今日已签到", "今日已打卡", "已经打过卡了", "已签到", "已打卡")
                    .any(pageText::contains)
            ) {
                TodaySignStatus.SIGNED
            } else if (
                document.select(".signbtn, a[href*=zqlj_sign], input[value*=签到]").isNotEmpty() ||
                pageText.contains("立即签到") || pageText.contains("我要签到")
            ) {
                TodaySignStatus.NOT_SIGNED
            } else {
                TodaySignStatus.UNKNOWN
            }
        }.getOrDefault(TodaySignStatus.UNKNOWN)

        saveTodaySignStatus(accountHash, today, status)
        status
    }

    suspend fun checkAndSignIfNeeded(
        context: Context,
        trigger: SignTrigger = SignTrigger.LAUNCH,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val accountHash = getCurrentAccountHash() ?: return@withContext
        val today = getServerToday()
        var (savedDate, launchCount, resumeCount) = getCurrentQuota(accountHash)

        if (today != savedDate) {
            launchCount = 0
            resumeCount = 0
        }

        if (!force) {
            val currentCount = if (trigger == SignTrigger.LAUNCH) launchCount else resumeCount
            if (currentCount >= MAX_DAILY_RETRIES) return@withContext
        }

        try {
            val favoriteApi = YamiboRetrofit.getInstance().create(FavoriteApi::class.java)
            val profileResponse = favoriteApi.getFormHash().execute()
            val json = profileResponse.body()?.string() ?: ""
            var formHash: String? = null
            try {
                val jsonObject = JSON.parseObject(json)
                formHash = jsonObject?.getJSONObject("Variables")?.getString("formhash")
            } catch (_: Exception) { }
            if (formHash.isNullOrEmpty()) {
                if (force) showToast(context, "登录验证失败，无法打卡")
                return@withContext
            }

            val signApi = YamiboRetrofit.getInstance().create(SignApi::class.java)
            val signUrl = "${BASE_URL}plugin.php?id=zqlj_sign&sign=$formHash"
            val actionResponseHtml = signApi.fetchHtml(signUrl).string()

            if (!force) {
                if (trigger == SignTrigger.LAUNCH) launchCount++ else resumeCount++
                updateQuota(accountHash, today, launchCount, resumeCount)
            }

            if (actionResponseHtml.contains("已经打过卡了") ||
                actionResponseHtml.contains("今日已打卡") ||
                actionResponseHtml.contains("重复操作")
            ) {
                saveTodaySignStatus(accountHash, today, TodaySignStatus.SIGNED)
                if (force) showToast(context, "今日已打卡")
            } else if (actionResponseHtml.contains("打卡成功") ||
                actionResponseHtml.contains("成功") ||
                actionResponseHtml.contains("获得了")
            ) {
                saveTodaySignStatus(accountHash, today, TodaySignStatus.SIGNED)
                showToast(context, "签到成功")
            } else {
                if (force) showToast(context, "打卡请求已发送")
            }
        } catch (_: Exception) {
            if (force) showToast(context, "网络异常，稍后重试")
        }
    }

    private suspend fun showToast(context: Context, msg: String) {
        withContext(Dispatchers.Main) {
            YamiboToast.show(
                context = context.applicationContext,
                message = msg,
                durationMillis = 1200L
            )
        }
    }
}
