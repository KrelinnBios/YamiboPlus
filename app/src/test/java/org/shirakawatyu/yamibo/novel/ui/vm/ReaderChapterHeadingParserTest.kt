package org.shirakawatyu.yamibo.novel.ui.vm

import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.util.reader.HTMLUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChapterHeadingParserTest {
    @Test
    fun linkedFirstFloorDirectoryDoesNotCreateDuplicateChapterHeadings() {
        val node = Jsoup.parseBodyFragment(
            """
            <div class="message" data-post-id="40946503">
              <div><a href="forum.php?mod=viewthread&amp;tid=546124">7.14更新了</a></div>
              <div>—————目录—————</div>
              <div><strong>第一章</strong></div>
              <div><a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=544422&amp;pid=40946515">『潮味未至』 其一</a></div>
              <div><a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=544422&amp;pid=40946523">『潮味未至』 其二</a></div>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        assertTrue(isReaderLinkedChapterDirectory(node, "544422"))

        val marked = markReaderEmbeddedChapterHeadings(node, "544422")
        assertFalse(marked.html().contains("YAMIBO_CHAPTER_START"))
    }

    @Test
    fun directoryLabelAndSinglePostLinkAreNotEnoughToSuppressHeadings() {
        val node = Jsoup.parseBodyFragment(
            """
            <div class="message">
              <div>返回目录</div>
              <div><strong>第一章</strong></div>
              <a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=544422&amp;pid=40946515">查看本楼</a>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        assertFalse(isReaderLinkedChapterDirectory(node, "544422"))
        assertTrue(
            markReaderEmbeddedChapterHeadings(node, "544422")
                .html()
                .contains("YAMIBO_CHAPTER_START")
        )
    }

    @Test
    fun extractReaderTextChapterHeading_stripsMarkdownHeadingMarker() {
        assertEquals("第一话", extractReaderTextChapterHeading("# 第一话"))
        assertEquals(
            "番外篇 蟑螂 · 任务",
            extractReaderTextChapterHeading("＃ 番外篇  蟑螂 · 任务")
        )
        assertNull(extractReaderTextChapterHeading("我一直憧憬着莉莉。"))
    }

    @Test
    fun extractReaderChapterHeadingAfterDivider_skipsAuthorNoteAndFindsChapterTitle() {
        val node = Jsoup.parse(
            """
            <div class="message">
              谢谢大家支持<br>
              在大家的鼓励下马上就着手了第二章<br>
              <hr class="l">
              <font size="3"><br>
                第二章　从幼小的开始攻击<br>
                <br>
                哈…哈…好累…到底走了多久。<br>
              </font>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        assertEquals(
            "第二章　从幼小的开始攻击",
            extractReaderChapterHeadingAfterDivider(node)
        )
    }

    @Test
    fun extractReaderChapterHeadingAfterDivider_doesNotUseAuthorNoteWithoutChapter() {
        val node = Jsoup.parse(
            """
            <div class="message">
              后篇来了<br>
              <hr class="l">
              <font size="3">AI 生成的角色图片，仅供参考。</font>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        assertNull(extractReaderChapterHeadingAfterDivider(node))
    }

    @Test
    fun extractReaderStructuralHeading_skipsLongContainerAndFindsNestedTitle() {
        val body = "正文段落。".repeat(30)
        val node = Jsoup.parse(
            """
            <div class="message">
              <div align="center">
                <font size="4"># 最强的蛋包饭</font>
                <font size="4">$body</font>
              </div>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        assertEquals("最强的蛋包饭", extractReaderStructuralHeading(node))
    }

    @Test
    fun extractReaderStructuralHeading_doesNotUseBodySentenceAsTitle() {
        val node = Jsoup.parse(
            """<div class="message"><font size="4">我一直憧憬着莉莉。</font></div>"""
        ).selectFirst(".message")!!

        assertNull(extractReaderStructuralHeading(node))
    }

    @Test
    fun extractReaderDateSubHeading_doesNotTreatSentenceContainingDateAsHeading() {
        val node = Jsoup.parse(
            """
            <div class="message">
              <div align="center">
                <font size="1"><i>值得一提的是，在2022年5月15日开办的</i></font>
                <a href="https://example.com"><strong>日本第53届星云奖投票活动</strong></a>
                <font size="1"><i>中，作品列入最终候选名单</i></font>
              </div>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        assertNull(extractReaderDateSubHeading(node))
    }

    @Test
    fun extractReaderDateSubHeading_acceptsStandaloneDateHeadings() {
        val currentDateHeading = Jsoup.parse(
            """<div class="message"><div align="center">现在 - 2044 年 1 月</div></div>"""
        ).selectFirst(".message")!!
        val relativeDateHeading = Jsoup.parse(
            """<div class="message"><font size="4">三年前 - 2041 年 5 月</font></div>"""
        ).selectFirst(".message")!!

        assertEquals("现在 - 2044 年 1 月", extractReaderDateSubHeading(currentDateHeading))
        assertEquals("三年前 - 2041 年 5 月", extractReaderDateSubHeading(relativeDateHeading))
    }

    @Test
    fun linkedPurchaseTextIsNotEligibleForLeadingChapterTitle() {
        val directLink = Jsoup.parse(
            """
            <a href="https://www.amazon.co.jp/dp/B0CG1D7R1P">
              <font size="4"><strong>点此日亚购买</strong></font>
            </a>
            """.trimIndent()
        ).selectFirst("a")!!
        val linkedWrapper = Jsoup.parse(
            """<font size="4"><a href="https://example.com">外部链接</a></font>"""
        ).selectFirst("font")!!
        val plainTitle = Jsoup.parse(
            """<strong><font size="3">夏梦4</font></strong>"""
        ).selectFirst("strong")!!

        assertTrue(isReaderLinkedTitleElement(directLink))
        assertTrue(isReaderLinkedTitleElement(linkedWrapper))
        assertFalse(isReaderLinkedTitleElement(plainTitle))
    }

    @Test
    fun proseLineEndingWithContinuationPunctuationIsNotEligibleForLeadingTitle() {
        assertTrue(
            endsWithReaderTitleContinuationPunctuation(
                "处在人生的悬崖边的奔三声优『吉冈奏绘』、"
            )
        )
        assertTrue(endsWithReaderTitleContinuationPunctuation("故事从这里开始，"))
        assertFalse(endsWithReaderTitleContinuationPunctuation("夏梦4"))
    }

    @Test
    fun splitReaderNumberedChapterSegments_splitsEmbeddedAndLeadingChapterNumbers() {
        val embedded = splitReaderNumberedChapterSegments(
            "卖身之事\n著：小野美由纪\n1\n当我得到这闪耀着银色光芒的新身体时……"
        )
        val leading = splitReaderNumberedChapterSegments(
            "2 一边这样想着，我在位于贫民窟15公里外的市区办公窗口办理完手续。"
        )

        assertEquals(listOf(null, "1"), embedded.map { it.title })
        assertEquals(listOf("2"), leading.map { it.title })
        assertEquals("2 一边这样想着，我在位于贫民窟15公里外的市区办公窗口办理完手续。", leading.single().text)
        assertEquals(true, containsReaderChapterStart(embedded))
        assertEquals(true, containsReaderChapterStart(leading))
        assertEquals(
            false,
            containsReaderChapterStart(
                splitReaderNumberedChapterSegments("没有数字章节的普通正文")
            )
        )
    }

    @Test
    fun splitReaderEmbeddedChapterSegments_splitsMultipleStyledHeadingsInOnePost() {
        val node = Jsoup.parse(
            """
            <div class="message">
              <div>科幻部分尽是胡诌，烦请见谅</div>
              <strong><font size="4"><strong>一、卖身契</strong></font></strong>
              <div>第一章正文。</div>
              <strong><font size="4"><strong>二、要求</strong></font></strong>
              <div>第二章正文。</div>
              <strong><font size="4"><strong>十、永生之问</strong><strong>2</strong></font></strong>
              <div>带嵌套尾号的章节正文。</div>
              <strong><font size="4"><strong>第八其零：乘龙载客的女孩</strong></font></strong>
              <div>子章正文。</div>
              <strong>第一章 尾声</strong>
              <div>尾声正文。</div>
            </div>
            """.trimIndent()
        ).selectFirst(".message")!!

        val markedText = HTMLUtil.toText(markReaderEmbeddedChapterHeadings(node).html())
        val segments = splitReaderEmbeddedChapterSegments(markedText)

        assertEquals(
            listOf(
                "一、卖身契",
                "二、要求",
                "十、永生之问2",
                "第八其零：乘龙载客的女孩",
                "第一章 尾声"
            ),
            segments.mapNotNull { it.title }
        )
        assertEquals(true, segments.first { it.title == "二、要求" }.text.contains("第二章正文"))
    }
}
