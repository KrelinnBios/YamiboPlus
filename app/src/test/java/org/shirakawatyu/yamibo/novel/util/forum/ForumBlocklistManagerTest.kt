package org.shirakawatyu.yamibo.novel.util.forum

import com.alibaba.fastjson2.JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumBlocklistManagerTest {
    @Test
    fun parsesThreadAndPostInputs() {
        assertEquals(
            ForumBlockedItem(ForumBlockedItem.TYPE_THREAD, "546273", "主题 546273"),
            ForumBlocklistManager.parseInput("https://bbs.yamibo.com/thread-546273-1-1.html")
        )
        assertEquals(
            ForumBlockedItem(ForumBlockedItem.TYPE_THREAD, "546273", "主题 546273"),
            ForumBlocklistManager.parseInput("https://bbs.yamibo.com/forum.php?mod=viewthread&tid=546273")
        )
        assertEquals(
            ForumBlockedItem(ForumBlockedItem.TYPE_POST, "40983574", "楼层 40983574"),
            ForumBlocklistManager.parseInput("https://bbs.yamibo.com/forum.php?mod=redirect&pid=40983574")
        )
        assertEquals(
            ForumBlockedItem(ForumBlockedItem.TYPE_THREAD, "546273", "主题 546273"),
            ForumBlocklistManager.parseInput("546273")
        )
    }

    @Test
    fun rejectsAuthorFilterAndInvalidInput() {
        assertNull(ForumBlocklistManager.parseInput("forum.php?mod=viewthread&tid=546273&authorid=1"))
        assertNull(ForumBlocklistManager.parseInput("not a forum link"))
    }

    @Test
    fun blockedItemsRoundTripThroughStoredJson() {
        val expected = listOf(
            ForumBlockedItem(ForumBlockedItem.TYPE_THREAD, "546273", "测试主题"),
            ForumBlockedItem(ForumBlockedItem.TYPE_POST, "40983574", "测试楼层"),
            ForumBlockedItem(
                type = ForumBlockedItem.TYPE_USER,
                id = "489445",
                title = "425",
                authorUid = "489445",
                authorName = "425"
            )
        )
        val actual = JSON.parseArray(
            JSON.toJSONString(expected),
            ForumBlockedItem::class.java
        )
        assertEquals(expected, actual)
    }
}
