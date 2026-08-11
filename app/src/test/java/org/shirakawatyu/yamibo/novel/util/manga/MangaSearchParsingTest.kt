package org.shirakawatyu.yamibo.novel.util.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.parser.MangaHtmlParser

class MangaSearchParsingTest {
    private val fullTitle = "【提灯喵汉化组】后续任凭想象[やナい]把我弄得乱七八糟吧！"

    @Test
    fun tagIdsAreReadFromRealPcThreadLinkShapes() {
        val html = """
            <a title="drunken_my_boss" href="https://bbs.yamibo.com/misc.php?mod=tag&amp;id=20378">tag</a>
            <a href="misc.php?mod=tag&amp;id=21754">tag</a>
        """.trimIndent()

        assertEquals(listOf("20378", "21754"), MangaHtmlParser.findTagIdsMobile(html))
    }

    @Test
    fun searchKeywordUsesContinuousDistinctiveTitleSegment() {
        assertEquals("把我弄得乱七八糟吧", MangaTitleCleaner.getSearchKeyword(fullTitle))
    }

    @Test
    fun matchingIgnoresBracketedAuthorInsertedInsideTitle() {
        assertTrue(
            MangaTitleCleaner.matchesSearchQuery(
                fullTitle,
                "后续任凭想象把我弄得乱七八糟吧"
            )
        )
    }

    @Test
    fun matchingSupportsMultipleFieldsAndCommonCharacterVariants() {
        val searchableText = "【犬山あむ】柊酱与富川酱 第10話 Kakukuroi汉化组 犬山あむ"
        assertTrue(
            MangaTitleCleaner.matchesSearchQuery(
                searchableText,
                "柊酱 富川酱 Kakukuroi"
            )
        )
        assertTrue(MangaTitleCleaner.matchesSearchQuery(searchableText, "第10话"))
    }

    @Test
    fun forumKeywordPrefersBookTitleOverChapterAndTranslationGroup() {
        assertEquals(
            "柊酱与富川酱",
            MangaTitleCleaner.getForumSearchKeyword(
                "柊酱与富川酱 第10话 Kakukuroi汉化组"
            )
        )
        assertEquals(
            "Kakukuroi汉化组",
            MangaTitleCleaner.getForumSearchKeyword("Kakukuroi汉化组")
        )
    }

    @Test
    fun directorySearchUsesBookNameInsteadOfCombinedFilters() {
        assertEquals(
            "DrunkenMyBoss",
            MangaTitleCleaner.getDirectoryForumSearchKeyword(
                cleanBookName = "DrunkenMyBoss",
                rawTitle = "[个人汉化][BBTan] DrunkenMyBoss 第一话",
                configuredKeywords = "BBTan 个人汉化"
            )
        )
    }

    @Test
    fun directoryCandidateKeepsAllBookMatchesAndSupportsAliases() {
        assertTrue(
            MangaTitleCleaner.matchesDirectoryCandidate(
                rawText = "[合作汉化][BBTan] DrunkenMyBoss 第四十七话+后记",
                cleanBookName = "DrunkenMyBoss",
                configuredKeywords = "个人汉化 BBTan"
            )
        )
        assertTrue(
            MangaTitleCleaner.matchesDirectoryCandidate(
                rawText = "[某汉化组] 酒醉上司 第2话",
                cleanBookName = "DrunkenMyBoss",
                configuredKeywords = "酒醉上司"
            )
        )
        assertFalse(
            MangaTitleCleaner.matchesDirectoryCandidate(
                rawText = "[BBTan] 完全不同的作品 第1话",
                cleanBookName = "DrunkenMyBoss",
                configuredKeywords = "酒醉上司"
            )
        )
    }

    @Test
    fun threadStyleFavoriteUrlExtractsTid() {
        assertEquals(
            "546273",
            MangaTitleCleaner.extractTidFromUrl(
                "https://bbs.yamibo.com/thread-546273-1-1.html"
            )
        )
    }

    @Test
    fun koreanTitleWithUpperPartSuffixKeepsBookNameAndChapterOrder() {
        val title =
            "[个人汉化][Willow（윌로우）]장마에서 살아남는 방법（从梅雨中活下来的方法）上篇"

        assertEquals(
            "장마에서 살아남는 방법（从梅雨中活下来的方法）",
            MangaTitleCleaner.getCleanBookName(title)
        )
        assertEquals(0.1f, MangaTitleCleaner.extractChapterNum(title))
    }

