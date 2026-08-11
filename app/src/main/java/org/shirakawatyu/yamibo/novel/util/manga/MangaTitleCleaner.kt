package org.shirakawatyu.yamibo.novel.util.manga

import java.text.Normalizer

/**
 * 漫画标题清洗工具
 */
class MangaTitleCleaner {
    companion object {
        fun getCleanThreadTitle(rawTitle: String): String {
            return rawTitle.replace(
                Regex("(?i)\\s+[-—–_]+\\s+(.*?[区板]\\s+[-—–_]+\\s+)?(百合会|论坛|手机版|Powered by).*$"),
                ""
            ).trim()
        }

        /**
         * Ignore visible URL text used as cross-post references.
         */
        fun isUrlLikeChapterTitle(rawTitle: String): Boolean {
            val compact = rawTitle.trim().replace(Regex("\\s+"), "")
            if (compact.isBlank()) return false

            val lower = compact.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
                return true
            }
            if (lower.contains("forum.php") || lower.contains("thread-")) return true
            if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(compact)) return true
            if (compact.length > 80 && Regex("[/?=&]").containsMatchIn(compact)) return true
            return false
        }

        /**
         * 判断链接文字是否是"上一话/下一话/目录"这类楼内导航标签。首楼常放指向前后
         * 话帖子的跳转链接，链接文字不是真实章节标题（"上一话"还会被话数提取误读成
         * 第 1 话），不能当章节收进目录；这些帖子会由搜索/标签更新按真实标题收录。
         * 只做去掉括号段和装饰符后的整串精确匹配，带副标题的真实标题不受影响。
         */
        fun isNavigationLinkTitle(rawTitle: String): Boolean {
            val core = rawTitle
                .replace(Regex("【.*?】|\\[.*?\\]"), "")
                .trim {
                    it.isWhitespace() || it in "<>《》()（）「」『』←→↑↓«»◀▶.。，,、:：-—~～|｜"
                }
            if (core.isBlank()) return false
            return core.matches(Regex("[上下前后後]一[话話章回页頁篇]|(返回|回到)?目[录錄]|传送门|傳送門"))
        }

        /**
         * 剥掉标题开头的括号标注段。同人作品常以"(原作名!#N)"这类原作/出处标记开头
         * （如"( ぼっち・ざ・おんりー!#2)ぼ喜多・が・ろっく2"），真实作品名在括号段
         * 后面；把标记留在书名里会让同 parody 的不同作品全部撞进同一个目录。
         * 仅当括号段后仍有实质内容时剥掉，作品名本身写在括号里的整段标题不动。
         */
        private fun stripLeadingParenAnnotations(value: String): String {
            var text = value
            while (true) {
                val lead = Regex("^\\s*[（(][^()（）]+[)）]").find(text) ?: break
                val rest = text.substring(lead.range.last + 1).trim()
                if (rest.length < 2) break
                text = rest
            }
            return text
        }

        /**
         * Extract cleaned book title for directory and search matching.
         */
        fun getCleanBookName(rawTitle: String): String {
            var clean = getCleanThreadTitle(rawTitle)

            clean = clean.replace(Regex("\\s+-\\s+.*?(中文百合漫画区|百合会|论坛).*$"), "")
            clean = clean.replace(Regex("【.*?】|\\[.*?\\]"), "")
            clean = clean.replace(Regex("(?i)[\\(（]?c\\d+[\\)）]?"), "")
            clean = stripLeadingParenAnnotations(clean)
            clean = clean.replace(Regex("\\s*[|｜].*$"), "")

            // 2.5 集合类后缀（短篇集/合集/选集/总集等）本身就是书名的一部分，其后紧跟的通常是
            // 单篇标题（如"……短篇集[原作：xxx]天使的抉择"去括号后变成"……短篇集天使的抉择"，
            // 中间没有任何分隔符）。必须在下面的章节标记截断之前先在后缀词处截住，否则要么把
            // "集"字当成普通文字被后面的规则连同单篇标题一起吞掉，要么（若单篇标题里没有可识别
            // 的章节标记）整段单篇标题会被误当成书名的一部分。
            val collectionSuffixPattern = Regex("(${COLLECTION_SUFFIXES.joinToString("|")})")
            collectionSuffixPattern.find(clean)?.let { suffixMatch ->
                clean = clean.substring(0, suffixMatch.range.last + 1)
            }

            // 3. 截断章节标记及其后面的所有内容
            val chapterMarkerPattern = Regex(
                "(?i)(" +
                        "第\\s*[\\d\\.\\-零一二两三四五六七八九十百千]+|" +
                        "\\s*(?:第\\s*)?(?<!\\d)[\\d０-９]+(?:[\\.．][\\d０-９]+)?\\s*[+＋]\\s*[\\d０-９]+(?:[\\.．][\\d０-９]+)?\\s*(?:[话話织回章节幕折更])?\\s*(?=[：:—\\-「【\\[(（《]|\\s|$)|" +
                        "[-—\\s]*[#＃]\\s*\\d+|" +
                        "[-—\\s]*S\\d+(\\s*EP\\d+)?|" +
                        "[-—\\s]*EP\\d+|" +
                        "[-—\\s]*Vol\\.?\\s*\\d+|" +
                        "[-—\\s]*Ch\\.?\\s*\\d+|" +
                        "[-—\\s]*(番外|特典|卷后附|卷彩页|附录|短篇(?!集)|单行本|最终话|最終話|最终回|最終回|大结局)|" +
                        "(前篇|上篇|中篇|后篇|下篇)|" +
                        "[-—\\s]+(上|中|下)|" +
                        "[-—\\s]*[(（]\\s*[\\d\\.\\-零一二两三四五六七八九十百千]+\\s*[)）]|" +
                        "\\s*(?<!\\d)\\d+(?:\\.\\d+)?\\s*(?:[话話织回章节幕折更])?\\s*(?=[：:—\\-「【\\[(（《]|\\s|$)" +
                        ")"
            )
            val markerMatch = chapterMarkerPattern.find(clean)
            if (markerMatch != null) {
                clean = clean.substring(0, markerMatch.range.first)
            }

            // 截断章节标记后可能留下未闭合的书名号段（如"……动漫 ——《谈话室笔记"，
            // 原标题是"……《谈话室笔记9-12》"），连同前面的连接符一起去掉。
            clean = clean.replace(Regex("[—\\-～~\\s]*《[^》]*$"), "")

            clean = clean.replace(Regex("\\s*\\d+(\\.\\d+)?\\s*$"), "")
            clean = clean.replace(Regex("[！？\\?！!~。，、\\.]+$"), "")
            clean = clean.replace(Regex("^[\\s\\-|/\\)#]+|[\\s\\-|/\\(#:]+$"), "").trim()

            // 章节标记截断可能留下未配对的括号（如"( ぼっち・ざ・おんりー!#2)…"被截成
            // "( ぼっち・ざ・おんりー"）。只在整串里找不到另一半括号时才剥掉，配对括号不动。
            if (!clean.contains(')') && !clean.contains('）')) {
                clean = clean.trimStart('(', '（', ' ')
            }
            if (!clean.contains('(') && !clean.contains('（')) {
                clean = clean.trimEnd(')', '）', ' ')
            }

            return clean
        }

        fun extractTranslationGroup(rawTitle: String): String {
            val title = getCleanThreadTitle(rawTitle)
            val bracketCandidate = Regex("""[【\[\(（「『]([^】\]\)）」』]{2,64})[】\]\)）」』]""")
                .findAll(title)
                .mapNotNull { extractExplicitTranslationGroup(it.groupValues[1]) }
                .firstOrNull()
            if (!bracketCandidate.isNullOrBlank()) return bracketCandidate

            return extractExplicitTranslationGroup(title).orEmpty()
        }

        /**
         * 目录归并用的"发布组"标识。优先取显式汉化组名；部分制作组不带"汉化"字样
         * （如【大友同好會】），按论坛惯例【】放组名、[] 放分类/原作者，此时取第一个
         * 【】段兜底。个人发布标注（个人翻译/转载等）不算组名，交给发布者逻辑处理。
         * 仅供 DirectoryRepository 做版本归并，不影响搜索关键词等其他链路。
         */
        fun extractReleaseGroup(rawTitle: String): String {
            val explicit = extractTranslationGroup(rawTitle)
            if (explicit.isNotBlank()) return explicit

            val bracket = Regex("【([^【】]{2,20})】").find(getCleanThreadTitle(rawTitle))
                ?.groupValues?.get(1)?.trim().orEmpty()
            if (bracket.isBlank() || isIndividualRelease(bracket)) return ""
            return bracket
        }

        fun matchesTranslationGroup(rawTitle: String, translationGroup: String): Boolean {
            if (translationGroup.isBlank()) return true
            val normalizedTitle = normalizeTranslationGroup(rawTitle)
            val normalizedGroup = normalizeTranslationGroup(translationGroup)
            if (normalizedGroup.isNotBlank() && normalizedTitle.contains(normalizedGroup)) return true

            val groupCore = normalizedGroup.replace(TRANSLATION_ROLE_PATTERN, "")
            if (groupCore.length >= 2 && normalizedTitle.contains(groupCore)) return true

            val titleGroup = extractTranslationGroup(rawTitle)
            if (titleGroup.isBlank()) return false
            val normalizedTitleGroup = normalizeTranslationGroup(titleGroup)
            val titleGroupCore = normalizedTitleGroup.replace(TRANSLATION_ROLE_PATTERN, "")
            return normalizedTitleGroup == normalizedGroup ||
                    (groupCore.length >= 2 && titleGroupCore.contains(groupCore)) ||
                    (titleGroupCore.length >= 2 && groupCore.contains(titleGroupCore))
        }

        private fun extractExplicitTranslationGroup(value: String): String? {
            val suffixForm = Regex(
                """(?<![\p{L}\p{N}·._&＆+＋×、/／\-])([\p{L}\p{N}·._&＆+＋×、/／\-]{1,40}(?:汉化组|漢化組))""",
                RegexOption.IGNORE_CASE
            )
            val prefixForm = Regex(
                """(?<![\p{L}\p{N}·._&＆+＋×、/／\-])((?:汉化|漢化)[\p{L}\p{N}·._&＆+＋×、/／\-]{1,40}(?:组|組))""",
                RegexOption.IGNORE_CASE
            )
            return sequenceOf(suffixForm, prefixForm)
                .mapNotNull { pattern -> pattern.find(value)?.groupValues?.get(1) }
                .map(::sanitizeTranslationGroup)
                .firstOrNull(String::isNotBlank)
        }

        private fun sanitizeTranslationGroup(value: String): String =
            value.replace(Regex("\\s+"), " ")
                .trim(' ', '-', '—', '_', '|', '｜', ':', '：', '·')

        private fun normalizeTranslationGroup(value: String): String =
            value.lowercase()
                .replace('會', '会')
                .replace('組', '组')
                .replace('漢', '汉')
                .replace('譯', '译')
                .replace(Regex("[\\s\\p{Punct}【】（）「」『』·・＆＋×、／]+"), "")

        private val TRANSLATION_ROLE_PATTERN = Regex(
            "(?:联合|聯合|个人|個人|自)?(?:汉化|漢化|翻译|翻譯|译制|譯製|制作|製作|扫图|掃圖|嵌字|修图|修圖|校对|校對|图源|圖源|润色|潤色|字幕)(?:组|組|社|团队|團隊|工作室|team)?",
            RegexOption.IGNORE_CASE
        )

        /**
         * 提取核心搜索词
         */
        fun getSearchKeyword(rawTitle: String): String {
            val cleanName = getCleanBookName(rawTitle)
                .replace(Regex("\\s+"), " ")
                .trim()
            val rawSegments = getCleanThreadTitle(rawTitle)
                .replace(Regex("【.*?】|\\[.*?\\]"), "|")
                .split('|')
                .map { segment ->
                    segment
                        .replace(
                            Regex(
                                "(?i)(第\\s*[\\d\\.零一二两三四五六七八九十百千]+.*|" +
                                        "\\d+(?:\\.\\d+)?\\s*[话話回章节].*|" +
                                        "番外.*|特典.*|最终话.*|最終話.*)$"
                            ),
                            ""
                        )
                        .replace(Regex("\\s+"), "")
                        .trim(' ', '-', '—', '_', '|', '｜', '。', '！', '!', '？', '?')
                }
                .filter { it.length >= 4 && !extractTranslationGroup(it).equals(it, true) }

            return rawSegments
                .maxByOrNull { it.length }
                ?.take(24)
                ?.takeIf { it.isNotBlank() }
                ?: cleanName.take(24).trim()
        }

        /**
         * 目录列表里的章节标题展示：去掉【汉化组】[原作者]等括号段和重复的作品名，
         * 只留话数/副标题部分（如"13.2"、"第4话 出差"）；清理后为空则回退 fallback
         * （通常传章节编号）。避免目录里出现整行的原始帖子标题。
         */
        fun getDisplayChapterTitle(rawTitle: String, bookName: String, fallback: String): String {
            var text = getCleanThreadTitle(rawTitle)
                .replace(Regex("【.*?】|\\[.*?\\]"), "")
                .trim()
            // 与 getCleanBookName 一致：开头的原作/出处括号标注不属于章节标题
            text = stripLeadingParenAnnotations(text)
            if (bookName.isNotBlank()) {
                // 书名常整体落在括号段里（如"( ぼっち・ざ・おんりー!#2)副标题"），只删书名
                // 会留下"!#2)"这样的残渣，此时把包含书名的整个括号段删掉
                val parenGroup =
                    Regex("[（(][^()（）]*${Regex.escape(bookName)}[^()（）]*[)）]")
                text = if (parenGroup.containsMatchIn(text)) {
                    parenGroup.replace(text, "")
                } else {
                    text.replace(bookName, "")
                }.trim()
            }
            // (C102) 这类场刊编号是发布信息，不是章节副标题
            text = text.replace(Regex("(?i)[（(]\\s*c\\d+\\s*[)）]"), "").trim()
            // 去掉书名（如"……《谈话室笔记"）后残留的半截书名号/括号一并修剪
            text = text.trim(
                ' ', '-', '—', '_', '|', '｜', ':', '：', '·',
                '《', '》', '「', '」', '『', '』', '（', '）', '(', ')'
            )
            // 旧目录名尚未迁移（书名仍带"( "前缀）时，删书名会剩下"!#2)"这样的
            // 章节标记尾巴，这里兜底剥掉；话数在左侧编号列有展示，不损失信息
            text = text.replace(Regex("^[!！]*\\s*[#＃]\\s*\\d+(?:[\\.．]\\d+)?\\s*[)）]?\\s*"), "")
            return text.ifBlank { fallback }
        }

        /**
         * 判断已存目录名是否是旧版清洗器留下的残次品（如"作品名 52+"——旧版把"52+52.5"
         * 只截掉了"52.5"）。条件：存量名 = 新清洗名 + 纯章节编号残渣（含数字的编号/标点片段）。
         * 用户手动改的名字（如加"(完结)"等文字后缀）不会命中，不会被误还原。
         */
        fun isStaleCleanBookName(storedName: String, freshName: String): Boolean {
            if (freshName.isBlank() || storedName == freshName) return false
            if (!storedName.startsWith(freshName)) return false
            val residue = storedName.removePrefix(freshName).trim()
            if (residue.isEmpty() || residue.none(Char::isDigit)) return false
            return residue.matches(Regex("[\\d０-９.．+＋\\-—~～\\s第话話回章节]+"))
        }

        /**
         * 判断已存目录名是否是旧版清洗器"切太短"留下的残次品（如短篇集/合集类作品，旧版没有
         * 集合类后缀识别，把"XX短篇集"从"短篇"处直接截断只存了"XX"）。与 [isStaleCleanBookName]
         * 相反：那个处理的是存量名比新清洗名长，这个处理存量名比新清洗名短的情况。
         * 条件：新清洗名 = 存量名 + 集合类后缀词，避免误还原用户手动改短的名字。
         */
        fun isTruncatedCleanBookName(storedName: String, freshName: String): Boolean {
            if (storedName.isBlank() || freshName == storedName) return false
            if (!freshName.startsWith(storedName)) return false
            val residue = freshName.removePrefix(storedName)
            return residue in COLLECTION_SUFFIXES
        }

        /**
         * 判断已存目录名是否是旧版清洗器留下的"未配对括号"残次品（如"( ぼっち・ざ・おんりー"——
         * 旧版截断章节标记后没有剥掉残留的左括号）。条件：去掉首尾括号和空白后与新清洗名相同，
         * 用户手动起的名字（多出的不只是括号）不会命中。
         */
        fun isParenResidueCleanBookName(storedName: String, freshName: String): Boolean {
            if (freshName.isBlank() || storedName == freshName) return false
            return storedName.trim(' ', '(', '（', ')', '）') == freshName
        }

        /**
         * 判断已存目录名是否是旧版清洗器留下的"原作标注"残次品：旧版把开头括号里的
         * 原作名（如"( ぼっち・ざ・おんりー!#2)…"里的"ぼっち・ざ・おんりー"）当成了
         * 书名，导致同 parody 的不同作品全撞进一个目录。条件：存量名（去掉首尾括号后）
         * 出现在当前帖标题的开头括号标注段里，且新清洗名与之不同。命中后目录应迁移到
         * 新名，并按新书名重新校验既有章节（甩掉混进来的其它作品）。
         */
        fun isParodyResidueCleanBookName(
            storedName: String,
            rawTitle: String,
            freshName: String
        ): Boolean {
            if (freshName.isBlank() || storedName == freshName) return false
            val core = storedName.trim(' ', '(', '（', ')', '）')
            if (core.isBlank() || core == freshName) return false
            val stripped = getCleanThreadTitle(rawTitle)
                .replace(Regex("【.*?】|\\[.*?\\]"), "")
                .trim()
            val lead = Regex("^\\s*[（(]([^()（）]+)[)）]").find(stripped)
                ?.groupValues?.get(1) ?: return false
            return lead.contains(core)
        }

        private val COLLECTION_SUFFIXES =
            setOf("短篇集", "合集", "选集", "選集", "总集", "總集", "精选集", "精選集")

        /**
         * 判断标题是否标注为个人/非固定团队发布（个人汉化、个人翻译、合作汉化、自翻、代发、
         * 渣翻及其常见变体如中字渣翻/个人渣翻/个人渣改翻、转载/授权转载、以及繁体写法）。
         * 这类作品通常没有稳定的汉化组名可用于跨帖归并同一目录，只能退回按发布者（论坛账号）
         * 区分——这也是目前唯一会自动写入目录"发布者"字段的场景，其余情况一律只依据作品名和
         * 汉化组，不自动记发布者，避免同一汉化组内多人分工投稿被错误拆开。
         */
        fun isIndividualRelease(rawTitle: String): Boolean {
            return Regex("个人汉化|個人漢化|个人翻译|個人翻譯|合作汉化|合作漢化|自翻|代发|代發|渣.{0,2}翻|转载|轉載")
                .containsMatchIn(rawTitle)
        }

        fun isAdministrativeThread(rawTitle: String): Boolean {
            val normalized = rawTitle.replace(Regex("\\s+"), "")
            return listOf(
                "公告", "版规", "论坛规则", "新人须知", "快速导航",
                "任务帖", "问题反馈", "找回账号", "修改密码", "版务"
            ).any { normalized.contains(it, ignoreCase = true) }
        }

        fun matchesSearchQuery(rawTitle: String, rawQuery: String): Boolean {
            val titleVariants = buildList {
                add(
                    normalizeSearchText(rawTitle)
                )
                add(
                    normalizeSearchText(rawTitle.replace(Regex("【.*?】|\\[.*?\\]"), ""))
                )
                add(normalizeSearchText(getCleanBookName(rawTitle)))
                add(normalizeSearchText(extractTranslationGroup(rawTitle)))
            }.filter(String::isNotBlank).distinct()
            val normalizedFullQuery = normalizeSearchText(rawQuery)
            if (normalizedFullQuery.isNotBlank() &&
                titleVariants.any { it.contains(normalizedFullQuery) }
            ) {
                return true
            }

            val terms = rawQuery
                .trim()
                .split(Regex("[\\s【】\\[\\]（）()《》「」『』·・,，、/／|｜]+"))
                .filter(String::isNotBlank)
            return terms.all { term ->
                val queryVariants = listOf(
                    normalizeSearchText(term),
                    normalizeSearchText(getCleanBookName(term)),
                    normalizeSearchText(term.replace(Regex("【.*?】|\\[.*?\\]"), ""))
                ).filter(String::isNotBlank).distinct()
                queryVariants.any { query ->
                    titleVariants.any { title ->
                        title.contains(query) ||
                                fuzzyChunkMatch(title, query)
                    }
                }
            }
        }

        fun getForumSearchKeyword(rawQuery: String): String {
            val candidates = rawQuery
                .trim()
                .split(Regex("[\\s【】\\[\\]（）()《》「」『』·・,，、/／|｜]+"))
                .map { candidate ->
                    candidate
                        .replace(
                            Regex(
                                "(?i)(第\\s*[\\d\\.零一二两三四五六七八九十百千]+.*|" +
                                        "\\d+(?:\\.\\d+)?\\s*[话話回章节卷].*|" +
                                        "番外.*|特典.*|最终话.*|最終話.*)$"
                            ),
                            ""
                        )
                        .trim()
                }
                .filter { normalizeSearchText(it).length >= 2 }
            val contentCandidates = candidates.filter { candidate ->
                extractTranslationGroup(candidate).isBlank()
            }
            return (contentCandidates.ifEmpty { candidates })
                .maxByOrNull { normalizeSearchText(it).length }
                ?.take(40)
                ?.takeIf(String::isNotBlank)
                ?: rawQuery.trim().take(40)
        }

        fun getDirectoryForumSearchKeyword(
            cleanBookName: String,
            rawTitle: String,
            configuredKeywords: String?
        ): String {
            val bookKeyword = getForumSearchKeyword(cleanBookName)
                .takeIf { normalizeSearchText(it).length >= 2 }
            if (bookKeyword != null) return bookKeyword

            val configuredKeyword = configuredKeywords
                ?.takeIf(String::isNotBlank)
                ?.let(::getForumSearchKeyword)
                ?.takeIf { normalizeSearchText(it).length >= 2 }
            return configuredKeyword ?: getSearchKeyword(rawTitle)
        }

        fun matchesDirectoryCandidate(
            rawText: String,
            cleanBookName: String,
            configuredKeywords: String?
        ): Boolean {
            if (cleanBookName.isNotBlank() && matchesSearchQuery(rawText, cleanBookName)) {
                return true
            }

            val normalizedBookName = normalizeSearchText(cleanBookName)
            return configuredKeywords
                .orEmpty()
                .split(Regex("[\\s【】\\[\\]（）()《》「」『』·・,，、/／|｜]+"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { normalizeSearchText(it) == normalizedBookName }
                .any { matchesSearchQuery(rawText, it) }
        }

        fun matchesPublisher(
            authorUid: String?,
            authorName: String?,
            publisherUid: String?,
            publisherName: String?
        ): Boolean {
            val targetUid = publisherUid?.trim().orEmpty()
            val targetName = normalizeSearchText(publisherName.orEmpty())
            if (targetUid.isBlank() && targetName.isBlank()) return true

            val candidateUid = authorUid?.trim().orEmpty()
            if (targetUid.isNotBlank() && candidateUid == targetUid) return true

            val candidateName = normalizeSearchText(authorName.orEmpty())
            return targetName.isNotBlank() && candidateName == targetName
        }
        fun matchesDirectoryConstraints(
            rawTitle: String,
            authorUid: String?,
            authorName: String?,
            translationGroup: String?,
            publisherUid: String?,
            publisherName: String?,
            keepUnknownPublisher: Boolean
        ): Boolean {
            val group = translationGroup.orEmpty()
            val hasGroup = group.isNotBlank()
            val hasPublisher = !publisherUid.isNullOrBlank() || !publisherName.isNullOrBlank()

            if (hasGroup && matchesTranslationGroup(rawTitle, group)) {
                return true
            }

            if (hasPublisher) {
                if (authorUid.isNullOrBlank() && authorName.isNullOrBlank()) {
                    return keepUnknownPublisher
                }
                return matchesPublisher(
                    authorUid = authorUid,
                    authorName = authorName,
                    publisherUid = publisherUid,
                    publisherName = publisherName
                )
            }

            return !hasGroup
        }

        private fun normalizeSearchText(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase()
                .replace('臺', '台')
                .replace('裏', '里')
                .replace('話', '话')
                .replace('冊', '册')
                .replace('巻', '卷')
                .replace(Regex("[\\s\\p{Punct}【】（）《》「」『』·・]+"), "")

        private fun fuzzyChunkMatch(title: String, query: String): Boolean {
            if (query.length < 4) return false
            val chunks = query.windowed(size = 3, step = 2, partialWindows = true)
                .filter { it.length >= 2 }
            if (chunks.isEmpty()) return false
            val matched = chunks.count(title::contains)
            return matched * 3 >= chunks.size * 2
        }

        private const val NUM =
            "(\\d+(?:\\.\\d+)?|[０-９]+(?:\\.[０-９]+)?|[〇零一二两三四五六七八九十百千]+|[①-⑳]|[Ⅰ-Ⅻ])"

        private const val ARABIC = "(\\d+(?:\\.\\d+)?|[０-９]+(?:\\.[０-９]+)?)"

        /**
         * 清洗出用于话数提取的标题。
         * - strict（默认）：【】[]()（）「」《》 整段删除——括号内容多为组名/作者/杂项，
         *   里面的数字通常不是话数；
         * - keepQuotedContent：仅去掉《》「」引号本身、保留内容。话数写在书名号里的标题
         *   （如"……《谈话室笔记9-12》"）严格模式会把数字连同书名一起删光，此时用这个
         *   模式重试。
         */
        private fun cleanTitleForNumber(rawTitle: String, keepQuotedContent: Boolean): String {
            val base = if (keepQuotedContent) {
                rawTitle
                    .replace(Regex("【.*?】|\\[.*?\\]|\\(.*?\\)|（.*?）"), "")
                    .replace(Regex("[《》「」『』]"), " ")
            } else {
                rawTitle.replace(Regex("【.*?】|\\[.*?\\]|\\(.*?\\)|（.*?）|「.*?」|《.*?》"), "")
            }
            return base
                .replace(Regex("\\d+\\s*[xX×]\\s*\\d+"), "")
                .replace(Regex("(?i)\\bc\\d+\\b"), "")
        }

        fun extractChapterNum(rawTitle: String): Float {
            if (Regex(
                    "番外|特典|附录|SP|卷后附|卷彩页|小剧场|小漫画",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(rawTitle)
            ) {
                return 0f
            }
            val strict = chapterNumFromCleanTitle(cleanTitleForNumber(rawTitle, keepQuotedContent = false))
            if (strict != 0f) return strict
            // 常规提取完全失败时才保留书名号内容重试，避免书名里的数字干扰正常标题
            return chapterNumFromCleanTitle(cleanTitleForNumber(rawTitle, keepQuotedContent = true))
        }

        private fun chapterNumFromCleanTitle(cleanTitle: String): Float {
            if (Regex("最终话|最終話|最终回|最終回|大结局").containsMatchIn(cleanTitle)) {
                return 999f
            }

            // 3. 计算微调值 (前中后篇、①②③)
            var subModifier = 0f

            val modPrefix = "(?<=[\\s\\-—_/(（\\[【话話回章节幕折更\\d]|^)"
            val modSuffix = "(?=[\\s)）\\]】!！？?。，~]*$)"

            if (Regex("(?:前篇|上篇|${modPrefix}上)$modSuffix").containsMatchIn(cleanTitle)) subModifier =
                0.1f
            else if (Regex("(?:中篇|${modPrefix}中)$modSuffix").containsMatchIn(cleanTitle)) subModifier =
                0.2f
            else if (Regex("(?:后篇|下篇|${modPrefix}下)$modSuffix").containsMatchIn(cleanTitle)) subModifier =
                0.3f

            val circleMap = mapOf(
                '①' to 0.1f,
                '②' to 0.2f,
                '③' to 0.3f,
                '④' to 0.4f,
                '⑤' to 0.5f,
                '⑥' to 0.6f,
                '⑦' to 0.7f,
                '⑧' to 0.8f,
                '⑨' to 0.9f
            )
            Regex("[①②③④⑤⑥⑦⑧⑨]").find(cleanTitle)
                ?.let { subModifier = circleMap[it.value[0]] ?: 0f }

            val baseNum =
                // 规则 1.1: 明确带“话”等字眼的 其之 (e.g., "14话 其之2")
                Regex("(?:第)?\\s*$NUM\\s*[话話织回章节幕折更].*?其[之の]?\\s*$NUM").find(cleanTitle)
                    ?.let {
                        parseNumber(it.groupValues[1]) + (parseNumber(it.groupValues[2]) / 100f)
                    }
                // 规则 1.2: 没带话，但是靠得很近的其之 (限制中间长度，防止跨越太长匹配到“百合”)
                    ?: Regex("(?:第)?\\s*$NUM\\s*[^\\d零一二两三四五六七八九十百千]{0,5}?其[之の]?\\s*$NUM").find(
                        cleanTitle
                    )?.let {
                        parseNumber(it.groupValues[1]) + (parseNumber(it.groupValues[2]) / 100f)
                    }
                    // 规则 2: 第X-Y
                    ?: Regex("第\\s*$NUM\\s*[-—]\\s*$NUM").find(cleanTitle)?.let {
                        parseNumber(it.groupValues[1]) + (parseNumber(it.groupValues[2]) / 100f)
                    }
                    // 规则 3.1: (第)X话 (核心修复：不需要“第”字也能完美匹配“02话”)
                    ?: Regex("(?:第)?\\s*$NUM\\s*[话話织回章节幕折更]").find(cleanTitle)?.let {
                        parseNumber(it.groupValues[1])
                    }
                    // 规则 3.2: 第X (必须有第)
                    ?: Regex("第\\s*$NUM(?=[\\s:：,，.。!！?？|｜\\-—]|$)").find(cleanTitle)?.let {
                        parseNumber(it.groupValues[1])
                    }
                    // 规则 3.3: 无"第"无"话"的纯 X-Y (如"向笨蛋告白 2-1")。必须先于规则4匹配，
                    // 否则规则4只截取横杠后半段（如"-1"→1），把横杠前的章号（2）丢掉。
                    ?: Regex("(?<![\\d.])$ARABIC\\s*[-—]\\s*$ARABIC(?!\\d)").find(cleanTitle)?.let {
                        parseNumber(it.groupValues[1]) + (parseNumber(it.groupValues[2]) / 100f)
                    }
                    // 规则 4: 分隔符后跟数字 (限制为纯阿拉伯数字 ARABIC)
                    ?: Regex("[-—|｜]\\s*$ARABIC(?:\\s|\\.|$)").find(cleanTitle)?.let {
                        it.groupValues[1].toFloatOrNull() ?: 0f
                    }
                    // 规则 5: 孤立数字 (限制为纯阿拉伯数字 ARABIC，拒绝把“百”当成孤立数字)
                    ?: Regex("(?:^|\\s)([^\\d\\s部季名次期天卷]?)\\s*$ARABIC\\s*([^\\d\\s部季名次期天卷]?)(?=[\\s:：—\\-,，.。!！?？|｜]|$|[^\\d])").find(
                        cleanTitle
                    )?.let {
                        it.groupValues[2].toFloatOrNull() ?: 0f
                    }
                    // 规则 6: 结尾数字 (限制为纯阿拉伯数字 ARABIC)
                    ?: Regex("$ARABIC(?!.*\\d)").find(cleanTitle)?.let {
                        if (it.groupValues[1] != ".") it.groupValues[1].toFloatOrNull()
                            ?: 0f else 0f
                    }
                    ?: 0f // 兜底

            return Math.round((baseNum + subModifier) * 1000) / 1000f
        }

        private fun formatLabelNum(numStr: String): String {
            val value = parseNumber(numStr)
            return if (value % 1f == 0f) value.toInt().toString() else value.toString()
        }

        private fun formatCombinedChapterLabel(value: String): String {
            return value
                .split(Regex("\\s*[+＋]\\s*"))
                .joinToString("+") { formatLabelNum(it) }
        }

        /**
         * 提取章节显示文案（如"第7-2话"→"7-2"、"52+52.5"→"52+52.5"），
         * 用于列表和阅读器顶部直接展示原始分段/合集编号，避免把排序用 chapterNum 当作显示文本。
         * 仅当标题确实是 X-Y / X+Y / 其之 这类分段形式时返回；其余情况返回 null，由调用方按
         * chapterNum 数值格式化（如单纯的"7"或前后篇的"7.1"）。
         */
        fun extractChapterLabel(rawTitle: String): String? {
            if (Regex(
                    "番外|特典|附录|SP|卷后附|卷彩页|小剧场|小漫画|最终话|最終話|最终回|最終回|大结局",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(rawTitle)
            ) {
                return null
            }
            chapterLabelFromCleanTitle(cleanTitleForNumber(rawTitle, keepQuotedContent = false))
                ?.let { return it }
            // 与 extractChapterNum 的重试一致：话数写在书名号里时保留内容再试一次
            return chapterLabelFromCleanTitle(cleanTitleForNumber(rawTitle, keepQuotedContent = true))
        }

        private fun chapterLabelFromCleanTitle(cleanTitle: String): String? {
            Regex("(?:第)?\\s*($ARABIC(?:\\s*[+＋]\\s*$ARABIC)+)\\s*[话話织回章节幕折更]?").find(cleanTitle)
                ?.let { return formatCombinedChapterLabel(it.groupValues[1]) }
            Regex("(?:第)?\\s*$NUM\\s*[话話织回章节幕折更].*?其[之の]?\\s*$NUM").find(cleanTitle)?.let {
                return "${formatLabelNum(it.groupValues[1])}-${formatLabelNum(it.groupValues[2])}"
            }
            Regex("(?:第)?\\s*$NUM\\s*[^\\d零一二两三四五六七八九十百千]{0,5}?其[之の]?\\s*$NUM").find(cleanTitle)
                ?.let {
                    return "${formatLabelNum(it.groupValues[1])}-${formatLabelNum(it.groupValues[2])}"
                }
            Regex("第\\s*$NUM\\s*[-—]\\s*$NUM").find(cleanTitle)?.let {
                return "${formatLabelNum(it.groupValues[1])}-${formatLabelNum(it.groupValues[2])}"
            }
            Regex("(?<![\\d.])$ARABIC\\s*[-—]\\s*$ARABIC(?!\\d)").find(cleanTitle)?.let {
                return "${formatLabelNum(it.groupValues[1])}-${formatLabelNum(it.groupValues[2])}"
            }
            return null
        }


        /**
         * 章节显示编号：目录、原生阅读器、WebView 兜底阅读器共用同一套逻辑，
         * 保证同一章在目录和标题下方的展示完全一致。
         * - chapterNum <= 0（识别失败/番外等）时用列表位置 fallbackNumber 兜底，
         *   不再显示"Ex"/"SP"，与目录保持一致。
         * - 最终话（chapterNum >= 999）显示"终"。
         * - "第7-2话"/"12-2"/"52+52.5" 等分段/合集标题按 extractChapterLabel 原样展示。
         * - 纯小数编号（如 13.2）保留小数点，不再转成"13-2"。
         */
        fun formatChapterDisplayNumber(
            rawTitle: String,
            chapterNum: Float,
            fallbackNumber: Int
        ): String {
            if (!chapterNum.isFinite() || chapterNum <= 0f) return fallbackNumber.toString()
            if (chapterNum >= 999f) return "终"
            extractChapterLabel(rawTitle)?.let { return it }
            return if (chapterNum % 1f == 0f) {
                chapterNum.toInt().toString()
            } else {
                chapterNum.toString().trimEnd('0').trimEnd('.')
            }
        }

        /**
         * 暴力提取标题中出现的所有数字
         */
        fun extractAllPossibleNumbers(rawTitle: String): List<Float> {
            val cleanTitle = rawTitle
                .replace(Regex("【.*?】|\\[.*?\\]|\\(.*?\\)|（.*?）|「.*?」|《.*?》"), "")
                .replace(Regex("(?i)\\bc\\d+\\b"), "")
            // 抓出所有范围在 [0, 999) 的有效数字
            var matches = Regex(NUM)
                .findAll(cleanTitle)
                .map { parseNumber(it.groupValues[1]) }
                .filter { it in 0f..<999f }
                .toList()
            // 与 extractChapterNum 一致：数字全在书名号里时保留内容重试
            if (matches.isEmpty()) {
                matches = Regex(NUM)
                    .findAll(cleanTitleForNumber(rawTitle, keepQuotedContent = true))
                    .map { parseNumber(it.groupValues[1]) }
                    .filter { it in 0f..<999f }
                    .toList()
            }

            val pool = mutableSetOf<Float>()
            pool.addAll(matches)

            for (i in 0 until matches.size) {
                for (j in 0 until matches.size) {
                    if (i == j) continue

                    val major = matches[i]
                    val minor = matches[j]

                    var divisor = 10f
                    while (minor >= divisor) {
                        divisor *= 10f
                    }
                    pool.add(major + minor / divisor)

                    pool.add(major + minor / (divisor * 10f))
                }
            }
            return pool.toList()
        }

        /**
         * 从 URL 提取 tid (用于去重唯一键)
         */
        fun extractTidFromUrl(url: String): String? {
            val match = Regex("tid=(\\d+)").find(url)
                ?: Regex("thread-(\\d+)-").find(url)
                ?: Regex("[?&]ptid=(\\d+)").find(url)
            return match?.groupValues?.get(1)
        }

        /**
         * 将中文数字/阿拉伯数字/全角/罗马/圆圈 统一解析为 Float 浮点数
         */
        private fun parseNumber(numStr: String): Float {
            // 1. 标准半角阿拉伯数字
            numStr.toFloatOrNull()?.let { return it }

            // 2. 全角数字转半角
            val halfWidthStr = numStr.map {
                if (it in '０'..'９') (it.code - '０'.code + '0'.code).toChar()
                else if (it == '．') '.'
                else it
            }.joinToString("")
            halfWidthStr.toFloatOrNull()?.let { return it }

            // 3. 特殊符号映射字典
            val specialMap = mapOf(
                '①' to 1f, '②' to 2f, '③' to 3f, '④' to 4f, '⑤' to 5f,
                '⑥' to 6f, '⑦' to 7f, '⑧' to 8f, '⑨' to 9f, '⑩' to 10f,
                '⑪' to 11f, '⑫' to 12f, '⑬' to 13f, '⑭' to 14f, '⑮' to 15f,
                '⑯' to 16f, '⑰' to 17f, '⑱' to 18f, '⑲' to 19f, '⑳' to 20f,
                'Ⅰ' to 1f, 'Ⅱ' to 2f, 'Ⅲ' to 3f, 'Ⅳ' to 4f, 'Ⅴ' to 5f,
                'Ⅵ' to 6f, 'Ⅶ' to 7f, 'Ⅷ' to 8f, 'Ⅸ' to 9f, 'Ⅹ' to 10f,
                'Ⅺ' to 11f, 'Ⅻ' to 12f
            )
            // 如果提取出来的刚好是单个特殊符号
            if (numStr.length == 1 && specialMap.containsKey(numStr[0])) {
                return specialMap[numStr[0]]!!
            }

            // 4. 中文数字处理
            var total = 0f
            var number = -1f
            for (i in numStr.indices) {
                val c = numStr[i]
                val v = when (c) {
                    '〇', '零' -> 0f; '一' -> 1f; '二', '两' -> 2f; '三' -> 3f
                    '四' -> 4f; '五' -> 5f; '六' -> 6f; '七' -> 7f
                    '八' -> 8f; '九' -> 9f; else -> -1f
                }
                if (v != -1f) {
                    number = v
                } else {
                    val unit = when (c) {
                        '十' -> 10f; '百' -> 100f; '千' -> 1000f; else -> 0f
                    }
                    if (unit > 0) {
                        if (number == -1f) number = 1f
                        total += number * unit
                        number = -1f
                    }
                }
            }
            if (number != -1f) {
                total += number
            }

            if (total > 0f || numStr.contains(Regex("[〇零]"))) {
                return total
            }
            return -1f // 兜底：解析失败
        }
    }
}
