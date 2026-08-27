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
import org.shirakawatyu.yamibo.novel.bean.space.BlogBatchActionResult
import org.shirakawatyu.yamibo.novel.bean.space.BlogBatchOperation
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail
import org.shirakawatyu.yamibo.novel.bean.space.PrivateMessageConversation
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.SpaceApi
import org.shirakawatyu.yamibo.novel.parser.SpaceDesktopParser
import org.shirakawatyu.yamibo.novel.parser.SpaceMobileParser
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import org.shirakawatyu.yamibo.novel.util.AppErrorLog

private const val SPACE_PAGE_SIZE = 20

internal fun mergeUserReplyItems(
    replies: List<SpaceListItem>,
    comments: List<SpaceListItem>
): List<SpaceListItem> =
    (replies + comments)
        .distinctBy { item ->
            val thread = item as? SpaceListItem.UserThread
            if (thread == null) item.toString()
            else "${thread.entryType}:${thread.postId}:${thread.url}"
        }
        .sortedByDescending { item ->
            val time = (item as? SpaceListItem.UserThread)?.time.orEmpty()
            Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")
                .find(time)
                ?.groupValues
                ?.drop(1)
                ?.mapNotNull(String::toLongOrNull)
                ?.fold(0L) { value, part -> value * 100L + part }
                ?: Long.MIN_VALUE
        }
        .take(SPACE_PAGE_SIZE)

