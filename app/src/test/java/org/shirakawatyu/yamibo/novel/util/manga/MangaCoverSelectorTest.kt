package org.shirakawatyu.yamibo.novel.util.manga

import com.alibaba.fastjson2.JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaCoverSelectorTest {
    @Test
    fun firstCoverUrl_skipsDiscuzSmiliesBeforeContentImages() {
        val message = """
            <div>
              <img src="static/image/smiley/default/dizzy.gif" smilieid="924" border="0" alt="">
              <img src="data/attachment/forum/202606/thumb.jpg"
                   file="data/attachment/forum/202606/cover.jpg">
            </div>
        """.trimIndent()

        assertEquals(
            "https://bbs.yamibo.com/data/attachment/forum/202606/cover.jpg",
            MangaCoverSelector.firstCoverUrl(listOf(message))
        )
    }

    @Test
    fun firstCoverUrl_keepsSearchingLaterPostsWhenEarlierImagesAreStaticAssets() {
        val staticOnly = """
            <img src="static/image/common/online_member.gif">
        """.trimIndent()
        val content = """
            <img zoomfile="data/attachment/forum/202606/page_001.png"
                 src="static/image/common/none.gif">
        """.trimIndent()

        assertEquals(
            "https://bbs.yamibo.com/data/attachment/forum/202606/page_001.png",
            MangaCoverSelector.firstCoverUrl(listOf(staticOnly, content))
        )
    }

    @Test
    fun firstCoverUrl_usesApiAttachmentWhenMessageHasNoImages() {
        val attachmentUrl = MangaCoverSelector.attachmentImageUrl(
            urlPrefix = "data/attachment/forum/",
            attachmentPath = "202606/23/142457vx7wpmpw5225g2gg.jpg"
        )

        assertEquals(
            "https://bbs.yamibo.com/data/attachment/forum/202606/23/142457vx7wpmpw5225g2gg.jpg",
            attachmentUrl
        )
        assertEquals(
            "https://bbs.yamibo.com/data/attachment/forum/202606/23/142457vx7wpmpw5225g2gg.jpg",
            MangaCoverSelector.firstCoverUrl(
                messages = listOf("<p>message without inline images</p>"),
                attachmentUrls = listOfNotNull(attachmentUrl)
            )
        )
    }

    @Test
    fun firstCoverUrl_ignoresInlineAndBlobImages() {
        val message = """
            <img src="data:image/png;base64,AAAA">
            <img src="blob:https://bbs.yamibo.com/cover">
        """.trimIndent()

        assertNull(MangaCoverSelector.firstCoverUrl(listOf(message)))
    }

    @Test
    fun firstCoverUrl_readsLazyLoadedImageAttributes() {
        val message = """
            <img src="static/image/common/none.gif"
                 data-original="data/attachment/forum/202608/cover.webp">
        """.trimIndent()

        assertEquals(
            "https://bbs.yamibo.com/data/attachment/forum/202608/cover.webp",
            MangaCoverSelector.firstCoverUrl(listOf(message))
        )
    }

    @Test
    fun attachmentImageUrls_supportsAttachListArrays() {
        val attachList = JSON.parseArray(
            """
            [
              {
                "aid": "123",
                "url": "data/attachment/forum",
                "attachment": "202608/cover.png",
                "isimage": "1"
              }
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf("https://bbs.yamibo.com/data/attachment/forum/202608/cover.png"),
            MangaCoverSelector.attachmentImageUrls(attachList)
        )
    }

    @Test
    fun attachmentImageUrls_fallsBackToAttachmentEndpointForImageWithoutPath() {
        val attachments = JSON.parseObject(
            """
            {
              "456": {
                "aid": "456",
                "filename": "cover.jpg",
                "isimage": "1"
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("https://bbs.yamibo.com/forum.php?mod=attachment&aid=456"),
            MangaCoverSelector.attachmentImageUrls(attachments)
        )
    }
}
