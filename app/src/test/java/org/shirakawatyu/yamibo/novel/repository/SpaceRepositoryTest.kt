package org.shirakawatyu.yamibo.novel.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem

class SpaceRepositoryTest {
    @Test
    fun mergedRepliesAndCommentsAreLimitedToTwentyItems() {
        val replies = (1..20).map { index -> item("回复", index, "2026-8-24 03:${index.toString().padStart(2, '0')}") }
        val comments = (21..30).map { index -> item("点评", index, "2026-8-23 03:00") }

        assertEquals(20, mergeUserReplyItems(replies, comments).size)
    }

    private fun item(type: String, index: Int, time: String) =
        SpaceListItem.UserThread(
            tid = index.toString(),
            title = "title-$index",
            time = time,
            forumName = "forum",
            viewCount = "0",
            replyCount = "0",
            isClosed = false,
            url = "https://bbs.yamibo.com/thread-$index-1-1.html",
            entryType = type,
            postId = index.toString()
        )
}
