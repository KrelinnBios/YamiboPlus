package org.shirakawatyu.yamibo.novel.repository

import android.content.Context
import okhttp3.ResponseBody
import org.shirakawatyu.yamibo.novel.bean.SignPageData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.parser.SignPageParser
import org.shirakawatyu.yamibo.novel.util.AutoSignManager
import org.shirakawatyu.yamibo.novel.util.reader.AuthenticatedWebViewPageLoader
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

private interface SignPageApi {
    @GET
    suspend fun fetch(
        @Url url: String,
        @Header("Cache-Control") cacheControl: String = "no-cache"
    ): ResponseBody
}

class SignRepository {
    private val api: SignPageApi = YamiboRetrofit.getInstance().create(SignPageApi::class.java)

    suspend fun getPage(
        context: Context,
        year: Int? = null,
        month: Int? = null
    ): SignPageData {
        val directUrl = buildUrl(year, month, mobile = false)
        runCatching {
            val html = api.fetch(directUrl).string()
            // 原生签到页只同步状态与动作地址，不在页面加载后悄悄触发一次自动签到；
            // 否则按钮仍显示“点击打卡”时，头像可能已经先变成已签到的绿色。
            AutoSignManager.captureSignPageHtml(html, autoSignIfNeeded = false)
            SignPageParser.parse(html)
        }
            .getOrNull()
            ?.let { return it }

        val webUrl = buildUrl(year, month, mobile = true)
        val page = AuthenticatedWebViewPageLoader.fetch(
            context = context,
            url = webUrl,
            readyPredicate = { html ->
                html.contains("id=\"tablehead\"") &&
                    html.contains("id=\"tablebody\"")
            }
        ) ?: throw IllegalStateException("论坛拒绝了签到页数据请求，请刷新重试")

        AutoSignManager.captureSignPageHtml(page.html, autoSignIfNeeded = false)
        return runCatching { SignPageParser.parse(page.html) }
            .getOrElse {
                throw IllegalStateException("签到页面尚未通过论坛验证，请刷新重试", it)
            }
    }

    private fun buildUrl(year: Int?, month: Int?, mobile: Boolean): String =
        buildString {
            append("https://bbs.yamibo.com/plugin.php?id=zqlj_sign")
            if (mobile) append("&mobile=2")
            if (year != null && month != null) {
                append("&y=").append(year)
                append("&m=").append(month)
            }
        }
}
