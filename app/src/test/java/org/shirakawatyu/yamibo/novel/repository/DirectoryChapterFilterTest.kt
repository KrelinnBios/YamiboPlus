package org.shirakawatyu.yamibo.novel.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.MangaChapterItem

/**
 * 目录章节过滤与首楼链接目录识别的规则测试。
 */
class DirectoryChapterFilterTest {

    private fun chapter(
        tid: String,
        title: String,
        num: Float,
        authorUid: String? = null,
        authorName: String? = null
    ) = MangaChapterItem(
        tid = tid,
        rawTitle = title,
        chapterNum = num,
        url = "https://bbs.yamibo.com/thread-$tid-1-1.html",
        authorUid = authorUid,
        authorName = authorName
    )

    private fun filter(
        chapters: List<MangaChapterItem>,
        group: String?
    ) = DirectoryRepository.filterChaptersByDirectoryConstraints(
        chapters = chapters,
        translationGroup = group,
        publisherUid = null,
        publisherName = null,
        keepUnknownPublisher = true
    )

    @Test
    fun keepsAllWhenNoConstraints() {
        val chapters = listOf(
            chapter("100", "【A汉化组】某作品 第1话", 1f),
            chapter("200", "【B汉化组】某作品 第1话", 1f)
        )
        assertEquals(chapters, filter(chapters, null))
        assertEquals(chapters, filter(chapters, ""))
    }

    @Test
    fun dropsNavigationLinkPseudoChapters() {
        // 已经写进目录的"上一话/下一话"导航链接伪章节要被过滤自愈，无约束时也一样
        val chapters = listOf(
            chapter("100", "【A汉化组】某作品 第1话", 1f),
            chapter("101", "上一话", 1f),
            chapter("102", "【A汉化组】下一话", 1f)
        )
        assertEquals(listOf("100"), filter(chapters, null).map { it.tid })
        assertEquals(listOf("100"), filter(chapters, "A汉化组").map { it.tid })
    }

    @Test
    fun dropsOtherGroupsInsteadOfGapFilling() {
        val chapters = listOf(
            chapter("100", "【A汉化组】某作品 第1话", 1f),
            chapter("101", "【A汉化组】某作品 第2话", 2f),
            chapter("200", "【B汉化组】某作品 第2话", 2f),
            chapter("201", "【B汉化组】某作品 第3话", 3f)
        )
        assertEquals(
            listOf("100", "101"),
            filter(chapters, "A汉化组").map { it.tid }
        )
    }

    @Test
    fun dropsOtherGroupsEvenWhenOnlyTheyHaveMissingNumber() {
        val chapters = listOf(
            chapter("100", "【A汉化组】某作品 第2话", 2f),
            chapter("300", "【C汉化组】某作品 第3话", 3f),
            chapter("200", "【B汉化组】某作品 第3话", 3f)
        )
        assertEquals(
            listOf("100"),
            filter(chapters, "A汉化组").map { it.tid }
        )
    }

    @Test
    fun dropsUnnumberedChaptersFromOtherGroups() {
        val chapters = listOf(
            chapter("100", "【A汉化组】某作品 第1话", 1f),
            chapter("200", "【B汉化组】某作品 单篇", 0f)
        )
        assertEquals(
            listOf("100"),
            filter(chapters, "A汉化组").map { it.tid }
        )
    }

    @Test
    fun publisherConstraintStillKeepsUnknownAuthors() {
        // 只设发布者约束时：作者未知的章节按 keepUnknownPublisher 保留（旧行为不变）
        val chapters = listOf(
            chapter("100", "【个人汉化】某作品 第1话", 1f, authorUid = "42"),
            chapter("200", "【个人汉化】某作品 第2话", 2f)
        )
        val kept = DirectoryRepository.filterChaptersByDirectoryConstraints(
            chapters = chapters,
            translationGroup = null,
            publisherUid = "42",
            publisherName = null,
            keepUnknownPublisher = true
        )
        assertEquals(listOf("100", "200"), kept.map { it.tid })
    }