    @Test
    fun bareDashChapterNumberKeepsLeadingChapterDigit() {
        // 无"第"无"话"的纯 X-Y 格式（如"向笨蛋告白 2-1"）曾被规则4只截取横杠后半段，
        // 算出 1 而不是 2.01，导致同书多章节号倒退（2,1,2,1,2...）。
        assertEquals(2.01f, MangaTitleCleaner.extractChapterNum("向笨蛋告白 2-1"))
        assertEquals(2.02f, MangaTitleCleaner.extractChapterNum("向笨蛋告白2-2"))
        assertEquals(5.01f, MangaTitleCleaner.extractChapterNum("向笨蛋告白5-1"))
    }

    @Test
    fun combinedChapterNumberKeepsDisplayLabel() {
        val title =
            "[長篇連載] 【大友同好會】 [ドスコイ]特别な中途半端(特别的半吊子) 52+52.5"

        assertEquals("特别な中途半端(特别的半吊子)", MangaTitleCleaner.getCleanBookName(title))
        assertEquals("52+52.5", MangaTitleCleaner.extractChapterLabel(title))
        assertEquals(52f, MangaTitleCleaner.extractChapterNum(title))
        assertEquals(
            "52+52.5",
            MangaTitleCleaner.formatChapterDisplayNumber(
                title,
                MangaTitleCleaner.extractChapterNum(title),
                1
            )
        )
    }

    @Test
    fun realCombinedChapterTitleCleansWithoutResidue() {
        // 真实标题（繁体 特別/半吊子帖）：清洗结果不得残留 "52+"
        val title = "【大友同好會】[ドスコイ]特別な中途半端(特别的半吊子) 52+52.5"
        assertEquals("特別な中途半端(特别的半吊子)", MangaTitleCleaner.getCleanBookName(title))
    }

    @Test
    fun chapterNumberInsideTitleQuotesIsExtracted() {
        // 话数写在书名号里（《谈话室笔记9-12》）：严格清洗会把《..》整段删光导致话数丢失，
        // 需要"保留书名号内容"的重试路径兜住
        val title = "[百合雜誌]【个人翻译】《上伊那牡丹，酒醉身姿似百合花般》动漫 ——《谈话室笔记9-12》"
        assertEquals(9.12f, MangaTitleCleaner.extractChapterNum(title))
        assertEquals("9-12", MangaTitleCleaner.extractChapterLabel(title))
        assertEquals("9-12", MangaTitleCleaner.formatChapterDisplayNumber(title, 9.12f, 4))
        // 截断话数后不能留下未闭合的"——《谈话室笔记"尾巴
        assertEquals(
            "《上伊那牡丹，酒醉身姿似百合花般》动漫",
            MangaTitleCleaner.getCleanBookName(title)
        )
        // 目录标题列：去掉作品名后应显示"谈话室笔记9-12"，不带残留书名号
        assertEquals(
            "谈话室笔记9-12",
            MangaTitleCleaner.getDisplayChapterTitle(
                title,
                "《上伊那牡丹，酒醉身姿似百合花般》动漫",
                "9-12"
            )
        )
        // 旧目录里存的是带尾巴的书名，标题列也要能修剪出干净结果
        assertEquals(
            "9-12",
            MangaTitleCleaner.getDisplayChapterTitle(
                title,
                "《上伊那牡丹，酒醉身姿似百合花般》动漫 ——《谈话室笔记",
                "9-12"
            )
        )
    }

    @Test
    fun quotedRetryDoesNotAffectNormalTitles() {
        // 常规标题（话数在书名号外）不受重试路径影响
        assertEquals(5f, MangaTitleCleaner.extractChapterNum("【某汉化组】《某部作品》 第5话"))
        // 完全无数字的标题依旧返回 0（走列表位置兜底）
        assertEquals(0f, MangaTitleCleaner.extractChapterNum("【提灯喵汉化组】就像你一样"))
    }

    @Test
    fun displayChapterTitleStripsBracketsAndBookName() {
        // 目录标题列去掉【汉化组】[原作者]和作品名，只留话数
        assertEquals(
            "13.2",
            MangaTitleCleaner.getDisplayChapterTitle(
                "【大友同好會】[原作:都築真紀／漫画:川上修一]魔法少女奈叶 EXCEEDS 13.2",
                "魔法少女奈叶 EXCEEDS",
                "13.2"
            )
        )
        assertEquals(
            "4",
            MangaTitleCleaner.getDisplayChapterTitle(
                "【提灯喵汉化组】[ユニ/YUNI]就像你一样 4",
                "就像你一样",
                "4"
            )
        )
        // 本身就是短话数文本的目录项保持原样
        assertEquals(
            "8.2",
            MangaTitleCleaner.getDisplayChapterTitle("8.2", "魔法少女奈叶 EXCEEDS", "8.2")
        )
        // 全部清掉后回退章节编号
        assertEquals(
            "1",
            MangaTitleCleaner.getDisplayChapterTitle("【提灯喵汉化组】就像你一样", "就像你一样", "1")
        )
    }

