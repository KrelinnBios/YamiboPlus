package org.shirakawatyu.yamibo.novel.util.favorite

import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FavoriteUtilTest {
    @Test
    fun jsonToHashMapKeepsPinAnchorUrl() {
        val json = """
            {
              "forum.php?mod=viewthread&tid=123": {
                "title": "测试小说",
                "url": "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=123&mobile=2",
                "lastPage": 5,
                "lastView": 2,
                "type": 1,
                "pinAnchorUrl": "forum.php?mod=viewthread&tid=88"
              }
            }
        """.trimIndent()

        val result = FavoriteUtil.jsonToHashMap(json)
        val favorite = result["forum.php?mod=viewthread&tid=123"]

        assertNotNull(favorite)
        assertEquals("forum.php?mod=viewthread&tid=88", favorite?.pinAnchorUrl)
        assertEquals("forum.php?mod=viewthread&tid=123", favorite?.url)
    }

    @Test
    fun newFavoritesStayBelowPinnedFavorites() {
        val pinnedFirst = Favorite(
            title = "置顶一",
            url = "forum.php?mod=viewthread&tid=1",
            pinAnchorUrl = ""
        )
        val pinnedSecond = Favorite(
            title = "置顶二",
            url = "forum.php?mod=viewthread&tid=2",
            pinAnchorUrl = "forum.php?mod=viewthread&tid=9"
        )
        val normal = Favorite(
            title = "普通旧收藏",
            url = "forum.php?mod=viewthread&tid=3"
        )
        val newFavorite = Favorite(
            title = "新收藏",
            url = "forum.php?mod=viewthread&tid=4"
        )

        val result = FavoriteUtil.mergeNewFavoritesPreservingPins(
            oldMap = linkedMapOf(
                pinnedFirst.url to pinnedFirst,
                pinnedSecond.url to pinnedSecond,
                normal.url to normal
            ),
            newItems = listOf(newFavorite)
        )

        assertEquals(
            listOf(pinnedFirst.url, pinnedSecond.url, newFavorite.url, normal.url),
            result.keys.toList()
        )
    }

    @Test
    fun displayOrderAlwaysKeepsPinnedFavoritesFirst() {
        val normalNew = Favorite(
            title = "最新收藏",
            url = "forum.php?mod=viewthread&tid=10"
        )
        val pinned = Favorite(
            title = "置顶收藏",
            url = "forum.php?mod=viewthread&tid=11",
            pinAnchorUrl = "forum.php?mod=viewthread&tid=9"
        )
        val normalOld = Favorite(
            title = "旧收藏",
            url = "forum.php?mod=viewthread&tid=12"
        )

        val result = FavoriteUtil.orderPinnedFavoritesFirst(
            listOf(normalNew, pinned, normalOld)
        )

        assertEquals(listOf(pinned.url, normalNew.url, normalOld.url), result.map { it.url })
    }

    @Test
    fun decodeTitleRepairsRepeatedDiscuzEscaping() {
        assertEquals(
            "【姜姐姐 & Miss PM】月夜花園 01",
            FavoriteUtil.decodeTitle("【姜姐姐 &amp;amp; Miss PM】月夜花園 01")
        )
    }
}
