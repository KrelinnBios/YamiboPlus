package org.shirakawatyu.yamibo.novel.repository

import android.content.Context
import android.util.Log
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.DirectoryStrategy
import org.shirakawatyu.yamibo.novel.bean.MangaChapterItem
import org.shirakawatyu.yamibo.novel.bean.MangaDirectory
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.MangaApi
import org.shirakawatyu.yamibo.novel.parser.MangaHtmlParser
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

data class DirectoryUpdateResult(val directory: MangaDirectory, val searchPerformed: Boolean)

class DirectoryRepository private constructor(private val context: Context) {
    private val mangaApi = YamiboRetrofit.getInstance().create(MangaApi::class.java)
    private val DIRECTORY_DIR = "manga_directory"
    private val LOG_TAG = "DirectoryRepo"

    private val STRIPE_COUNT = 32
    private val locks = Array(STRIPE_COUNT) { Mutex() }
    private fun getFileLock(name: String) =
        locks[(name.hashCode() and Int.MAX_VALUE) % STRIPE_COUNT]

    private val memoryCache: MutableMap<String, MangaDirectory> = Collections.synchronizedMap(
        object : LinkedHashMap<String, MangaDirectory>(20, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MangaDirectory>?) =
                size > 50
        }
    )
    private val threadFidCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > 500
        }
    )

    private data class ThreadAuthor(
        val uid: String? = null,
        val name: String? = null
    )

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun MangaDirectory.hasPublisherSetting(): Boolean =
        publisherUid != null || publisherName != null

    private fun MangaChapterItem.withInheritedReleaseGroup(group: String): MangaChapterItem {
        if (group.isBlank() || MangaTitleCleaner.matchesTranslationGroup(rawTitle, group)) {
            return this
        }
        return copy(rawTitle = "【$group】$rawTitle")
    }

    private suspend fun resolveThreadAuthor(tid: String?): ThreadAuthor {
        if (tid.isNullOrBlank()) return ThreadAuthor()
        return runCatching {
            val root = JSON.parseObject(mangaApi.getThreadDetailApi(tid).string())
            val variables = root.getJSONObject("Variables")
            val thread = variables?.getJSONObject("thread")
            val firstPost = variables?.getJSONArray("postlist")?.getJSONObject(0)
            ThreadAuthor(
                uid = thread?.getString("authorid").nonBlank()
                    ?: firstPost?.getString("authorid").nonBlank(),
                name = thread?.getString("author").nonBlank()
                    ?: firstPost?.getString("author").nonBlank()
            )
        }.getOrDefault(ThreadAuthor())
    }

    companion object {
        private val MANGA_FIDS = setOf("30", "37")

        @Volatile
        private var instance: DirectoryRepository? = null
        fun getInstance(context: Context): DirectoryRepository = instance ?: synchronized(this) {
            instance ?: DirectoryRepository(context.applicationContext).also { instance = it }
        }

        /**
         * 章节唯一键：跨帖时用 tid，单帖多章时用 tid-pid
         */
        internal fun chapterUniqueKey(chapter: MangaChapterItem): String {
            return if (chapter.pid != null) "${chapter.tid}-pid-${chapter.pid}" else chapter.tid
        }

        /**
         * 目录章节过滤：
         * - 未设汉化组/发布者约束时全部保留。
         * - 设了约束时只保留匹配约束的章节；不同汉化组的同名作品不再按话数补缺混入。
         * - 发布者约束仍尊重 keepUnknownPublisher，用于个人/转载类标题的旧数据兼容。
         */
        internal fun filterChaptersByDirectoryConstraints(
            chapters: List<MangaChapterItem>,
            translationGroup: String?,
            publisherUid: String?,
            publisherName: String?,
            keepUnknownPublisher: Boolean
        ): List<MangaChapterItem> {
            // 顺带自愈已经写进目录的"上一话/下一话"等导航链接伪章节
            val valid = chapters.filterNot {
                MangaTitleCleaner.isUrlLikeChapterTitle(it.rawTitle) ||
                        MangaTitleCleaner.isNavigationLinkTitle(it.rawTitle)
            }
            val hasConstraint = !translationGroup.isNullOrBlank() ||
                    !publisherUid.isNullOrBlank() || !publisherName.isNullOrBlank()
            if (!hasConstraint) return valid

            fun isPreferred(chapter: MangaChapterItem): Boolean =
                MangaTitleCleaner.matchesDirectoryConstraints(
                    rawTitle = chapter.rawTitle,
                    authorUid = chapter.authorUid,
                    authorName = chapter.authorName,
                    translationGroup = translationGroup,
                    publisherUid = publisherUid,
                    publisherName = publisherName,
                    keepUnknownPublisher = keepUnknownPublisher
                )

            return valid.filter { isPreferred(it) }
        }

        internal fun hasAuthoritativeCrossWorkPageLinks(
            currentTid: String,
            cleanBookName: String,
            pageLinks: List<MangaChapterItem>
        ): Boolean {
            if (pageLinks.isEmpty()) return false
            val numberedLinkCount = pageLinks.count { chapter ->
                chapter.rawTitle.trim().matches(Regex("^\\d+\\s*[、.．].*"))
            }
            if (numberedLinkCount < 2) return false
            return pageLinks.any { chapter ->
                chapter.tid != currentTid &&
                        !MangaTitleCleaner.matchesDirectoryCandidate(
                            rawText = chapter.rawTitle,
                            cleanBookName = cleanBookName,
                            configuredKeywords = null
                        )
            }
        }

        internal fun mergeExplicitOrderedPageLinks(
            gatheredFromPage: List<MangaChapterItem>,
            cachedChapters: List<MangaChapterItem>
        ): List<MangaChapterItem> {
            val gatheredKeys = gatheredFromPage.map { chapterUniqueKey(it) }.toSet()
            val result = gatheredFromPage.toMutableList()
            val cachedExtras = cachedChapters
                .filter { chapterUniqueKey(it) !in gatheredKeys }
                .map { chapter ->
                    chapter.copy(
                        chapterNum = MangaTitleCleaner.extractChapterNum(chapter.rawTitle)
                    )
                }
                .sortedBy { chapter ->
                    chapter.chapterNum.takeIf { it > 0f && it < 999f } ?: Float.MAX_VALUE
                }

            cachedExtras.forEach { chapter ->
                val chapterNum = chapter.chapterNum
                val insertionIndex = if (chapterNum > 0f && chapterNum < 999f) {
                    result.indexOfFirst { existing ->
                        val existingNum = MangaTitleCleaner.extractChapterNum(existing.rawTitle)
                        existingNum > chapterNum && existingNum < 999f
                    }
                } else {
                    -1
                }
                if (insertionIndex >= 0) {
                    result.add(insertionIndex, chapter)
                } else {
                    result.add(chapter)
                }
            }
            return result
        }
    }

    private fun getDirectoryFile(cleanName: String): File {
        val safeName = cleanName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { if (it.length > 50) it.substring(0, 50) else it }
        return File(
            File(context.filesDir, DIRECTORY_DIR).apply { if (!exists()) mkdirs() },
            "${safeName}_${cleanName.hashCode().toString(16)}_dir.json"
        )
    }

    private suspend fun loadDirectory(cleanName: String): MangaDirectory? {
        memoryCache[cleanName]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val file = getDirectoryFile(cleanName)
                if (!file.exists()) return@withContext null
                JSON.parseObject(file.readText(), MangaDirectory::class.java)
                    .also { if (it != null) memoryCache[cleanName] = it }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun saveDirectory(directory: MangaDirectory) = withContext(Dispatchers.IO) {
        val name = directory.cleanBookName
        val file = getDirectoryFile(name)
        val tempFile = File(file.parent, "${file.name}.tmp")
        try {
            memoryCache[name] = directory
            tempFile.writeText(JSON.toJSONString(directory))
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
            Unit
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Save failed: $name", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * 把残次名目录迁移到正确的清洗名下：若目标名已有目录则合并章节，随后删除旧文件。
     * 返回迁移后的目录；迁移失败时返回 null（调用方继续沿用旧名，不影响功能）。
     * revalidateBookName：parody 残次目录（书名曾是原作标记）里通常混着同 parody 的
     * 不相干作品，迁移时按新书名重新校验章节，只带走属于本作品的部分；被甩掉的作品
     * 在用户下次打开对应帖子时会各自建新目录。
     */
    private suspend fun migrateStaleDirectoryName(
        oldDir: MangaDirectory,
        newCleanName: String,
        revalidateBookName: Boolean = false
    ): MangaDirectory? = withContext(Dispatchers.IO) {
        try {
            val carriedChapters = if (revalidateBookName) {
                oldDir.chapters.filter {
                    MangaTitleCleaner.matchesDirectoryCandidate(
                        rawText = "${it.rawTitle} ${it.authorName.orEmpty()}",
                        cleanBookName = newCleanName,
                        configuredKeywords = null
                    )
                }
            } else {
                oldDir.chapters
            }
            val target = loadDirectory(newCleanName)
            val migrated = if (target != null) {
                target.copy(chapters = mergeAndSortChapters(carriedChapters, target.chapters))
            } else {
                oldDir.copy(
                    cleanBookName = newCleanName,
                    chapters = mergeAndSortChapters(emptyList(), carriedChapters)
                )
            }
            saveDirectory(migrated)
            deleteDirectory(oldDir.cleanBookName)
            Log.d(LOG_TAG, "迁移残次目录名: '${oldDir.cleanBookName}' -> '$newCleanName' " +
                    "(章节 ${oldDir.chapters.size} -> ${migrated.chapters.size})")
            migrated
        } catch (e: Exception) {
            Log.w(LOG_TAG, "残次目录名迁移失败: ${oldDir.cleanBookName}", e)
            null
        }
    }

    // 基于 TID 排序并重组序号
    private fun mergeAndSortChapters(
        old: List<MangaChapterItem>,
        new: List<MangaChapterItem>
    ): List<MangaChapterItem> {
        val map = LinkedHashMap<String, MangaChapterItem>()

        old.forEach { map[chapterUniqueKey(it)] = it }
        new.forEach { map[chapterUniqueKey(it)] = it }

        val sortedByTid = map.values.sortedBy { it.tid.toLongOrNull() ?: 0L }

        val extractedChapters = sortedByTid.map { item ->
            val extractedNum = MangaTitleCleaner.extractChapterNum(item.rawTitle)
            item.copy(chapterNum = extractedNum)
        }

        return smoothChapterDisplayNumbers(extractedChapters)
    }

    /**
     * 话数纠错
     */
    private fun smoothChapterDisplayNumbers(items: List<MangaChapterItem>): List<MangaChapterItem> {
        if (items.size < 3) return items

        val processed = items.toMutableList()
        val isBad = BooleanArray(processed.size)

        for (i in processed.indices) {
            val curr = processed[i].chapterNum
            if (curr <= 0f || curr >= 999f) continue

            var leftNum = -1f
            for (j in i - 1 downTo 0) {
                if (!isBad[j] && processed[j].chapterNum > 0f && processed[j].chapterNum < 999f) {
                    leftNum = processed[j].chapterNum
                    break
                }
            }

            if (leftNum != -1f && curr < leftNum - 3f) {
                isBad[i] = true
                continue
            }

            if (leftNum != -1f && curr > leftNum + 3f) {
                var dropCount = 0
                for (j in i + 1 until minOf(processed.size, i + 8)) {
                    val futureNum = processed[j].chapterNum
                    if (futureNum > 0f && futureNum < curr - 5f) {
                        dropCount++
                        if (dropCount >= 3) {
                            isBad[i] = true
                            break
                        }
                    }
                }
                if (isBad[i]) continue
            }

            if (leftNum == -1f) {
                var dropCount = 0
                for (j in i + 1 until minOf(processed.size, i + 8)) {
                    if (processed[j].chapterNum > 0f && processed[j].chapterNum < curr - 3f) {
                        dropCount++
                        if (dropCount >= 3) {
                            isBad[i] = true
                            break
                        }
                    }
                }
            }
        }

        for (i in processed.indices) {
            if (isBad[i]) {
                val currItem = processed[i]

                var prevValidNum = 0f
                for (j in i - 1 downTo 0) if (!isBad[j] && processed[j].chapterNum > 0f && processed[j].chapterNum < 999f) {
                    prevValidNum = processed[j].chapterNum; break
                }

                var nextValidNum = 9999f
                for (j in i + 1 until processed.size) if (!isBad[j] && processed[j].chapterNum > 0f && processed[j].chapterNum < 999f) {
                    nextValidNum = processed[j].chapterNum; break
                }

                val candidates = MangaTitleCleaner.extractAllPossibleNumbers(currItem.rawTitle)
                var bestFit = -1f

                val beautyComparator = compareBy<Float> { num ->
                    val str = java.text.DecimalFormat("0.###").format(num)
                    if (str.contains(".")) str.substringAfter(".").length else 0
                }.thenBy { it }

                val validCandidates = candidates.filter { it >= prevValidNum && it <= nextValidNum }

                if (validCandidates.isNotEmpty()) {
                    val strictGreater = validCandidates.filter { it > prevValidNum }
                    bestFit = if (strictGreater.isNotEmpty()) {
                        strictGreater.minWithOrNull(beautyComparator) ?: -1f
                    } else {
                        validCandidates.minWithOrNull(beautyComparator) ?: -1f
                    }
                }

                if (bestFit == -1f) {
                    var smartFill = -1f

                    if (prevValidNum > 0f && nextValidNum < 9999f) {
                        val expectedInt = kotlin.math.floor(prevValidNum.toDouble()).toFloat() + 1f

                        if (expectedInt > prevValidNum && expectedInt < nextValidNum && (nextValidNum - prevValidNum) <= 3.5f) {
                            val existsGlobally = processed.any { it.chapterNum == expectedInt }
                            val someoneElseClaimsIt = items.any {
                                it.tid != currItem.tid &&
                                        MangaTitleCleaner.extractAllPossibleNumbers(it.rawTitle)
                                            .contains(expectedInt)
                            }

                            if (!existsGlobally && !someoneElseClaimsIt) {
                                smartFill = expectedInt
                            }
                        }
                    }

                    if (smartFill != -1f) {
                        bestFit = smartFill
                    } else if (candidates.isNotEmpty()) {
                        bestFit = candidates.minWithOrNull(beautyComparator) ?: candidates.first()
                    }
                }

                // 最终回填
                if (bestFit != -1f) {
                    val formattedNum = Math.round(bestFit * 1000) / 1000f
                    processed[i] = currItem.copy(chapterNum = formattedNum)
                    isBad[i] = false
                } else {
                    val safeBase =
                        if (i > 0 && processed[i - 1].chapterNum > 0f && processed[i - 1].chapterNum < 999f) {
                            processed[i - 1].chapterNum
                        } else {
                            prevValidNum
                        }
                    val fallbackNum = Math.round((safeBase + 0.001f) * 1000) / 1000f
                    processed[i] = currItem.copy(chapterNum = fallbackNum)
                    isBad[i] = false
                }
            }
        }

        return processed
    }

    suspend fun initDirectoryForThread(
        tid: String,
        currentUrl: String,
        rawTitle: String,
        mobileHtml: String
    ): MangaDirectory {
        val threadTitle = MangaTitleCleaner.getCleanThreadTitle(rawTitle)
        val detectedGroup = MangaTitleCleaner.extractReleaseGroup(threadTitle)
        val allDirs = getAllDirectories()
        val existingDir = allDirs.find { dir -> dir.chapters.any { it.tid == tid } }
        val detectedSourceFid = existingDir?.sourceFid ?: resolveSourceFid(tid)

        // 自愈旧版清洗器留下的残次目录名：切多了（如"作品名 52+"）、切少了
        // （如短篇集类作品旧版没有集合后缀识别，把"作品名短篇集"截成"作品名"）、
        // 括号残渣，以及把开头原作标记当书名的 parody 残次目录，都重命名到当前清洗结果
        val freshCleanName = MangaTitleCleaner.getCleanBookName(rawTitle)
        val parodyResidue = existingDir != null &&
                MangaTitleCleaner.isParodyResidueCleanBookName(
                    existingDir.cleanBookName, rawTitle, freshCleanName
                )
        val migratedDir = if (existingDir != null &&
            (MangaTitleCleaner.isStaleCleanBookName(existingDir.cleanBookName, freshCleanName) ||
                    MangaTitleCleaner.isTruncatedCleanBookName(existingDir.cleanBookName, freshCleanName) ||
                    MangaTitleCleaner.isParenResidueCleanBookName(existingDir.cleanBookName, freshCleanName) ||
                    parodyResidue)
        ) {
            migrateStaleDirectoryName(existingDir, freshCleanName, revalidateBookName = parodyResidue)
        } else {
            null
        }

        val cleanName = migratedDir?.cleanBookName ?: existingDir?.cleanBookName ?: freshCleanName

        return getFileLock(cleanName).withLock {
            val cachedDir = loadDirectory(cleanName)

            // 直接提取当前页超链接
            val detectedAuthor = resolveThreadAuthor(tid)
            val rawSamePageLinks = MangaHtmlParser.extractSamePageLinks(mobileHtml).map { chapter ->
                if (chapter.authorUid.isNullOrBlank() && chapter.authorName.isNullOrBlank()) {
                    chapter.copy(authorUid = detectedAuthor.uid, authorName = detectedAuthor.name)
                } else {
                    chapter
                }
            }

            // 提取 #threadindex 页内目录（单帖多章）
            val threadindexLinks = MangaHtmlParser.extractThreadindexLinks(mobileHtml).map { chapter ->
                if (chapter.authorUid.isNullOrBlank() && chapter.authorName.isNullOrBlank()) {
                    chapter.copy(authorUid = detectedAuthor.uid, authorName = detectedAuthor.name)
                } else {
                    chapter
                }
            }

            // ===== 尝试 PC 版 HTML #threadindex 目录（最可靠，但 OkHttp 拦截器会注入 mobile=2
            // cookie，故用 HttpURLConnection 直接请求并清除 mobile cookie）=====
            // 使用 forum.php?authorid=XXX（只看楼主）格式，与用户已验证的保存页面一致。
            // #threadindex 只在第 1 页楼主首帖中出现，一次请求即可获取全部章节。
            var pcHtmlThreadindexLinks = emptyList<MangaChapterItem>()
            if (threadindexLinks.isEmpty() && detectedAuthor.uid != null) {
                try {
                    val pcUrlStr = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$tid&page=1&authorid=${detectedAuthor.uid}"
                    val pcUrl = URL(pcUrlStr)
                    val conn = pcUrl.openConnection() as HttpURLConnection
                    conn.apply {
                        setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        setRequestProperty("Cookie",
                            GlobalData.currentCookie.replace(Regex("mobile=[^;]+;?"), "").trimEnd(';').trim())
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                    if (conn.responseCode == 200) {
                        val pcHtml = conn.inputStream.bufferedReader().readText()
                        pcHtmlThreadindexLinks = MangaHtmlParser.extractThreadindexLinks(pcHtml).map { ch ->
                            if (ch.authorUid.isNullOrBlank() && ch.authorName.isNullOrBlank()) {
                                ch.copy(authorUid = detectedAuthor.uid, authorName = detectedAuthor.name)
                            } else ch
                        }
                    }
                    conn.disconnect()
                    Log.d(LOG_TAG, "PC HTML threadindex: ${pcHtmlThreadindexLinks.size} links")
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "PC HTML fetch failed", e)
                }
            }

            val hasAuthoritativeCrossWorkLinks = hasAuthoritativeCrossWorkPageLinks(
                currentTid = tid,
                cleanBookName = cleanName,
                pageLinks = rawSamePageLinks
            )
            val hasExplicitOrderedPageLinks =
                rawSamePageLinks.size >= 3 &&
                        MangaHtmlParser.hasExplicitMangaDirectoryMarker(mobileHtml)
            val hasAuthoritativePageLinks =
                hasAuthoritativeCrossWorkLinks || hasExplicitOrderedPageLinks

            // ===== 补充：PC HTML 也未获目录时，走 API 逐页扫描楼主发帖 =====
            val supplementaryLinks = mutableListOf<MangaChapterItem>()
            var firstFloorPid: String? = null
            val needApiFallback = !hasAuthoritativePageLinks &&
                    pcHtmlThreadindexLinks.isEmpty() && threadindexLinks.isEmpty() && (
                    rawSamePageLinks.any { it.tid == tid } ||
                            (rawSamePageLinks.isNotEmpty() && rawSamePageLinks.size < 5)
                    )
            if (needApiFallback && detectedAuthor.uid != null) {
                Log.d(LOG_TAG, "API fallback: scanning tid=$tid authorUid=${detectedAuthor.uid}")
                try {
                    val firstPage = JSON.parseObject(mangaApi.getThreadDetailApi(tid, 1).string())
                    val variables = firstPage.getJSONObject("Variables")
                    val totalPages = (variables?.getJSONObject("thread")?.getString("maxposition")
                        ?.toIntOrNull()?.let { (it + 19) / 20 }
                        ?: variables?.getString("totalpage")?.toIntOrNull()
                        ?: 99).coerceAtLeast(1)
                    val seenAuthorPids = mutableSetOf<String>()
                    var emptyPageCount = 0
                    for (page in 1..totalPages) {
                        val root = JSON.parseObject(mangaApi.getThreadDetailApi(tid, page).string())
                        val postlist = root.getJSONObject("Variables")?.getJSONArray("postlist") ?: continue
                        var pageFound = 0
                        for (i in 0 until postlist.size) {
                            val post = postlist.getJSONObject(i)
                            val authorId = post.getString("authorid")
                            if (authorId != detectedAuthor.uid) continue
                            val pid = post.getString("pid") ?: continue
                            if (pid in seenAuthorPids) continue
                            seenAuthorPids.add(pid)
                            pageFound++
                            // 楼主首帖就是本帖章节本身（currentChapter 已覆盖），里面的加粗
                            // 文字多是制作名单/转载警告等正文内容，不能当章节标题收进目录
                            val position = post.getString("position")?.toIntOrNull()
                            if (position == 1 || (position == null && page == 1 && i == 0)) {
                                firstFloorPid = pid
                                continue
                            }
                            val msg = post.getString("message") ?: continue
                            val title = org.jsoup.Jsoup.parse(msg).select("strong, b")
                                .firstOrNull()?.text()?.trim()
                            if (title.isNullOrBlank() || title.length > 100) continue
                            val chapterNum = MangaTitleCleaner.extractChapterNum(title)
                            val safeUrl = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$tid&mobile=2&pid=$pid"
                            supplementaryLinks.add(
                                MangaChapterItem(tid, title, chapterNum, safeUrl,
                                    authorUid = detectedAuthor.uid, authorName = detectedAuthor.name,
                                    pid = pid)
                            )
                        }
                        if (page == 1 && pageFound == 0) break
                        if (page > 1 && pageFound == 0) {
                            emptyPageCount++
                            if (emptyPageCount >= 3) break
                        } else {
                            emptyPageCount = 0
                        }
                        Log.d(LOG_TAG, "API fallback: page $page author chaps=${supplementaryLinks.size}")
                    }
                    Log.d(LOG_TAG, "API fallback: total=${supplementaryLinks.size}")
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "API fallback failed", e)
                }
            }

            val currentChapter = MangaChapterItem(
                tid = tid,
                rawTitle = threadTitle,
                chapterNum = MangaTitleCleaner.extractChapterNum(threadTitle),
                url = currentUrl,
                authorUid = detectedAuthor.uid,
                authorName = detectedAuthor.name
            )

            val allLinks = if (hasAuthoritativePageLinks) {
                rawSamePageLinks
            } else {
                rawSamePageLinks.map { it.withInheritedReleaseGroup(detectedGroup) } +
                        threadindexLinks.map { it.withInheritedReleaseGroup(detectedGroup) } +
                        pcHtmlThreadindexLinks.map { it.withInheritedReleaseGroup(detectedGroup) } +
                        supplementaryLinks.map { it.withInheritedReleaseGroup(detectedGroup) }
            }
            Log.d(LOG_TAG, "rawSamePageLinks=${rawSamePageLinks.size} threadindex=${threadindexLinks.size} pcHtmlThreadindex=${pcHtmlThreadindexLinks.size} apiSupplementary=${supplementaryLinks.size}")
            rawSamePageLinks.forEachIndexed { i, ch -> Log.d(LOG_TAG, "  raw[$i]: pid=${ch.pid} title=${ch.rawTitle}") }
            supplementaryLinks.forEachIndexed { i, ch -> Log.d(LOG_TAG, "  supp[$i]: pid=${ch.pid} title=${ch.rawTitle}") }
            Log.d(
                LOG_TAG,
                "authoritativePageLinks=$hasAuthoritativePageLinks " +
                        "crossWork=$hasAuthoritativeCrossWorkLinks " +
                        "explicitOrdered=$hasExplicitOrderedPageLinks"
            )

            val gatheredFromPage = if (hasAuthoritativePageLinks) {
                (allLinks + currentChapter).distinctBy { chapterUniqueKey(it) }
            } else {
                (listOf(currentChapter) + allLinks).distinctBy { chapterUniqueKey(it) }
            }
            Log.d(LOG_TAG, "gatheredFromPage=${gatheredFromPage.size}")

            if (cachedDir != null) {
                // 目录一旦确认为"首楼权威链接目录"就保持该属性：即使某次打开时页面
                // 没解析出链接（HTML 异常等），也不能退回按组过滤/搜索合并的普通逻辑
                val dirIsAuthoritative = hasAuthoritativePageLinks || cachedDir.authoritativeLinks
                val hasPageLinks = allLinks.isNotEmpty()
                val newStrategy =
                    if (dirIsAuthoritative ||
                        (cachedDir.strategy == DirectoryStrategy.PENDING_SEARCH && hasPageLinks)
                    ) {
                        DirectoryStrategy.LINKS
                    } else cachedDir.strategy

                val existingChapterKeys = cachedDir.chapters.map { chapterUniqueKey(it) }.toSet()
                Log.d(LOG_TAG, "cached chapters=${cachedDir.chapters.size} existingKeys=${existingChapterKeys.size}")

                val preferCurrentThreadMetadata = tid !in cachedDir.chapters.map { it.tid }.toSet()

                // 同名不同版本（不同汉化组/发布者）的作品会落到同一个 cleanBookName 目录。
                // 既然目录按组硬过滤，旧目录组与当前帖不一致时应优先切到当前组；
                // 只有当前帖识别不出组名时，才沿用旧组，避免把目录约束清空。
                // 若首楼提供跨作品编号链接列表，则这些链接就是权威目录，去掉当前汉化组约束。
                val cachedGroupMatchesThread = MangaTitleCleaner.matchesTranslationGroup(
                    threadTitle, cachedDir.translationGroup.orEmpty()
                )
                val detectedOrSavedGroup = when {
                    dirIsAuthoritative -> null
                    cachedDir.translationGroup.isNullOrBlank() -> detectedGroup.takeIf { it.isNotBlank() }
                    cachedGroupMatchesThread -> cachedDir.translationGroup
                    detectedGroup.isNotBlank() -> detectedGroup
                    else -> cachedDir.translationGroup
                }
                // 默认只按作品名和汉化组归目录，不自动记发布者（同一汉化组多人分工投稿时，
                // 按发布者会把本该同目录的章节拆开）。只有标题标注为个人/非固定团队发布
                // （个人汉化/个人翻译/合作汉化/自翻/代发）时，才用发布者当兜底区分依据；
                // 已有的发布者设置（无论是这样自动写入的还是手动填的）不会被清空。
                val canAutoDetectPublisher = MangaTitleCleaner.isIndividualRelease(threadTitle)
                val detectedOrSavedPublisherUid = if (canAutoDetectPublisher &&
                    (preferCurrentThreadMetadata || !cachedDir.hasPublisherSetting())
                ) {
                    detectedAuthor.uid ?: cachedDir.publisherUid
                } else {
                    cachedDir.publisherUid
                }
                val detectedOrSavedPublisherName = if (canAutoDetectPublisher &&
                    (preferCurrentThreadMetadata || !cachedDir.hasPublisherSetting())
                ) {
                    detectedAuthor.name ?: cachedDir.publisherName
                } else {
                    cachedDir.publisherName
                }
                Log.d(LOG_TAG, "filter group='$detectedOrSavedGroup'")
                // 自愈旧版兜底写进目录的"楼主首帖伪章节"（正文加粗文字被当成了章节标题）
                val cachedChapters = if (firstFloorPid != null) {
                    cachedDir.chapters.filterNot { it.tid == tid && it.pid == firstFloorPid }
                } else {
                    cachedDir.chapters
                }
                val mergedAll = when {
                    // 跨作品编号列表整体以首楼为准，不混入未列出的旧条目。
                    hasAuthoritativeCrossWorkLinks -> gatheredFromPage
                    // 同一作品的“本作目录”采用正文顺序；正文漏列但旧目录已识别出的普通
                    // 话数按章节号插回相邻位置，较新的章节追加到末尾，避免倒退丢章。
                    hasExplicitOrderedPageLinks -> mergeExplicitOrderedPageLinks(
                        gatheredFromPage = gatheredFromPage,
                        cachedChapters = cachedChapters
                    )
                    // 目录是权威列表但本次页面没解析出链接：保持已存列表原样（含顺序），
                    // 只补充列表里没有的新条目，绝不能让 currentChapter 的原始帖子标题
                    // 覆盖列表里带编号的条目（如"2、去你家/君の家まで"）
                    cachedDir.authoritativeLinks -> {
                        val existingKeys = cachedChapters.map { chapterUniqueKey(it) }.toSet()
                        cachedChapters + gatheredFromPage.filter { chapterUniqueKey(it) !in existingKeys }
                    }
                    else -> mergeAndSortChapters(cachedChapters, gatheredFromPage)
                }
                Log.d(LOG_TAG, "mergedAll=${mergedAll.size} before filter")
                val mergedChapters = filterChaptersByDirectoryConstraints(
                    chapters = mergedAll,
                    translationGroup = detectedOrSavedGroup,
                    publisherUid = detectedOrSavedPublisherUid,
                    publisherName = detectedOrSavedPublisherName,
                    keepUnknownPublisher = true
                )

                val updatedDir = cachedDir.copy(
                    chapters = mergedChapters,
                    strategy = newStrategy,
                    sourceFid = cachedDir.sourceFid ?: detectedSourceFid,
                    translationGroup = detectedOrSavedGroup,
                    publisherUid = detectedOrSavedPublisherUid,
                    publisherName = detectedOrSavedPublisherName,
                    authoritativeLinks = dirIsAuthoritative,
                    lastUpdateTime = if (hasAuthoritativePageLinks) {
                        System.currentTimeMillis()
                    } else {
                        cachedDir.lastUpdateTime
                    }
                )

                if (mergedChapters != cachedDir.chapters ||
                    updatedDir.strategy != cachedDir.strategy ||
                    updatedDir.sourceFid != cachedDir.sourceFid ||
                    updatedDir.translationGroup != cachedDir.translationGroup ||
                    updatedDir.publisherUid != cachedDir.publisherUid ||
                    updatedDir.publisherName != cachedDir.publisherName ||
                    updatedDir.authoritativeLinks != cachedDir.authoritativeLinks ||
                    updatedDir.lastUpdateTime != cachedDir.lastUpdateTime
                ) {
                    saveDirectory(updatedDir)
                }
                return@withLock updatedDir
            } else {
                val tagIds = MangaHtmlParser.findTagIdsMobile(mobileHtml)

                val strategy: DirectoryStrategy
                val sourceKey: String

                if (hasAuthoritativePageLinks) {
                    strategy = DirectoryStrategy.LINKS
                    sourceKey = cleanName
                } else if (tagIds.isNotEmpty()) {
                    strategy = DirectoryStrategy.TAG
                    sourceKey = tagIds.joinToString(",")
                } else if (allLinks.isNotEmpty()) {
                    strategy = DirectoryStrategy.LINKS
                    sourceKey = cleanName
                } else {
                    strategy = DirectoryStrategy.PENDING_SEARCH
                    sourceKey = cleanName
                }

                // 调用核心 TID 排序初始化
                val canAutoDetectPublisher = MangaTitleCleaner.isIndividualRelease(threadTitle)
                val initialAll = if (hasAuthoritativePageLinks) {
                    gatheredFromPage
                } else {
                    mergeAndSortChapters(emptyList(), gatheredFromPage)
                }
                val initialChapters = filterChaptersByDirectoryConstraints(
                    chapters = initialAll,
                    translationGroup = if (hasAuthoritativePageLinks) {
                        null
                    } else {
                        detectedGroup.takeIf { it.isNotBlank() }
                    },
                    publisherUid = if (canAutoDetectPublisher) detectedAuthor.uid else null,
                    publisherName = if (canAutoDetectPublisher) detectedAuthor.name else null,
                    keepUnknownPublisher = true
                )
                val newDir = MangaDirectory(
                    cleanBookName = cleanName,
                    strategy = strategy,
                    sourceKey = sourceKey,
                    chapters = initialChapters,
                    sourceFid = detectedSourceFid,
                    translationGroup = if (hasAuthoritativePageLinks) {
                        null
                    } else {
                        detectedGroup.takeIf { it.isNotBlank() }
                    },
                    publisherUid = if (canAutoDetectPublisher) detectedAuthor.uid else null,
                    publisherName = if (canAutoDetectPublisher) detectedAuthor.name else null,
                    authoritativeLinks = hasAuthoritativePageLinks,
                    lastUpdateTime = if (hasAuthoritativePageLinks) {
                        System.currentTimeMillis()
                    } else {
                        0L
                    }
                )

                saveDirectory(newDir)
                return@withLock newDir
            }
        }
    }

    suspend fun manuallyUpdateDirectory(
        currentDir: MangaDirectory,
        forceSearch: Boolean = false,
        currentTid: String? = null
    ): Result<DirectoryUpdateResult> = withContext(Dispatchers.IO) {
        val newChapters = mutableListOf<MangaChapterItem>()
        var searchPerformed = false
        try {
            // 首楼编号链接列表是权威目录：搜索更新会用原始帖子标题覆盖列表条目、
            // 按 tid 打乱列表顺序、甚至把未列出的帖子混进来，这里整体跳过。
            // 列表内容在每次打开原帖时按首楼自动刷新。
            val latestForAuthoritativeCheck = loadDirectory(currentDir.cleanBookName) ?: currentDir
            if (latestForAuthoritativeCheck.authoritativeLinks) {
                val refreshed = getFileLock(currentDir.cleanBookName).withLock {
                    val latest = loadDirectory(currentDir.cleanBookName) ?: currentDir
                    latest.copy(lastUpdateTime = System.currentTimeMillis()).also { saveDirectory(it) }
                }
                // searchPerformed 报 true：强制搜索对权威目录同样跳过，
                // 报 false 会让面板弹出"强制搜索"快捷入口，点了也没有意义
                return@withContext Result.success(DirectoryUpdateResult(refreshed, true))
            }

            val targetChapter = currentDir.chapters.find { it.tid == currentTid }
                ?: currentDir.chapters.lastOrNull()

            val firstRawTitle = targetChapter?.rawTitle ?: currentDir.cleanBookName
            val exactKeyword = currentDir.searchKeyword
            val sourceFid = (
                    resolveSourceFid(currentTid ?: targetChapter?.tid)
                        ?: currentDir.sourceFid
                    )?.takeIf { it in MANGA_FIDS }
                ?: return@withContext Result.failure(
                    Exception("无法确认当前漫画所属版区，请重新打开原帖后再更新")
                )

            if (!forceSearch && currentDir.strategy == DirectoryStrategy.TAG) {
                val tagIdList = currentDir.sourceKey.split(",")
                val desktopCookie = YamiboSession.desktopCookie(
                    YamiboSession.cookieFor(RequestConfig.BASE_URL)
                )
                var tagFetchFailed = false
                for ((index, tagId) in tagIdList.withIndex()) {
                    if (tagId.isBlank()) continue
                    try {
                        val html1 = mangaApi.getTagPageHtml(
                            tagId = tagId,
                            page = 1,
                            cookie = desktopCookie
                        ).string()
                        val parsed = MangaHtmlParser.parseListHtml(html1, index)
                        if (parsed.isNotEmpty()) {
                            newChapters.addAll(parsed)
                            val total = MangaHtmlParser.extractTotalPages(html1)
                            if (total > 1) {
                                for (p in 2..total) {
                                    newChapters.addAll(
                                        MangaHtmlParser.parseListHtml(
                                            mangaApi.getTagPageHtml(
                                                tagId = tagId,
                                                page = p,
                                                cookie = desktopCookie
                                            ).string(),
                                            index
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        tagFetchFailed = true
                        Log.w(LOG_TAG, "Tag directory request failed for tagId=$tagId", e)
                    }
                }

                if (newChapters.isEmpty() || tagFetchFailed) {
                    val res = try {
                        performSearch(
                            rawTitle = firstRawTitle,
                            cleanBookName = currentDir.cleanBookName,
                            configuredKeywords = exactKeyword,
                            sourceFid = sourceFid
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Result.failure(e)
                    }
                    if (res.isSuccess) {
                        searchPerformed = true
                        newChapters.addAll(res.getOrNull().orEmpty())
                    } else if (newChapters.isEmpty()) {
                        return@withContext Result.failure(res.exceptionOrNull()!!)
                    }
                }
            } else {
                searchPerformed = true
                val res = performSearch(
                    rawTitle = firstRawTitle,
                    cleanBookName = currentDir.cleanBookName,
                    configuredKeywords = exactKeyword,
                    sourceFid = sourceFid
                )
                if (res.isFailure) return@withContext Result.failure(res.exceptionOrNull()!!)
                newChapters.addAll(res.getOrNull()!!)
            }

            // 搜索接口已经通过 srchfid[] 限定版区，不再逐帖请求 API 二次确认。
            // 旧逻辑会在帖子详情请求临时失败时丢掉对应章节，导致目录随机缺话。
            val verifiedNewChapters = if (searchPerformed) {
                newChapters.distinctBy { it.tid }
            } else {
                filterChaptersBySourceFid(
                    chapters = newChapters,
                    sourceFid = sourceFid,
                    keepUnresolved = true
                )
            }
            val latestSnapshot = loadDirectory(currentDir.cleanBookName) ?: currentDir
            val targetGroup = latestSnapshot.translationGroup.orEmpty()
            val latestPublisherUid = latestSnapshot.publisherUid
            val latestPublisherName = latestSnapshot.publisherName
            val existingCandidates = filterChaptersByDirectoryConstraints(
                chapters = latestSnapshot.chapters,
                translationGroup = targetGroup,
                publisherUid = latestPublisherUid,
                publisherName = latestPublisherName,
                keepUnknownPublisher = true
            )
            val verifiedExistingChapters =
                filterChaptersBySourceFid(
                    chapters = existingCandidates,
                    sourceFid = sourceFid,
                    keepUnresolved = true
                )

            val finalDir = getFileLock(currentDir.cleanBookName).withLock {
                val latest = loadDirectory(currentDir.cleanBookName) ?: currentDir
                val filteredNewChapters = filterChaptersByDirectoryConstraints(
                    chapters = verifiedNewChapters,
                    translationGroup = targetGroup,
                    publisherUid = latestPublisherUid,
                    publisherName = latestPublisherName,
                    keepUnknownPublisher = false
                )
                // 合并后再整体过一遍约束：两侧分别预过滤后，跨侧的同话数重复
                // （如既有里是本组版本、新搜到其它组的同一话）要在这里收敛
                val merged = filterChaptersByDirectoryConstraints(
                    chapters = mergeAndSortChapters(verifiedExistingChapters, filteredNewChapters),
                    translationGroup = targetGroup,
                    publisherUid = latestPublisherUid,
                    publisherName = latestPublisherName,
                    keepUnknownPublisher = true
                )
                val updated = latest.copy(
                    chapters = merged,
                    lastUpdateTime = System.currentTimeMillis(),
                    sourceFid = sourceFid,
                    strategy = if (latest.strategy != DirectoryStrategy.TAG) DirectoryStrategy.SEARCHED else latest.strategy,
                    publisherUid = latestPublisherUid,
                    publisherName = latestPublisherName
                )
                saveDirectory(updated)
                updated
            }
            Result.success(DirectoryUpdateResult(finalDir, searchPerformed))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun performSearch(
        rawTitle: String,
        cleanBookName: String,
        configuredKeywords: String? = null,
        sourceFid: String?
    ): Result<List<MangaChapterItem>> {
        val safeSourceFid = sourceFid
            ?.takeIf { it in MANGA_FIDS }
            ?: return Result.failure(Exception("无法确认当前漫画所属版区，请重新打开原帖后再更新"))
        val forumKeyword = MangaTitleCleaner.getDirectoryForumSearchKeyword(
            cleanBookName = cleanBookName,
            rawTitle = rawTitle,
            configuredKeywords = configuredKeywords
        )

        val profileJson = JSON.parseObject(mangaApi.getForumDisplay(safeSourceFid, 1).string())
        val formHash = profileJson.getJSONObject("Variables")?.getString("formhash")
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure(Exception("无法获取搜索校验信息"))
        val desktopCookie = YamiboSession.desktopCookie(
            YamiboSession.cookieFor(RequestConfig.BASE_URL)
        )
        val firstPageHtml = mangaApi.searchForum(
            formHash = formHash,
            fids = listOf(safeSourceFid),
            keyword = forumKeyword,
            cookie = desktopCookie
        ).string()
        if (MangaHtmlParser.isFloodControlOrError(firstPageHtml)) return Result.failure(Exception("触发论坛防灌水限制，请稍后再试"))

        val allItems = mutableListOf<MangaChapterItem>()
        allItems.addAll(MangaHtmlParser.parseListHtml(firstPageHtml))

        val totalPages = MangaHtmlParser.extractTotalPages(firstPageHtml)
        val searchId = MangaHtmlParser.extractSearchId(firstPageHtml)

        if (searchId != null && totalPages > 1) {
            for (p in 2..totalPages) {
                val pageHtml = mangaApi.searchForumPage(
                    searchid = searchId,
                    page = p,
                    cookie = desktopCookie
                ).string()
                allItems.addAll(MangaHtmlParser.parseListHtml(pageHtml))
            }
        }

        val matchedItems = allItems
            .asSequence()
            .filterNot { MangaTitleCleaner.isAdministrativeThread(it.rawTitle) }
            .filter {
                MangaTitleCleaner.matchesDirectoryCandidate(
                    rawText = "${it.rawTitle} ${it.authorName.orEmpty()}",
                    cleanBookName = cleanBookName,
                    configuredKeywords = configuredKeywords
                )
            }
            // 汉化组/发布者过滤统一交给调用方的 filterChaptersByDirectoryConstraints
            // （同时看汉化组和发布者，任一命中即收），这里不再单独按发布者预过滤，
            // 否则同一汉化组内不同账号投稿的章节会在这里就被提前挡掉。
            .distinctBy { it.tid }
            .toList()

        return Result.success(matchedItems)
    }

    private suspend fun resolveThreadFid(tid: String?): String? {
        if (tid.isNullOrBlank()) return null
        threadFidCache[tid]?.let { return it }
        val fid = runCatching {
            val root = JSON.parseObject(mangaApi.getThreadDetailApi(tid).string())
            root.getJSONObject("Variables")
                ?.getJSONObject("thread")
                ?.getString("fid")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        if (fid != null) threadFidCache[tid] = fid
        return fid
    }

    private suspend fun resolveSourceFid(tid: String?): String? =
        resolveThreadFid(tid)?.takeIf { it in MANGA_FIDS }

    private suspend fun filterChaptersBySourceFid(
        chapters: List<MangaChapterItem>,
        sourceFid: String,
        keepUnresolved: Boolean = false
    ): List<MangaChapterItem> = coroutineScope {
        val limiter = Semaphore(4)
        chapters
            .distinctBy { it.tid }
            .map { chapter ->
                async {
                    val chapterFid = limiter.withPermit {
                        resolveThreadFid(chapter.tid)
                    }
                    chapter.takeIf {
                        chapterFid == sourceFid || (keepUnresolved && chapterFid == null)
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    suspend fun getDirectoryByCleanName(cleanName: String): MangaDirectory? = loadDirectory(cleanName)

    suspend fun getAllDirectories(): List<MangaDirectory> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIRECTORY_DIR)
        if (!dir.exists()) return@withContext emptyList()

        val files = dir.listFiles { _, name -> name.endsWith("_dir.json") }
            ?: return@withContext emptyList()
        files.mapNotNull { file ->
            try {
                JSON.parseObject(file.readText(), MangaDirectory::class.java).also {
                    if (it != null) memoryCache[it.cleanBookName] = it
                }
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.lastUpdateTime }
    }

    suspend fun deleteDirectory(cleanName: String): Boolean = withContext(Dispatchers.IO) {
        getFileLock(cleanName).withLock {
            memoryCache.remove(cleanName)
            val file = getDirectoryFile(cleanName)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        }
    }

    suspend fun clearAllDirectories(): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIRECTORY_DIR)
        if (!dir.exists()) return@withContext true

        memoryCache.clear()
        val files =
            dir.listFiles { _, name -> name.endsWith("_dir.json") } ?: return@withContext true
        var allDeleted = true
        for (file in files) {
            if (!file.delete()) allDeleted = false
        }
        allDeleted
    }

    suspend fun renameAndMergeDirectory(
        currentDir: MangaDirectory,
        newCleanName: String,
        newTranslationGroup: String,
        newPublisher: String,
        currentTid: String
    ): MangaDirectory = withContext(Dispatchers.IO) {
        val oldName = currentDir.cleanBookName
        val normalizedTranslationGroup = newTranslationGroup.trim().takeIf { it.isNotBlank() }
        val normalizedPublisher = newPublisher.trim()
        val publisherIsUid = normalizedPublisher.matches(Regex("\\d+"))
        val newPublisherUid = when {
            normalizedPublisher.isBlank() -> null
            publisherIsUid -> normalizedPublisher
            normalizedPublisher == currentDir.publisherName -> currentDir.publisherUid
            else -> null
        }
        val newPublisherName = when {
            normalizedPublisher.isBlank() -> null
            publisherIsUid -> currentDir.chapters.firstOrNull { it.authorUid == normalizedPublisher }?.authorName
                ?: currentDir.publisherName?.takeIf { currentDir.publisherUid == normalizedPublisher }
            else -> normalizedPublisher
        }
        val resolvedSourceFid = resolveSourceFid(currentTid)
            ?: currentDir.sourceFid
            ?: resolveSourceFid(currentDir.chapters.lastOrNull()?.tid)
        val sourceChanged = currentDir.sourceFid != null &&
                resolvedSourceFid != null &&
                currentDir.sourceFid != resolvedSourceFid
        val currentSectionChapters = if (sourceChanged) {
            currentDir.chapters.filter { it.tid == currentTid }
        } else {
            currentDir.chapters
        }

        if (oldName == newCleanName &&
            currentDir.translationGroup == normalizedTranslationGroup &&
            currentDir.publisherUid == newPublisherUid &&
            currentDir.publisherName == newPublisherName &&
            currentDir.sourceFid == resolvedSourceFid
        ) return@withContext currentDir

        fun filteredChapters(chapters: List<MangaChapterItem>): List<MangaChapterItem> =
            filterChaptersByDirectoryConstraints(
                chapters = chapters,
                translationGroup = normalizedTranslationGroup,
                publisherUid = newPublisherUid,
                publisherName = newPublisherName,
                keepUnknownPublisher = true
            )

        if (oldName == newCleanName) {
            getFileLock(oldName).withLock {
                val mergedDir = currentDir.copy(
                    sourceFid = resolvedSourceFid,
                    translationGroup = normalizedTranslationGroup,
                    publisherUid = newPublisherUid,
                    publisherName = newPublisherName,
                    chapters = filteredChapters(currentSectionChapters),
                    lastUpdateTime = System.currentTimeMillis()
                )
                saveDirectory(mergedDir)
                mergedDir
            }
        } else {
            val lock1 = getFileLock(if (oldName < newCleanName) oldName else newCleanName)
            val lock2 = getFileLock(if (oldName < newCleanName) newCleanName else oldName)

            lock1.withLock {
                lock2.withLock {
                    val targetDir = loadDirectory(newCleanName)
                        ?.takeIf { it.sourceFid == resolvedSourceFid }

                    val mergedChapters = if (targetDir != null) {
                        mergeAndSortChapters(
                            filteredChapters(targetDir.chapters),
                            filteredChapters(currentSectionChapters)
                        )
                    } else filteredChapters(currentSectionChapters)

                    val newStrategy = targetDir?.strategy ?: currentDir.strategy
                    val newSourceKey = targetDir?.sourceKey
                        ?: if (currentDir.strategy == DirectoryStrategy.TAG) currentDir.sourceKey else newCleanName

                    val mergedDir = MangaDirectory(
                        cleanBookName = newCleanName,
                        strategy = newStrategy,
                        sourceKey = newSourceKey,
                        chapters = mergedChapters,
                        lastUpdateTime = System.currentTimeMillis(),
                        searchKeyword = currentDir.searchKeyword,
                        sourceFid = resolvedSourceFid,
                        translationGroup = normalizedTranslationGroup,
                        publisherUid = newPublisherUid,
                        publisherName = newPublisherName
                    )

                    saveDirectory(mergedDir)

                    val oldFile = getDirectoryFile(oldName)
                    if (oldFile.exists()) oldFile.delete()
                    memoryCache.remove(oldName)

                    mergedDir
                }
            }
        }
    }
}