    @Test
    fun staleCleanBookNameDetectsOldCleanerResidue() {
        // 旧版清洗器把"52+52.5"只截掉"52.5"，存量目录名残留"52+"
        assertTrue(
            MangaTitleCleaner.isStaleCleanBookName(
                "特別な中途半端(特别的半吊子) 52+",
                "特別な中途半端(特别的半吊子)"
            )
        )
        assertTrue(MangaTitleCleaner.isStaleCleanBookName("作品名 第12话", "作品名"))
        // 名字一致 / 用户自定义文字后缀 / 完全不同的名字，都不算残次品
        assertFalse(MangaTitleCleaner.isStaleCleanBookName("作品名", "作品名"))
        assertFalse(MangaTitleCleaner.isStaleCleanBookName("作品名 (完结)", "作品名"))
        assertFalse(MangaTitleCleaner.isStaleCleanBookName("别的书 12", "作品名"))
        assertFalse(MangaTitleCleaner.isStaleCleanBookName("作品名 12", ""))
    }

    @Test
    fun chapterDisplayNumberMatchesDirectoryFormat() {
        // 纯小数编号（13.2）保留小数点，不转成"13-2"
        assertEquals(
            "13.2",
            MangaTitleCleaner.formatChapterDisplayNumber("魔法少女奈叶 EXCEEDS 13.2", 13.2f, 5)
        )
        // 分段标题（X-Y）原样展示为"12-2"
        assertEquals(
            "12-2",
            MangaTitleCleaner.formatChapterDisplayNumber("某漫画 12-2", 12.02f, 5)
        )
        // 整数编号
        assertEquals(
            "4",
            MangaTitleCleaner.formatChapterDisplayNumber("就像你一样 4", 4f, 9)
        )
        // 识别失败（chapterNum<=0）时用列表位置兜底，而非"Ex"
        assertEquals(
            "4",
            MangaTitleCleaner.formatChapterDisplayNumber("【提灯喵汉化组】就像你一样", 0f, 4)
        )
    }

    @Test
    fun samePageLinksIgnoreUrlTextReferences() {
        val html = """
            <div class="message">
                Previous: <a href="https://bbs.yamibo.com/forum.php?mod=viewthread&amp;tid=566102&amp;highlight=%E5%97%9C%E5%A5%BD%E5%93%81">https://bbs.yamibo.com/forum.php ... C%E5%A5%BD%E5%93%81</a><br>
                <a href="https://bbs.yamibo.com/forum.php?mod=viewthread&amp;tid=573149">2</a>
            </div>
        """.trimIndent()

        val parsed = MangaHtmlParser.extractSamePageLinks(html)

        assertEquals(1, parsed.size)
        assertEquals("573149", parsed.single().tid)
        assertEquals("2", parsed.single().rawTitle)
    }

    @Test
    fun samePageLinksIgnoreNavigationLinkText() {
        // 首楼"上一话/下一话/目录"这类导航链接不是章节，不能收进目录
        val html = """
            <div class="message">
                <a href="https://bbs.yamibo.com/thread-566102-1-1.html">←上一话</a><br>
                <a href="https://bbs.yamibo.com/thread-573149-1-1.html">下一話→</a><br>
                <a href="https://bbs.yamibo.com/thread-570000-1-1.html">目录</a><br>
                <a href="https://bbs.yamibo.com/thread-573150-1-1.html">第3话 出差</a>
            </div>
        """.trimIndent()

        val parsed = MangaHtmlParser.extractSamePageLinks(html)

        assertEquals(1, parsed.size)
        assertEquals("573150", parsed.single().tid)
    }

