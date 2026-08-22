package org.shirakawatyu.yamibo.novel.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.FormElement
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListPage
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListRequest
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail
import org.shirakawatyu.yamibo.novel.bean.space.PrivateMessageConversation
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.SpaceApi
import org.shirakawatyu.yamibo.novel.parser.SpaceDesktopParser
import org.shirakawatyu.yamibo.novel.parser.SpaceMobileParser
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import org.shirakawatyu.yamibo.novel.util.AppErrorLog

class SpaceRepository(
    private val api: SpaceApi = YamiboRetrofit.getInstance().create(SpaceApi::class.java)
) {
    private val blogCategoryCache = ConcurrentHashMap<String, String>()

    private companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    suspend fun getList(request: SpaceListRequest, page: Int): SpaceListPage {
        if (request.kind == SpacePageKind.USER_THREAD && request.type == "reply") {
            return getRepliesWithPostComments(request, page)
        }
        val html = when (request.kind) {
            SpacePageKind.PRIVATE_MESSAGE -> api.getSpacePage(
                doParam = "pm",
                page = page
            ).string()
            SpacePageKind.NOTICE -> api.getSpacePage(
                doParam = "notice",
                page = page
            ).string()
            SpacePageKind.FRIEND -> api.getSpacePage(
                doParam = "friend",
                view = request.view.ifBlank { null },
                type = request.type.ifBlank { null },
                page = page
            ).string()
            SpacePageKind.DOING -> api.getSpacePage(
                doParam = "doing",
                view = request.view,
                page = page
            ).string()
            SpacePageKind.BLOG -> api.getSpacePage(
                uid = request.uid,
                doParam = "blog",
                view = request.view,
                classId = request.categoryId.ifBlank { null },
                friendUid = request.fuid.ifBlank { null },
                page = page
            ).string()
            SpacePageKind.USER_THREAD -> api.getSpacePage(
                uid = request.uid,
                doParam = "thread",
                view = "me",
                type = request.type.ifBlank { null },
                page = page
            ).string()
        }
        val result = SpaceDesktopParser.parseListPage(request.kind, html)
        if (result.items.isEmpty()) {
            if (SpaceMobileParser.isLoginRequired(html)) {
                throw IllegalStateException("需要登录后才能查看此页面")
            }
            AppErrorLog.record(
                "空间页解析为空 kind=${request.kind} view=${request.view} page=$page"
            )
        }
        return result
    }

    private suspend fun getRepliesWithPostComments(
        request: SpaceListRequest,
        page: Int
    ): SpaceListPage {
        suspend fun load(type: String): SpaceListPage {
            val html = api.getSpacePage(
                uid = request.uid,
                doParam = "thread",
                view = "me",
                type = type,
                page = page
            ).string()
            return SpaceDesktopParser.parseListPage(SpacePageKind.USER_THREAD, html)
        }

        val replies = load("reply")
        val comments = runCatching { load("postcomment") }
            .onFailure { AppErrorLog.record("点评列表加载失败：${it.message}") }
            .getOrNull()
        return SpaceListPage(
            items = replies.items + comments?.items.orEmpty(),
            previousUrl = replies.previousUrl ?: comments?.previousUrl,
            nextUrl = replies.nextUrl ?: comments?.nextUrl
        )
    }

    suspend fun getListByUrl(request: SpaceListRequest, url: String): SpaceListPage {
        val html = api.getPageByUrl(url).string()
        val result = SpaceDesktopParser.parseListPage(request.kind, html)
        if (result.items.isEmpty()) {
            if (SpaceMobileParser.isLoginRequired(html)) {
                throw IllegalStateException("需要登录后才能查看此页面")
            }
            AppErrorLog.record("空间页解析为空 kind=${request.kind} url=$url")
        }
        return result
    }

    /** 使用电脑版 doing 弹窗表单执行回复或删除，并重新加载当前页。 */
    suspend fun submitDoingAction(
        request: SpaceListRequest,
        page: Int,
        actionUrl: String,
        message: String? = null
    ): SpaceListPage {
        require(request.kind == SpacePageKind.DOING) { "仅记录页面支持此操作" }
        if (actionUrl.isBlank()) throw IllegalStateException("记录操作入口不可用，请刷新后重试")
        val text = message?.trim()
        if (message != null && text.isNullOrBlank()) throw IllegalArgumentException("请输入回复内容")

        val referer = doingReferer(request, page)
        val actionPageUrl = desktopUrl(actionUrl)
        val form = parseForm(executeDesktopGet(actionPageUrl, referer), actionPageUrl)
        val fields = form.fields.toMutableMap()
        val formHash = fields["formhash"].orEmpty().ifBlank { GlobalData.currentFormHash }
        if (formHash.isNotBlank()) fields["formhash"] = formHash
        if (text != null) fields["message"] = text

        val lowerUrl = actionUrl.lowercase()
        when {
            "op=delete" in lowerUrl -> fields.putIfAbsent("deletesubmit", "true")
            "op=docomment" in lowerUrl -> fields.putIfAbsent("commentsubmit", "true")
        }
        verifyActionResponse(executeDesktopPost(form.action, fields, referer))
        return getList(request, page.coerceAtLeast(1))
    }

    private fun doingReferer(request: SpaceListRequest, page: Int): String = buildString {
        append("https://bbs.yamibo.com/home.php?mod=space&do=doing")
        request.uid.takeIf(String::isNotBlank)?.let { append("&uid=").append(it) }
        request.view.takeIf(String::isNotBlank)?.let { append("&view=").append(it) }
        append("&page=").append(page.coerceAtLeast(1))
        append("&mobile=no")
    }

    suspend fun getBlogDetail(url: String): BlogDetail {
        val desktopUrl = desktopUrl(url)
        val html = executeDesktopGet(desktopUrl, url)
        return SpaceDesktopParser.parseBlogDetail(html, desktopUrl)
    }

    /**
     * 好友日志列表模板不输出个人分类，只能从详情页补齐。
     * 在列表先显示后于后台调用；限制并发并缓存空结果，避免刷新或切页重复打论坛。
     */
    suspend fun getMissingBlogCategories(
        items: List<SpaceListItem.Blog>
    ): Map<String, String> = coroutineScope {
        val candidates = items
            .filter { it.category.isBlank() && it.blogId.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.blogId }
        val semaphore = Semaphore(3)
        candidates.map { blog ->
            async {
                val cached = blogCategoryCache[blog.blogId]
                if (cached != null) {
                    blog.blogId to cached
                } else {
                    val category = semaphore.withPermit {
                        try {
                            getBlogDetail(blog.url).category
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppErrorLog.record(
                                "好友日志分类补全失败 blogId=${blog.blogId}：${e.message}"
                            )
                            ""
                        }
                    }
                    blogCategoryCache.putIfAbsent(blog.blogId, category)
                    blog.blogId to category
                }
            }
        }.awaitAll().toMap()
    }

    suspend fun submitBlogComment(
        pageUrl: String,
        detail: BlogDetail,
        message: String
    ): BlogDetail {
        val text = message.trim()
        if (text.isBlank()) throw IllegalArgumentException("请输入评论内容")
        val actionUrl = detail.commentFormUrl.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("评论入口不可用，请刷新日志")
        val formHash = detail.commentFormHash.ifBlank { GlobalData.currentFormHash }
            .takeIf(String::isNotBlank)
            ?: throw IllegalStateException("评论校验已失效，请刷新日志")
        val fields = linkedMapOf(
            "referer" to detail.commentReferer.ifBlank { pageUrl },
            "id" to detail.blogId,
            "idtype" to "blogid",
            "handlekey" to "qcblog_${detail.blogId}",
            "commentsubmit" to "true",
            "quickcomment" to "true",
            "message" to text,
            "formhash" to formHash
        )
        verifyActionResponse(executeDesktopPost(actionUrl, fields, pageUrl))
        return getBlogDetail(pageUrl)
    }

    suspend fun submitBlogCommentAction(
        pageUrl: String,
        actionUrl: String,
        message: String? = null
    ): BlogDetail {
        if (actionUrl.isBlank()) throw IllegalStateException("评论操作不可用")
        val actionPageUrl = desktopUrl(actionUrl)
        val formHtml = executeDesktopGet(actionPageUrl, pageUrl)
        val form = parseForm(formHtml, actionPageUrl)
        val fields = form.fields.toMutableMap()
        val formHash = fields["formhash"].orEmpty().ifBlank { GlobalData.currentFormHash }
        if (formHash.isNotBlank()) fields["formhash"] = formHash
        if (message != null) fields["message"] = message.trim()

        val lowerUrl = actionUrl.lowercase()
        when {
            "op=delete" in lowerUrl -> fields.putIfAbsent("deletesubmit", "true")
            "op=edit" in lowerUrl -> fields.putIfAbsent("editsubmit", "true")
            "op=reply" in lowerUrl -> fields.putIfAbsent("commentsubmit", "true")
        }
        verifyActionResponse(executeDesktopPost(form.action, fields, pageUrl))
        return getBlogDetail(pageUrl)
    }

    private fun executeDesktopGet(url: String, referer: String): String {
        val cookie = YamiboSession.desktopCookie(YamiboSession.cookieFor(url))
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Cookie", cookie)
            .header("Referer", referer)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()
            .build()
        return YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("电脑版空间页面请求失败：HTTP ${response.code}")
            }
            YamiboSession.storeSetCookies(
                response.request.url.toString(),
                response.headers("Set-Cookie")
            )
            response.body?.string().orEmpty()
        }
    }

    private fun executeDesktopPost(
        url: String,
        fields: Map<String, String>,
        referer: String
    ): String {
        val cookie = YamiboSession.desktopCookie(YamiboSession.cookieFor(url))
        val body = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder()
            .url(desktopUrl(url))
            .header("User-Agent", DESKTOP_UA)
            .header("Cookie", cookie)
            .header("Referer", referer)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .post(body)
            .build()
        return YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("空间操作失败：HTTP ${response.code}")
            }
            YamiboSession.storeSetCookies(
                response.request.url.toString(),
                response.headers("Set-Cookie")
            )
            response.body?.string().orEmpty()
        }
    }

    private fun parseForm(html: String, pageUrl: String): DesktopForm {
        val document = Jsoup.parse(html, pageUrl)
        val form = document.selectFirst("form") as? FormElement
            ?: throw IllegalStateException("操作表单加载失败，请刷新后重试")
        val fields = linkedMapOf<String, String>()
        form.select("input[name]").forEach { input ->
            val type = input.attr("type").lowercase()
            if (type != "checkbox" && type != "radio" || input.hasAttr("checked")) {
                fields[input.attr("name")] = input.attr("value")
            }
        }
        form.select("textarea[name]").forEach { textarea ->
            fields[textarea.attr("name")] = textarea.text()
        }
        form.select("button[name], input[type=submit][name]").firstOrNull()?.let { submit ->
            fields[submit.attr("name")] = submit.attr("value")
        }
        val action = form.absUrl("action").ifBlank { pageUrl }
        return DesktopForm(desktopUrl(action), fields)
    }

    private fun verifyActionResponse(body: String) {
        if (SpaceMobileParser.isLoginRequired(body)) {
            throw IllegalStateException("请先登录后再操作")
        }
        val text = Jsoup.parse(body).text()
        val failure = listOf(
            "权限不足", "无权操作", "操作失败", "提交失败", "验证码错误",
            "请先登录", "尚未登录"
        )
            .firstOrNull(text::contains)
        if (failure != null) throw IllegalStateException(failure)
    }

    private fun desktopUrl(url: String): String =
        url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.setQueryParameter("mobile", "no")
            ?.build()
            ?.toString()
            ?: url

    private data class DesktopForm(
        val action: String,
        val fields: Map<String, String>
    )

    suspend fun getPrivateMessageConversation(url: String): PrivateMessageConversation {
        val html = api.getPageByUrl(url).string()
        if (SpaceMobileParser.isLoginRequired(html)) {
            throw IllegalStateException("需要登录后才能查看私信")
        }
        return SpaceMobileParser.parsePrivateMessageConversation(html, url)
    }

    suspend fun sendPrivateMessage(
        conversation: PrivateMessageConversation,
        message: String
    ): PrivateMessageConversation {
        if (conversation.formHash.isBlank() || conversation.touid.isBlank()) {
            throw IllegalStateException("会话信息已失效，请返回重新进入")
        }
        api.sendPrivateMessage(
            pmid = conversation.pmid,
            formHash = conversation.formHash,
            touid = conversation.touid,
            message = message
        ).string()
        return getPrivateMessageConversation(
            "https://bbs.yamibo.com/home.php?mod=space&do=pm&subop=view&touid=${conversation.touid}&mobile=2"
        )
    }
}
