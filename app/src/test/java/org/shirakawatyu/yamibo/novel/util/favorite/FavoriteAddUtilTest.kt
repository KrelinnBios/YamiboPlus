package org.shirakawatyu.yamibo.novel.util.favorite

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteAddUtilTest {

    @Test
    fun htmlSuccessTextIsAccepted() {
        val html = "<div class=\"alert_info\">收藏成功，点击返回</div>"

        assertEquals(true, FavoriteAddUtil.parseAddFavoriteResponse(html))
    }

    @Test
    fun englishSuccessTextIsAccepted() {
        assertEquals(true, FavoriteAddUtil.parseAddFavoriteResponse("favorite succeed"))
    }

    @Test
    fun jsonFavoriteFlagIsAccepted() {
        val json = """{"Variables":{"favorite":1},"Message":{"messageval":"favorite_succeed"}}"""

        assertEquals(true, FavoriteAddUtil.parseAddFavoriteResponse(json))
    }

    @Test
    fun jsonFailureFlagIsRejected() {
        val json = """{"Variables":{"favorite":0},"Message":{"messageval":"favorite_fail"}}"""

        assertEquals(false, FavoriteAddUtil.parseAddFavoriteResponse(json))
    }

    @Test
    fun xmlFavoriteFlagIsAccepted() {
        val xml = """<?xml version="1.0" encoding="utf-8"?><root><Variables><favorite>1</favorite></Variables></root>"""

        assertEquals(true, FavoriteAddUtil.parseAddFavoriteResponse(xml))
    }

    @Test
    fun xmlFailureFlagIsRejected() {
        val xml = """<?xml version="1.0" encoding="utf-8"?><root><Variables><favorite>0</favorite></Variables></root>"""

        assertEquals(false, FavoriteAddUtil.parseAddFavoriteResponse(xml))
    }

    @Test
    fun blankBodyIsRejected() {
        assertEquals(false, FavoriteAddUtil.parseAddFavoriteResponse(null))
        assertEquals(false, FavoriteAddUtil.parseAddFavoriteResponse(""))
    }
}