    @Test
    fun navigationLinkTitleDetection() {
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("上一话"))
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("下一話"))
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("←上一话"))
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("【某某汉化组】下一话"))
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("返回目录"))
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("目錄"))
        assertTrue(MangaTitleCleaner.isNavigationLinkTitle("传送门"))
        // 真实标题、分篇标记不受影响
        assertFalse(MangaTitleCleaner.isNavigationLinkTitle("第1话"))
        assertFalse(MangaTitleCleaner.isNavigationLinkTitle("前篇"))
        assertFalse(MangaTitleCleaner.isNavigationLinkTitle("后篇"))
        assertFalse(MangaTitleCleaner.isNavigationLinkTitle("上一话的回忆"))
        assertFalse(MangaTitleCleaner.isNavigationLinkTitle("2、去你家/君の家まで"))
    }

    @Test
    fun samePageLinksPreservePidInChapterUrls() {
        val html = """
            <div class="message">
                <a href="https://bbs.yamibo.com/forum.php?mod=redirect&amp;goto=findpost&amp;ptid=573142&amp;pid=41573469">2</a>
            </div>
        """.trimIndent()

        val parsed = MangaHtmlParser.extractSamePageLinks(html)

        assertEquals(1, parsed.size)
        assertEquals("41573469", parsed.single().pid)
        assertTrue(parsed.single().url.contains("pid=41573469"))
    }

    @Test
    fun explicitMangaDirectoryMarkerIsDetectedOnMobileAndDesktopPages() {
        val mobileHtml = """
            <div class="message">
                <a href="misc.php?mod=tag&id=21146">本作目录</a>
                <a href="thread-561605-1-1.html">1卷彩页</a>
                <a href="thread-561650-1-1.html">1话</a>
                <a href="thread-561651-1-1.html">2话</a>
            </div>
        """.trimIndent()
        val desktopHtml = """
            <table>
                <tbody>
                    <tr>
                        <td class="t_f" id="postmessage_41588349">
                            <font>本作目錄</font>
                            <a href="thread-561605-1-1.html">1卷彩页</a>
                            <a href="thread-561650-1-1.html">1话</a>
                            <a href="thread-561651-1-1.html">2话</a>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        assertTrue(MangaHtmlParser.hasExplicitMangaDirectoryMarker(mobileHtml))
        assertTrue(MangaHtmlParser.hasExplicitMangaDirectoryMarker(desktopHtml))
    }

    @Test
    fun plainDirectoryNavigationIsNotAnExplicitMangaDirectoryMarker() {
        val html = """
            <div class="message">
                <a href="thread-1-1-1.html">返回目录</a>
                <a href="thread-2-1-1.html">下一话</a>
            </div>
        """.trimIndent()

        assertFalse(MangaHtmlParser.hasExplicitMangaDirectoryMarker(html))
    }

    @Test
    fun administrativeThreadsAreExcluded() {
        assertTrue(MangaTitleCleaner.isAdministrativeThread("百合会新人须知/论坛规则"))
        assertFalse(MangaTitleCleaner.isAdministrativeThread(fullTitle))
    }

    @Test
    fun translationGroupOnlyAcceptsExplicitGroupFormats() {
        assertEquals(
            "提灯喵汉化组",
            MangaTitleCleaner.extractTranslationGroup("【提灯喵汉化组】作品名 第1话")
        )
        assertEquals(
            "Kakukuroi汉化组",
            MangaTitleCleaner.extractTranslationGroup(
                "【犬山あむ】柊酱与富川酱 第10话 Kakukuroi汉化组"
            )
        )
        assertEquals(
            "汉化工房九九组",
            MangaTitleCleaner.extractTranslationGroup(
                "【汉化工房九九组】[工藤える]江口家的纯情魅魔 2卷彩页"
            )
        )
        assertEquals("", MangaTitleCleaner.extractTranslationGroup("[やナい]作品名 第3话"))
        assertEquals("", MangaTitleCleaner.extractTranslationGroup("[A社×B组 联合汉化]作品名"))
    }

    @Test
    fun translationGroupMatchingRequiresAnExplicitGroupName() {
        assertTrue(
            MangaTitleCleaner.matchesTranslationGroup(
                "【提灯喵汉化组】作品名 第2话",
                "提灯喵汉化组"
            )
        )
        assertFalse(
            MangaTitleCleaner.matchesTranslationGroup(
                "【另一汉化组】作品名 第2话",
                "提灯喵汉化组"
            )
        )
    }

    @Test
    fun publisherMatchingUsesUidOrExactName() {
        assertTrue(
            MangaTitleCleaner.matchesPublisher(
                authorUid = "489445",
                authorName = "发布者A",
                publisherUid = "489445",
                publisherName = null
            )
        )
        assertTrue(
            MangaTitleCleaner.matchesPublisher(
                authorUid = null,
                authorName = "发布者B",
                publisherUid = null,
                publisherName = "发布者B"
            )
        )
        assertFalse(
            MangaTitleCleaner.matchesPublisher(
                authorUid = "123",
                authorName = "发布者A",
                publisherUid = null,
                publisherName = "发布者B"
            )
        )
    }
    @Test
    fun directoryConstraintsUseGroupWhenPublisherIsBlank() {
        val targetGroup = "\u63d0\u706f\u55b5\u6c49\u5316\u7ec4"

        assertTrue(
            MangaTitleCleaner.matchesDirectoryConstraints(
                rawTitle = "\u3010${targetGroup}\u3011\u6c89\u6eba\u4e8e\u7f6a\u6076\u7684\u72af\u7f6a\u767e\u5408\u77ed\u7bc7\u96c6 \u7b2c5\u8bdd",
                authorUid = "200",
                authorName = "other-publisher",
                translationGroup = targetGroup,
                publisherUid = null,
                publisherName = null,
                keepUnknownPublisher = false
            )
        )
        assertFalse(
            MangaTitleCleaner.matchesDirectoryConstraints(
                rawTitle = "\u3010\u5176\u4ed6\u6c49\u5316\u7ec4\u3011\u6c89\u6eba\u4e8e\u7f6a\u6076\u7684\u72af\u7f6a\u767e\u5408\u77ed\u7bc7\u96c6 \u7b2c3\u8bdd",
                authorUid = "100",
                authorName = "homura1014",
                translationGroup = targetGroup,
                publisherUid = null,
                publisherName = null,
                keepUnknownPublisher = true
            )
        )
    }

    @Test
    fun directoryConstraintsUsePublisherOnlyWhenConfigured() {
        val targetGroup = "\u63d0\u706f\u55b5\u6c49\u5316\u7ec4"

        assertTrue(
            MangaTitleCleaner.matchesDirectoryConstraints(
                rawTitle = "No explicit group title",
                authorUid = "100",
                authorName = "homura1014",
                translationGroup = targetGroup,
                publisherUid = "100",
                publisherName = null,
                keepUnknownPublisher = false
            )
        )
        assertFalse(
            MangaTitleCleaner.matchesDirectoryConstraints(
                rawTitle = "No explicit group title",
                authorUid = "200",
                authorName = "other-publisher",
                translationGroup = targetGroup,
                publisherUid = "100",
                publisherName = null,
                keepUnknownPublisher = false
            )
        )
        assertTrue(
            MangaTitleCleaner.matchesDirectoryConstraints(
                rawTitle = "No explicit group title",
                authorUid = "200",
                authorName = "other-publisher",
                translationGroup = null,
                publisherUid = null,
                publisherName = null,
                keepUnknownPublisher = false
            )
        )
    }
    @Test
    fun mobileSearchParserReturnsCompleteTitleInsteadOfHighlightOnly() {
        val html = """
            <html><body id="search">
              <ul class="threadlist">
                <li class="list">
                  <a href="forum.php?mod=viewthread&amp;tid=123">
                    <div class="threadlist_tit">后续任凭<em>想象</em>[やナい]把我弄乱吧</div>
                  </a>
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val parsed = MangaHtmlParser.parseListHtml(html)
        assertEquals(1, parsed.size)
        assertEquals("后续任凭想象[やナい]把我弄乱吧", parsed.single().rawTitle)
    }

    @Test
    fun pcSearchParserIsSupported() {
        val html = """
            <html><body class="pg_search">
              <div class="slst"><ul><li class="pbw">
                <h3 class="xs3">
                  <a href="forum.php?mod=viewthread&amp;tid=456">
                    【提灯喵汉化组】后续任凭<em>想象</em>[やナい]把我弄得乱七八糟吧！
                  </a>
                </h3>
              </li></ul></div>
            </body></html>
        """.trimIndent()

        val parsed = MangaHtmlParser.parseListHtml(html)
        assertEquals(1, parsed.size)
        assertEquals("456", parsed.single().tid)
        assertTrue(parsed.single().rawTitle.contains("把我弄得乱七八糟吧"))
    }

    @Test
    fun searchPaginationReadsPageNumbersFromLinks() {
        val html = """
            <div class="pg">
              <a href="search.php?mod=forum&amp;searchid=321&amp;page=2">2</a>
              <a class="nxt" href="search.php?mod=forum&amp;searchid=321&amp;page=8">下一页</a>
            </div>
        """.trimIndent()

        assertEquals("321", MangaHtmlParser.extractSearchId(html))
        assertEquals(8, MangaHtmlParser.extractTotalPages(html))
    }
}
