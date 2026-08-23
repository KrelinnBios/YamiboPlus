package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.FavoriteApi
import org.shirakawatyu.yamibo.novel.parser.ProfileApiParser
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import retrofit2.HttpException
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

/** 手动/自动签到动作的结果，供调用方决定是否需要引导用户前往签到页。 */
enum class ManualSignResult {
    SUCCESS,
    ALREADY_SIGNED,
    WAF_BLOCKED,
    FAILED
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

/** 可见签到页解析结果：当日状态 + 页面给出的签到动作地址（可能为相对路径）。 */
internal data class CapturedSignPage(
    val status: TodaySignStatus,
    val actionUrl: String?
)

internal fun parseCapturedSignPage(html: String): CapturedSignPage {
    val document = Jsoup.parse(html)
    val pageText = document.text()
    val status = when {
        SIGNED_MARKERS.any(pageText::contains) -> TodaySignStatus.SIGNED
        document.select(".signbtn, a[href*=zqlj_sign], input[value*=签到]").isNotEmpty() ||
                NOT_SIGNED_MARKERS.any(pageText::contains) -> TodaySignStatus.NOT_SIGNED
        else -> TodaySignStatus.UNKNOWN
    }
    val actionUrl = document.select("a[href*=zqlj_sign], form[action*=zqlj_sign]")
        .asSequence()
        .map { it.attr("href").ifBlank { it.attr("action") } }
        .map(String::trim)
        .firstOrNull { raw -> raw.isNotBlank() && (raw.contains("sign=") || raw.contains("formhash=")) }
    return CapturedSignPage(status, actionUrl)
}

private val SIGNED_MARKERS = listOf(
    "今日已签到", "今日已簽到", "今日已打卡",
    "今天已签到", "今天已簽到", "今天已打卡",
    "已经签到", "已經簽到", "已经打过卡了", "已經打過卡了",
    "已完成签到", "已完成簽到", "已签到", "已簽到", "已打卡"
)
private val NOT_SIGNED_MARKERS = listOf("立即签到", "立即簽到", "我要签到", "我要簽到")
private val ACTION_SUCCESS_MARKERS = listOf("打卡成功", "签到成功", "簽到成功", "获得了", "獲得了")
private val ACTION_ALREADY_MARKERS = listOf(
    "已经打过卡了", "已經打過卡了", "今日已打卡", "重复操作", "重複操作"
)

/** 简化版的挑战页识别：CF Turnstile / nox 中间件响应体里常见的标记。 */
private fun looksLikeChallengePage(body: String): Boolean {
    val normalized = body.lowercase(Locale.ROOT)
    return normalized.contains("cf-chl-") ||
            normalized.contains("challenge-platform") ||
            normalized.contains("cloudflare") ||
            normalized.contains("turnstile") ||
            normalized.contains("just a moment") ||
            normalized.contains("__noxexpire") ||
            normalized.contains("gangplank_")
}

/**
 * 后台自动签到
 *
 * 签到页由 Cloudflare 类交互验证保护，OkHttp 请求会拿到 403 挑战页，且隐藏 WebView
 * 挑战永远无法通过。因此：用户打开可见签到页完成验证后，页面 HTML 会被
 * [captureSignPageHtml] 捕获并解析；之后再用带 cf_clearance 的会话请求执行签到动作。
 */
object AutoSignManager {
    private const val BASE_URL = "https://bbs.yamibo.com/"
    private const val MAX_DAILY_RETRIES = 2
    private val signMutex = Mutex()
    private val _todaySignStatus = MutableStateFlow<TodaySignStatus?>(null)
    val todaySignStatus = _todaySignStatus.asStateFlow()

    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class CapturedSignAction(
        val url: String,
        val accountHash: Int,
        val date: String
    )

    @Volatile
    private var capturedSignAction: CapturedSignAction? = null

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

    /**
     * WAF 已明确拦截后台请求时，当天停止继续自动尝试。
     *
     * 之前会退还本次配额，导致每次冷启动/回前台都再次触发同一个验证弹窗；
     * 可见签到页一旦真正加载成功，[captureSignPageHtml] 会重置配额并补签。
     */
    private suspend fun suspendAutomaticRetriesForToday(hash: Int, date: String) {
        updateQuota(hash, date, MAX_DAILY_RETRIES, 0)
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
        _todaySignStatus.value = status
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

        return launchCount + resumeCount < MAX_DAILY_RETRIES
    }