    @Test
    fun detectsNumberedCrossWorkLinksAsAuthoritativePageDirectory() {
        val pageLinks = listOf(
            chapter("558409", "1、波虹摇滚！/ぼにじ・ざ・ろっく！", 1f),
            chapter("558549", "2、去你家/君の家まで", 2f),
            chapter("545688", "3、我的吉他英雄/私のギターヒーロー", 3f),
            chapter("558372", "4、虹的彼端/虹の向こう", 4f)
        )
        assertTrue(
            DirectoryRepository.hasAuthoritativeCrossWorkPageLinks(
                currentTid = "558549",
                cleanBookName = "去你家/君の家まで（波虹）",
                pageLinks = pageLinks
            )
        )
    }

    @Test
    fun doesNotTreatSameWorkLinksAsAuthoritativeCrossWorkDirectory() {
        val pageLinks = listOf(
            chapter("569540", "(C102)[まぜもの(いちみ)]ぼ喜多・が・ろっく1", 1f),
            chapter("573142", "【大友同好會】( ぼっち・ざ・おんりー!#2)[まぜもの(いちみ)]ぼ喜多・が・ろっく2", 2f)
        )
        assertFalse(
            DirectoryRepository.hasAuthoritativeCrossWorkPageLinks(
                currentTid = "573142",
                cleanBookName = "ぼ喜多・が・ろっく",
                pageLinks = pageLinks
            )
        )
    }

    @Test
    fun doesNotTreatNumberedSameWorkLinksAsAuthoritativeCrossWorkDirectory() {
        val pageLinks = listOf(
            chapter("569540", "1、ぼ喜多・が・ろっく1", 1f),
            chapter("573142", "2、ぼ喜多・が・ろっく2", 2f)
        )
        assertFalse(
            DirectoryRepository.hasAuthoritativeCrossWorkPageLinks(
                currentTid = "573142",
                cleanBookName = "ぼ喜多・が・ろっく",
                pageLinks = pageLinks
            )
        )
    }

    @Test
    fun ignoresUnnumberedCrossWorkLinks() {
        val pageLinks = listOf(
            chapter("558409", "波虹摇滚！/ぼにじ・ざ・ろっく！", 1f),
            chapter("545688", "我的吉他英雄/私のギターヒーロー", 3f)
        )
        assertFalse(
            DirectoryRepository.hasAuthoritativeCrossWorkPageLinks(
                currentTid = "558549",
                cleanBookName = "去你家/君の家まで（波虹）",
                pageLinks = pageLinks
            )
        )
    }

    @Test
    fun explicitPageDirectoryRestoresSourceOrderAndKeepsNewerCachedChapter() {
        val gatheredFromPage = listOf(
            chapter("561605", "1卷彩页", 0f),
            chapter("561650", "1话", 1f),
            chapter("566000", "20话", 20f),
            chapter("572043", "29话", 29f),
            chapter("573457", "3卷彩页", 0f),
            chapter("573500", "31话", 31f),
            chapter("574097", "55话", 55f)
        )
        val cachedWrongTidOrder = listOf(
            chapter("573500", "31话", 31f),
            chapter("561605", "1卷彩页", 0f),
            chapter("561650", "1话", 1f),
            chapter("566000", "20话", 20f),
            chapter("570000", "21话", 0f),
            chapter("572043", "29话", 29f),
            chapter("573457", "3卷彩页", 0f),
            chapter("573490", "30话", 0f),
            chapter("574097", "55话", 55f),
            chapter("574098", "56话", 56f)
        )

        val merged = DirectoryRepository.mergeExplicitOrderedPageLinks(
            gatheredFromPage = gatheredFromPage,
            cachedChapters = cachedWrongTidOrder
        )

        assertEquals(
            listOf(
                "1卷彩页", "1话", "20话", "21话", "29话", "3卷彩页",
                "30话", "31话", "55话", "56话"
            ),
            merged.map { it.rawTitle }
        )
    }
}
