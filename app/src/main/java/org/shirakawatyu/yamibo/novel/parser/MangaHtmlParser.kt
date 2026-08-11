package org.shirakawatyu.yamibo.novel.parser

import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.MangaChapterItem
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner
import java.text.SimpleDateFormat
import java.util.Locale

class MangaHtmlParser {
    companion object {
        /**
         * 解析手机端HTML，寻找Tag ID
         */
        fun findTagIdsMobile(html: String): List<String> {
            val doc = Jsoup.parse(html)
            val tagLinks = doc.select("a[href*='mod=tag']")

            val ids = mutableListOf<String>()
            for (link in tagLinks) {
                val href = link.attr("href")
                val match = Regex("id=(\\d+)").find(href)
                match?.groupValues?.get(1)?.let { ids.add(it) }
            }
            return ids.distinct()
        }

        /**
         * 从URL提取UID
         */
        private fun extractUidFromUrl(url: String): String? {
            // 兼容 "uid=123" 和 "space-uid-123.html" 两种格式
            val match = Regex("uid=(\\d+)").find(url) ?: Regex("uid-(\\d+)").find(url)
            return match?.groupValues?.get(1)
        }

        /**
         * 提取1楼正文中的所有内部 TID 超链接（含 ptid 链接和 threadindex 目录）
         */
        fun extractSamePageLinks(html: String): List<MangaChapterItem> {
            val doc = Jsoup.parse(html)
            val messageDiv = doc.select(".message").firstOrNull() ?: return emptyList()

            val result = mutableListOf<MangaChapterItem>()
            val links = messageDiv.select(
                "a[href*='tid='], a[href*='thread-'], a[href*='ptid=']"
            )

            for (link in links) {
                val url = link.attr("href")
                val title = link.text().trim()
                if (title.isBlank() ||
                    MangaTitleCleaner.isUrlLikeChapterTitle(title) ||
                    MangaTitleCleaner.isNavigationLinkTitle(title)
                ) continue
                val tid = MangaTitleCleaner.extractTidFromUrl(url) ?: continue
                val chapterNum = MangaTitleCleaner.extractChapterNum(title)
                val pid = extractPidFromUrl(url)
                val safeUrl = buildThreadUrl(tid, pid)
                result.add(MangaChapterItem(tid, title, chapterNum, safeUrl, null, null, pid = pid))
            }
            return result
        }

        /**
         * 从 URL 提取 pid（用于单帖多章时区分楼层）
         * 匹配 mod=redirect&goto=findpost&ptid=TID&pid=PID 中的 pid
         */
        private fun extractPidFromUrl(url: String): String? {
            return Regex("[?&]pid=(\\d+)").find(url)?.groupValues?.get(1)
        }

        private fun buildThreadUrl(tid: String, pid: String? = null): String {
            val base = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$tid&mobile=2"
            return if (pid.isNullOrBlank()) base else "$base&pid=$pid"
        }

        /**
         * 首楼是否明确声明了作品目录。
         *
         * 这类目录的链接顺序由发布者手工编排，不能再按帖子 TID 推测章节顺序。
         * 只认带“本作/作品/漫画/章节”等限定词的目录标题，避免把普通“返回目录”
         * 导航误当成权威章节表。
         */
        fun hasExplicitMangaDirectoryMarker(html: String): Boolean {
            val doc = Jsoup.parse(html)
            val firstPostContent = doc.select(
                ".message, .t_f[id^=postmessage_], [id^=postmessage_]"
            ).firstOrNull() ?: return false
            val compactText = firstPostContent.text().replace(Regex("\\s+"), "")
            return Regex("(?:本作|本帖|作品|漫画|漫畫|章节|章節)目[录錄]")
                .containsMatchIn(compactText)
        }

        /**
         * 提取 #threadindex .tindex 目录（Discuz! 插件生成的页内目录）
         * 这些链接的 href 为 javascript:;，通过 onclick 中的 viewpid 定位楼层。
         * 章节标题可能在 <a> 内或直接为 <li> 文本，两者皆处理。
         */
        fun extractThreadindexLinks(html: String): List<MangaChapterItem> {
            val doc = Jsoup.parse(html)
            val items = doc.select("#threadindex .tindex li")
            if (items.isEmpty()) return emptyList()

            val result = mutableListOf<MangaChapterItem>()
            for (li in items) {
                val onclick = li.attr("onclick")
                val viewPidMatch = Regex("viewpid=(\\d+)").find(onclick)
                val tidMatch = Regex("tid=(\\d+)").find(onclick)
                if (viewPidMatch == null || tidMatch == null) continue
                val pid = viewPidMatch.groupValues[1]
                val tid = tidMatch.groupValues[1]
                val title = li.select("a").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
                    ?: li.text().trim().takeIf { it.isNotBlank() }
                    ?: continue
                val chapterNum = MangaTitleCleaner.extractChapterNum(title)
                val safeUrl = buildThreadUrl(tid, pid)
                result.add(MangaChapterItem(tid, title, chapterNum, safeUrl, null, null, pid = pid))
            }
            return result
        }

        /**
         * 从"只看楼主"过滤后的 HTML 中提取所有楼主发帖，识别为小说章节。
         * 每层楼取首个 <strong>/<b> 文本作为章节标题，pid 作为章节ID。
         */
        fun extractChaptersFromAuthorFilteredHtml(
            html: String,
            tid: String
        ): List<MangaChapterItem> {
            val doc = Jsoup.parse(html)
            val result = mutableListOf<MangaChapterItem>()

            // PC 版：table[id^=pid]
            val pcPosts = doc.select("table[id^=pid]")
            for (table in pcPosts) {
                val pid = table.attr("id").removePrefix("pid")
                if (pid.isNullOrBlank()) continue
                val tds = table.select(".t_fsz .t_f, .t_msgfont, .message")
                for (td in tds) {
                    val titleEl = td.select("strong, b").firstOrNull() ?: continue
                    val title = titleEl.text().trim()
                    if (title.isBlank() || title.length > 100) continue
                    val chapterNum = MangaTitleCleaner.extractChapterNum(title)
                    val safeUrl = buildThreadUrl(tid, pid)
                    result.add(MangaChapterItem(tid, title, chapterNum, safeUrl, null, null, pid = pid))
                }
            }

            // 移动版：div[id^=post_] 内的 .message / .t_f
            if (result.isEmpty()) {
                val mobilePosts = doc.select("div[id^=post_]")
                for (post in mobilePosts) {
                    val postId = post.attr("id")
                    val pid = postId.removePrefix("post_")
                    if (pid.isNullOrBlank()) continue
                    val message = post.select(".message, .t_f, #postmessage_$pid").firstOrNull() ?: continue
                    val titleEl = message.select("strong, b").firstOrNull() ?: continue
                    val title = titleEl.text().trim()
                    if (title.isBlank() || title.length > 100) continue
                    val chapterNum = MangaTitleCleaner.extractChapterNum(title)
                    val safeUrl = buildThreadUrl(tid, pid)
                    result.add(MangaChapterItem(tid, title, chapterNum, safeUrl, null, null, pid = pid))
                }
            }

            return result
        }

        /**
         * 提取列表页的总页数 (强化兼容手机端)
         */
        fun extractTotalPages(html: String): Int {
            val doc = Jsoup.parse(html)
            val candidates = mutableListOf(1)

            val mobileOptions = doc.select("select#dumppage option")
            if (mobileOptions.isNotEmpty()) {
                candidates += mobileOptions.mapNotNull { option ->
                    option.attr("value").toIntOrNull()
                        ?: Regex("[?&]page=(\\d+)").find(option.attr("value"))
                            ?.groupValues?.get(1)?.toIntOrNull()
                }
            }

            doc.select(".pg label span, div.page, div.pg").forEach { element ->
                val pageText = "${element.attr("title")} ${element.text()}"
                Regex("(?:共|/)?\\s*(\\d+)\\s*页").findAll(pageText).forEach { match ->
                    match.groupValues[1].toIntOrNull()?.let(candidates::add)
                }
            }

            doc.select("a[href*='page=']").forEach { link ->
                link.text().trim().toIntOrNull()?.let(candidates::add)
                Regex("[?&]page=(\\d+)").find(link.attr("href"))
                    ?.groupValues?.get(1)?.toIntOrNull()?.let(candidates::add)
            }

            return candidates.maxOrNull() ?: 1
        }

        /**
         * 提取搜索结果页的searchid
         */
        fun extractSearchId(html: String): String? {
            val doc = Jsoup.parse(html)
            val searchLinks = doc.select(
                "a[href*='searchid='], form[action*='searchid='], input[value*='searchid=']"
            )
            for (element in searchLinks) {
                val source = listOf(
                    element.attr("href"),
                    element.attr("action"),
                    element.attr("value")
                ).joinToString(" ")
                Regex("[?&]searchid=(\\d+)").find(source)
                    ?.groupValues?.get(1)?.let { return it }
            }
            return Regex("[?&]searchid=(\\d+)").find(html)?.groupValues?.get(1)
        }

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        private fun parsePublishTime(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return 0L
            return try {
                dateFormat.parse(dateStr.trim())?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        /**
         * 解析Tag列表页(PC端)或搜索结果页(手机端)，转换为统一的ChapterItem列表
         */
        fun parseListHtml(html: String, groupIndex: Int = 0): List<MangaChapterItem> {
            val doc = Jsoup.parse(html)
            val result = mutableListOf<MangaChapterItem>()

            // 处理 PC 端 Tag 页面
            if (doc.select("body.pg_tag").isNotEmpty()) {
                val rows = doc.select(".bm_c table tr")
                for (row in rows) {
                    if (row.select("th h2").isNotEmpty()) continue

                    val titleElement = row.select("th a").firstOrNull() ?: continue
                    val url = titleElement.attr("href")
                    val title = titleElement.text()

                    val authorTd = row.select("td.by").getOrNull(1)
                    val authorElement = authorTd?.select("cite a")?.firstOrNull()
                    val authorName = authorElement?.text()
                    val authorUid = authorElement?.attr("href")?.let { extractUidFromUrl(it) }

                    val timeStr = authorTd?.select("em span")?.firstOrNull()?.text()
                        ?: authorTd?.select("em")?.firstOrNull()?.text()

                    val cleanTimeStr = timeStr?.replace(Regex("[^0-9-]"), "")
                    val publishTime = parsePublishTime(cleanTimeStr)

                    val tid = MangaTitleCleaner.extractTidFromUrl(url) ?: continue
                    val chapterNum = MangaTitleCleaner.extractChapterNum(title)

                    result.add(
                        MangaChapterItem(
                            tid,
                            title,
                            chapterNum,
                            url,
                            authorUid,
                            authorName,
                            groupIndex,
                            publishTime
                        )
                    )
                }
            }
            // 处理手机端 Search 搜索结果页面
            else if (doc.select(".threadlist li.list").isNotEmpty()) {
                val items = doc.select(".threadlist li.list")
                for (item in items) {
                    val titleLink = item.select(
                        "a[href*='mod=viewthread'][href*='tid='], a[href*='thread-']"
                    ).firstOrNull() ?: continue
                    val url = titleLink.attr("href")
                    val title = titleLink.select(".threadlist_tit").text()
                        .ifBlank { titleLink.text() }

                    val authorElement = item.select(".muser h3 a").firstOrNull()
                    val authorName = authorElement?.text()
                    val authorUid = authorElement?.attr("href")?.let { extractUidFromUrl(it) }

                    val tid = MangaTitleCleaner.extractTidFromUrl(url) ?: continue
                    val chapterNum = MangaTitleCleaner.extractChapterNum(title)

                    result.add(
                        MangaChapterItem(
                            tid,
                            title,
                            chapterNum,
                            url,
                            authorUid,
                            authorName,
                            groupIndex
                        )
                    )
                }
            }

            // 论坛可能把 mobile=2 的搜索请求重定向到 PC 搜索结果页。
            if (result.isEmpty()) {
                val seen = mutableSetOf<String>()
                val links = doc.select(
                    ".slst li h3 a[href*='tid='], " +
                            ".slst li h3 a[href*='thread-'], " +
                            "li.pbw h3 a[href*='tid='], " +
                            "li.pbw h3 a[href*='thread-'], " +
                            "a[href*='mod=viewthread'][href*='tid=']"
                )
                for (titleLink in links) {
                    val url = titleLink.attr("href")
                    val tid = MangaTitleCleaner.extractTidFromUrl(url) ?: continue
                    if (!seen.add(tid)) continue
                    val title = titleLink.text().trim()
                    if (title.isBlank()) continue
                    val container = titleLink.closest("li")
                    val authorElement = container?.select(
                        "a[href*='mod=space'][href*='uid='], a[href*='space-uid-']"
                    )?.firstOrNull()
                    result.add(
                        MangaChapterItem(
                            tid = tid,
                            rawTitle = title,
                            chapterNum = MangaTitleCleaner.extractChapterNum(title),
                            url = url,
                            authorUid = authorElement?.attr("href")?.let { extractUidFromUrl(it) },
                            authorName = authorElement?.text(),
                            groupIndex = groupIndex
                        )
                    )
                }
            }

            return result
        }

        /**
         * 异常嗅探：防止把防灌水页面当做空目录解析
         */
        fun isFloodControlOrError(html: String): Boolean {
            // 搜不到不是错误，返回空列表即可，不要抛异常
            if (html.contains("没有找到匹配结果")) return false

            return html.contains("只能进行一次搜索") ||
                    html.contains("防灌水") ||
                    html.contains("指定的搜索词长度")
        }
    }
}