    /**
     * 可见签到页每次加载完成后把页面 HTML 交给这里解析：
     * 挑战页（仍在验证）直接忽略；真实签到页则缓存当日状态与签到动作地址，
     * 并顺带在未签到时自动签一次（与启动/恢复共用每日配额）。
     */
    fun captureSignPageHtml(html: String) {
        if (html.isBlank()) return
        if (looksLikeChallengePage(html)) return
        if (!html.contains("zqlj_sign") &&
            !html.contains("签到") && !html.contains("簽到") &&
            !html.contains("打卡")
        ) {
            return
        }
        val accountHash = getCurrentAccountHash() ?: return
        val snapshot = html
        captureScope.launch {
            val captured = parseCapturedSignPage(snapshot)
            val today = getServerToday()
            if (captured.status != TodaySignStatus.UNKNOWN) {
                saveTodaySignStatus(accountHash, today, captured.status)
            }
            capturedSignAction = captured.actionUrl
                ?.takeIf(String::isNotBlank)
                ?.let { CapturedSignAction(it, accountHash, today) }
            // 用户刚通过验证、页面显示未签到：解除 WAF 熔断并顺手自动签一次。
            if (captured.status == TodaySignStatus.NOT_SIGNED &&
                GlobalData.isAutoSignInEnabled.value
            ) {
                resetQuota(accountHash)
                GlobalData.applicationContext?.let { context ->
                    checkAndSignIfNeeded(context, SignTrigger.RESUME, force = false)
                }
            }
        }
    }

    suspend fun getTodaySignStatus(forceRefresh: Boolean = false): TodaySignStatus = withContext(Dispatchers.IO) {
        val accountHash = getCurrentAccountHash() ?: return@withContext TodaySignStatus.UNKNOWN
        val today = getServerToday()
        val cached = getCachedTodaySignStatus(accountHash, today)
        if (!forceRefresh) {
            cached?.let {
                _todaySignStatus.value = it
                return@withContext it
            }
        }

        val status = runCatching {
            val html = YamiboRetrofit.getInstance()
                .create(SignApi::class.java)
                .fetchHtml("${BASE_URL}plugin.php?id=zqlj_sign")
                .string()
            val document = Jsoup.parse(html)
            val pageText = document.text()
            if (SIGNED_MARKERS.any(pageText::contains)) {
                TodaySignStatus.SIGNED
            } else if (
                document.select(".signbtn, a[href*=zqlj_sign], input[value*=签到]").isNotEmpty() ||
                NOT_SIGNED_MARKERS.any(pageText::contains)
            ) {
                TodaySignStatus.NOT_SIGNED
            } else {
                TodaySignStatus.UNKNOWN
            }
        }.getOrDefault(TodaySignStatus.UNKNOWN)

        if (status == TodaySignStatus.UNKNOWN) {
            // 网络被 WAF 拦截时，保留当天从可见签到页捕获的状态，避免 UI 状态倒退。
            cached?.let {
                _todaySignStatus.value = it
                return@withContext it
            }
            _todaySignStatus.value = status
        } else {
            saveTodaySignStatus(accountHash, today, status)
            _todaySignStatus.value = status
        }
        status
    }

