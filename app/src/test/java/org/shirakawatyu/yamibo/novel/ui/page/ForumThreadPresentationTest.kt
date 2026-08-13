package org.shirakawatyu.yamibo.novel.ui.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread

class ForumThreadPresentationTest {
    @Test
    fun stickyThreads_areCollapsedByDefault() {
        assertFalse(STICKY_THREADS_INITIAL_EXPANDED)
    }

    @Test
    fun groupForumThreads_separatesStickyThreadsWithoutChangingOrder() {
        val regular = thread(id = "1", displayOrder = 0)
        val sticky = thread(id = "2", displayOrder = 1)
        val anotherRegular = thread(id = "3", displayOrder = 0)

        val result = groupForumThreads(listOf(regular, sticky, anotherRegular))

        assertEquals(listOf(sticky), result.sticky)
        assertEquals(listOf(regular, anotherRegular), result.regular)
    }

    @Test
    fun toggleExpandedForumId_expandsTargetAndCollapsesCurrentTarget() {
        assertEquals("5", toggleExpandedForumId(null, "5"))
        assertEquals("7", toggleExpandedForumId("5", "7"))
        assertEquals(null, toggleExpandedForumId("5", "5"))
    }

    @Test
    fun forumThreadStats_containsViewAndReplyCounts() {
        assertEquals("30 查看 · 4 回复", forumThreadStats(thread(viewCount = 30, replyCount = 4)))
    }

    private fun thread(
        id: String = "100",
        displayOrder: Int = 0,
        viewCount: Int = 0,
        replyCount: Int = 0
    ) = ForumThread(
        id = id,
        subject = "测试主题",
        authorId = "7",
        authorName = "作者",
        createdAt = "今天",
        lastPostAt = "今天",
        lastPoster = "读者",
        replyCount = replyCount,
        viewCount = viewCount,
        displayOrder = displayOrder
    )
}