class SpaceRepository(
    private val api: SpaceApi = YamiboRetrofit.getInstance().create(SpaceApi::class.java)
) {
    private val blogCategoryCache = ConcurrentHashMap<String, String>()
    private val userThreadTimeCache = ConcurrentHashMap<String, String>()

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
                page = page,
                perPage = SPACE_PAGE_SIZE
            ).string()
            SpacePageKind.NOTICE -> api.getSpacePage(
                doParam = "notice",
                page = page,
                perPage = SPACE_PAGE_SIZE
            ).string()
            SpacePageKind.FRIEND -> api.getSpacePage(
                doParam = "friend",
                view = request.view.ifBlank { null },
                type = request.type.ifBlank { null },
                page = page,
                perPage = SPACE_PAGE_SIZE
            ).string()
            SpacePageKind.DOING -> api.getSpacePage(
                doParam = "doing",
                view = request.view,
                page = page,
                perPage = SPACE_PAGE_SIZE
            ).string()
            SpacePageKind.BLOG -> api.getSpacePage(
                uid = request.uid,
                doParam = "blog",
                view = request.view,
                classId = request.categoryId.ifBlank { null },
                friendUid = request.fuid.ifBlank { null },
                page = page,
                perPage = SPACE_PAGE_SIZE
            ).string()
            SpacePageKind.USER_THREAD -> api.getSpacePage(
                uid = request.uid,
                doParam = "thread",
                view = "me",
                type = request.type.ifBlank { null },
                page = page,
                perPage = SPACE_PAGE_SIZE
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
        val mergedItems = enrichMissingUserThreadTimes(
            replies.items + comments?.items.orEmpty()
        )
        return SpaceListPage(
            items = mergeUserReplyItems(
                mergedItems.filter {
                    (it as? SpaceListItem.UserThread)?.entryType != "点评"
                },
                mergedItems.filter {
                    (it as? SpaceListItem.UserThread)?.entryType == "点评"
                }
            ),
            previousUrl = replies.previousUrl ?: comments?.previousUrl,
            nextUrl = replies.nextUrl ?: comments?.nextUrl
        )
    }

    private suspend fun enrichMissingUserThreadTimes(
        items: List<SpaceListItem>
    ): List<SpaceListItem> = coroutineScope {
        val threads = items.filterIsInstance<SpaceListItem.UserThread>()
        val unresolvedGroups = threads
            .filter { thread ->
                thread.time.isBlank() && thread.postId.isNotBlank() &&
                    thread.url.isNotBlank() &&
                    userThreadTimeCache["${thread.postId}:${thread.replyExcerpt}"] == null
            }
            .groupBy { thread -> "${thread.tid}:${thread.postId}" }
        val semaphore = Semaphore(2)
        val resolvedTimes = unresolvedGroups.values.map { group ->
            async {
                val target = group.first()
                val html = semaphore.withPermit {
                    runCatching {
                        api.getPageByUrl(
                            target.url,
                            "https://bbs.yamibo.com/home.php?mod=space&do=thread&view=me&mobile=no"
                        ).string()
                    }.onFailure {
                        AppErrorLog.record(
                            "我的回复发布时间补全失败 pid=${target.postId}：${it.message}"
                        )
                    }.getOrDefault("")
                }
                group.associate { thread ->
                    val cacheKey = "${thread.postId}:${thread.replyExcerpt}"
                    val time = SpaceDesktopParser.parseUserThreadTargetTime(
                        html = html,
                        postId = thread.postId,
                        excerpt = thread.replyExcerpt
                    )
                    if (time.isNotBlank()) userThreadTimeCache.putIfAbsent(cacheKey, time)
                    cacheKey to time
                }
            }
        }.awaitAll().flatMap { it.entries }.associate { it.toPair() }

        items.map { item ->
            val thread = item as? SpaceListItem.UserThread ?: return@map item
            if (thread.time.isNotBlank()) return@map thread
            val cacheKey = "${thread.postId}:${thread.replyExcerpt}"
            thread.copy(time = userThreadTimeCache[cacheKey] ?: resolvedTimes[cacheKey].orEmpty())
        }
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
        val lowerUrl = actionUrl.lowercase()
        // Discuz 的 docomment 入口由 ajaxget 加载；不带 inajax 时可能返回整张空间页，
        // 此时直接取第一个 form 会误拿到搜索/发布记录表单。
        val actionPageUrl = desktopAjaxUrl(actionUrl)
        val formSelector = if ("op=delete" in lowerUrl) {
            "form[action*='ac=doing'][action*='op=delete']"
        } else {
            "form[action*='ac=doing'][action*='op=comment']"
        }
        val form = parseForm(
            executeDesktopGet(actionPageUrl, referer),
            actionPageUrl,
            formSelector
        )
        val fields = form.fields.toMutableMap()
        val formHash = fields["formhash"].orEmpty().ifBlank { GlobalData.currentFormHash }
        if (formHash.isNotBlank()) fields["formhash"] = formHash
        if (text != null) fields["message"] = text

        when {
            "op=delete" in lowerUrl -> fields.putIfAbsent("deletesubmit", "true")
            "op=docomment" in lowerUrl -> fields.putIfAbsent("commentsubmit", "true")
        }
        verifyActionResponse(executeDesktopPost(desktopAjaxUrl(form.action), fields, referer))

        // 提交后必须绕过 Retrofit/HTTP 缓存重新抓取当前电脑版页面，否则 UI 会继续显示旧评论。
        val refreshedHtml = executeDesktopGet(referer, referer)
        if (SpaceMobileParser.isLoginRequired(refreshedHtml)) {
            throw IllegalStateException("请先登录后再操作")
        }
        return SpaceDesktopParser.parseListPage(SpacePageKind.DOING, refreshedHtml)
    }

    private fun doingReferer(request: SpaceListRequest, page: Int): String = buildString {
        append("https://bbs.yamibo.com/home.php?mod=space&do=doing")
        request.uid.takeIf(String::isNotBlank)?.let { append("&uid=").append(it) }
        request.view.takeIf(String::isNotBlank)?.let { append("&view=").append(it) }
        append("&page=").append(page.coerceAtLeast(1))
        append("&perpage=").append(SPACE_PAGE_SIZE)
        append("&mobile=no")
    }

    suspend fun getBlogDetail(url: String): BlogDetail {
        val desktopUrl = desktopUrl(url)
        val html = executeDesktopGet(desktopUrl, url)
        return SpaceDesktopParser.parseBlogDetail(html, desktopUrl)
    }

    suspend fun performBlogBatchAction(
        items: List<SpaceListItem.Blog>,
        operation: BlogBatchOperation
    ): BlogBatchActionResult = coroutineScope {
        val semaphore = Semaphore(2)
        val results = items.distinctBy(SpaceListItem.Blog::blogId).map { item ->
            async {
                semaphore.withPermit {
                    try {
                        when (operation) {
                            BlogBatchOperation.PIN ->
                                submitBlogManagementAction(item.stickUrl, item.url)
                            BlogBatchOperation.DELETE ->
                                submitBlogManagementAction(item.deleteUrl, item.url)
                            else -> updateBlogVisibility(
                                item = item,
                                visibilityValue = requireNotNull(operation.visibilityValue)
                            )
                        }
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppErrorLog.record(
                            "日志批量操作失败 operation=$operation blogId=${item.blogId}：${e.message}"
                        )
                        false
                    }
                }
            }
        }.awaitAll()
        BlogBatchActionResult(
            succeeded = results.count { it },
            failed = results.count { !it }
        )
    }

    suspend fun submitBlogManagementAction(actionUrl: String, referer: String) {
        if (actionUrl.isBlank()) throw IllegalStateException("日志操作入口不可用，请刷新后重试")
        val lowerUrl = actionUrl.lowercase()
        val actionPageUrl = desktopAjaxUrl(actionUrl)
        val selector = when {
            "op=stick" in lowerUrl -> "form[action*='ac=blog'][action*='op=stick']"
            "op=delete" in lowerUrl -> "form[action*='ac=blog'][action*='op=delete']"
            else -> "form[action*='ac=blog']"
        }
        val form = parseForm(
            executeDesktopGet(actionPageUrl, referer),
            actionPageUrl,
            selector
        )
        val fields = form.fields.toMutableMap()
        val formHash = fields["formhash"].orEmpty().ifBlank { GlobalData.currentFormHash }
        if (formHash.isNotBlank()) fields["formhash"] = formHash
        verifyActionResponse(
            executeDesktopPost(desktopAjaxUrl(form.action), fields, referer)
        )
    }

    private fun updateBlogVisibility(
        item: SpaceListItem.Blog,
        visibilityValue: String
    ) {
        val editUrl = item.editUrl.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("日志编辑入口不可用，请刷新后重试")
        val pageUrl = desktopUrl(editUrl)
        val form = parseForm(
            executeDesktopGet(pageUrl, item.url),
            pageUrl,
            "form#ttHtmlEditor[action*='ac=blog']"
        )
        val fields = form.fields.toMutableMap()
        fields["friend"] = visibilityValue
        fields["blogsubmit"] = "true"
        fields.remove("makefeed")
        val formHash = fields["formhash"].orEmpty().ifBlank { GlobalData.currentFormHash }
        if (formHash.isNotBlank()) fields["formhash"] = formHash
        verifyActionResponse(executeDesktopPost(form.action, fields, item.url))
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

    private fun parseForm(
        html: String,
        pageUrl: String,
        selector: String = "form"
    ): DesktopForm {
        val payloads = buildList {
            add(html)
            Regex("<!\\[CDATA\\[(.*)]]>", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::add)
        }
        val form = payloads.firstNotNullOfOrNull { payload ->
            Jsoup.parse(payload, pageUrl).selectFirst(selector) as? FormElement
        } ?: throw IllegalStateException("操作表单加载失败，请刷新后重试")
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
        form.select("select[name]").forEach { select ->
            val selected = select.selectFirst("option[selected]")
                ?: select.selectFirst("option")
            if (selected != null) {
                fields[select.attr("name")] = selected.attr("value")
            }
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

    private fun desktopAjaxUrl(url: String): String =
        url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.setQueryParameter("mobile", "no")
            ?.setQueryParameter("inajax", "1")
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
        return if (html.contains("id=\"pm_ul\"") || html.contains("id='pm_ul'")) {
            SpaceDesktopParser.parsePrivateMessageConversation(html, url)
        } else {
            SpaceMobileParser.parsePrivateMessageConversation(html, url)
        }
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