    suspend fun checkAndSignIfNeeded(
        context: Context,
        trigger: SignTrigger = SignTrigger.LAUNCH,
        force: Boolean = false
    ): ManualSignResult = signMutex.withLock {
        withContext(Dispatchers.IO) {
            val accountHash = getCurrentAccountHash()
                ?: return@withContext ManualSignResult.FAILED
            val today = getServerToday()
            var (savedDate, launchCount, resumeCount) = getCurrentQuota(accountHash)

            if (today != savedDate) {
                launchCount = 0
                resumeCount = 0
            }

            var consumedQuota = false
            if (!force) {
                if (launchCount + resumeCount >= MAX_DAILY_RETRIES) {
                    return@withContext ManualSignResult.FAILED
                }
                consumedQuota = true
                if (trigger == SignTrigger.LAUNCH) launchCount++ else resumeCount++
                // 先消耗次数，403/网络异常也不能被启动和恢复回调反复重试。
                updateQuota(accountHash, today, launchCount, resumeCount)
            }

            // 只有「请求从未到达论坛」（表单验证失败、被 WAF 拦截、网络异常）才退还本次
            // 配额，保证用户在签到页通过验证后仍能在配额内自动补签一次。
            val refundQuota: suspend () -> Unit = {
                if (consumedQuota) {
                    if (trigger == SignTrigger.LAUNCH) {
                        updateQuota(accountHash, today, (launchCount - 1).coerceAtLeast(0), resumeCount)
                    } else {
                        updateQuota(accountHash, today, launchCount, (resumeCount - 1).coerceAtLeast(0))
                    }
                }
            }

            try {
                // 使用移动 API 的 formhash，避免直接抓插件页面触发 403/WAF。
                val profileResponse = YamiboRetrofit.getInstance()
                    .create(FavoriteApi::class.java)
                    .getFormHash()
                    .execute()
                val profileHtml = if (profileResponse.isSuccessful) {
                    profileResponse.body()?.string()
                } else {
                    null
                }
                val formHash = profileHtml
                    ?.let { runCatching { ProfileApiParser.parseProfile(it).formhash }.getOrNull() }
                    ?.takeIf(String::isNotBlank)
                if (formHash == null) {
                    val wafBlocked = profileResponse.code() == 403 ||
                            profileResponse.code() == 405 ||
                            looksLikeChallengePage(profileHtml.orEmpty())
                    if (wafBlocked) {
                        if (!force) suspendAutomaticRetriesForToday(accountHash, today)
                        AppErrorLog.record("签到被 WAF 拦截：请在签到页完成验证后重试")
                        if (force) showToast(context, "签到请求被论坛拦截，请稍后重试")
                        return@withContext ManualSignResult.WAF_BLOCKED
                    }
                    refundQuota()
                    if (force) showToast(context, "签到页面验证失败，请重新登录后重试")
                    return@withContext ManualSignResult.FAILED
                }

                val signUrl = resolveSignActionUrl(formHash, accountHash, today)
                val actionResponseHtml = YamiboRetrofit.getInstance()
                    .create(SignApi::class.java)
                    .fetchHtml(signUrl)
                    .string()

                when {
                    ACTION_ALREADY_MARKERS.any(actionResponseHtml::contains) -> {
                        saveTodaySignStatus(accountHash, today, TodaySignStatus.SIGNED)
                        if (force) showToast(context, "今日已签到")
                        ManualSignResult.ALREADY_SIGNED
                    }

                    ACTION_SUCCESS_MARKERS.any(actionResponseHtml::contains) -> {
                        saveTodaySignStatus(accountHash, today, TodaySignStatus.SIGNED)
                        AppErrorLog.record("自动签到成功")
                        showToast(context, "签到成功")
                        ManualSignResult.SUCCESS
                    }

                    looksLikeChallengePage(actionResponseHtml) -> {
                        if (!force) suspendAutomaticRetriesForToday(accountHash, today)
                        AppErrorLog.record("签到被 WAF 拦截：请在签到页完成验证后重试")
                        if (force) showToast(context, "签到请求被论坛拦截，请稍后重试")
                        ManualSignResult.WAF_BLOCKED
                    }

                    else -> {
                        // 未出现成功/重复打卡标记就不能当作成功，否则会消耗当天配额，
                        // 但日历仍保持未打卡。允许后续启动或回前台继续重试。
                        refundQuota()
                        AppErrorLog.record("签到失败：响应未包含成功标记")
                        if (force) showToast(context, "签到失败，请稍后重试")
                        ManualSignResult.FAILED
                    }
                }
            } catch (error: HttpException) {
                val code = error.code()
                if (code == 403 || code == 405) {
                    if (!force) suspendAutomaticRetriesForToday(accountHash, today)
                    AppErrorLog.record("签到被 WAF 拦截($code)：请在签到页完成验证后重试")
                    if (force) showToast(context, "签到请求被论坛拦截，请稍后重试")
                    ManualSignResult.WAF_BLOCKED
                } else {
                    refundQuota()
                    AppErrorLog.record("签到请求失败：HTTP $code")
                    if (force) showToast(context, "签到请求失败，请稍后重试")
                    ManualSignResult.FAILED
                }
            } catch (error: Exception) {
                refundQuota()
                val detail = error.message ?: error::class.simpleName.orEmpty()
                AppErrorLog.record("签到请求失败：$detail")
                if (force) showToast(context, "签到请求失败，请稍后重试")
                ManualSignResult.FAILED
            }
        }
    }

    /** 使用 Lite 版长期采用的签到动作地址，避免 mobile/referer 改变插件响应。 */
    private fun resolveSignActionUrl(
        formHash: String,
        accountHash: Int,
        today: String
    ): String {
        val captured = capturedSignAction
            ?.takeIf { it.accountHash == accountHash && it.date == today }
            ?.url
            ?.takeIf(String::isNotBlank)
        return captured?.let(::absoluteSignActionUrl) ?: buildSignActionUrl(formHash)
    }

    internal fun absoluteSignActionUrl(url: String): String =
        if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        ) {
            url
        } else {
            BASE_URL + url.trimStart('/')
        }

    internal fun buildSignActionUrl(formHash: String): String =
        "${BASE_URL}plugin.php?id=zqlj_sign&sign=$formHash"

    internal fun extractSignFormHash(html: String): String? =
        Jsoup.parse(html)
            .selectFirst("#scbar_form input[name=formhash], input[name=formhash]")
            ?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotEmpty)

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
