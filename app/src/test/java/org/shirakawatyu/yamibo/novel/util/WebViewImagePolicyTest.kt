package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewImagePolicyTest {
    @Test
    fun proxiesOnlyForumAttachments() {
        assertTrue(
            WebViewImagePolicy.shouldProxyForumAttachment(
                "https://bbs.yamibo.com/data/attachment/forum/202606/12/example.jpg"
            )
        )
        assertTrue(
            WebViewImagePolicy.shouldProxyForumAttachment(
                "https://bbs.yamibo.com/forum.php?mod=attachment&aid=123"
            )
        )
        assertFalse(
            WebViewImagePolicy.shouldProxyForumAttachment(
                "https://bbs.yamibo.com/data/attachment/portal/banner.jpg"
            )
        )
        assertFalse(
            WebViewImagePolicy.shouldProxyForumAttachment(
                "https://bbs.yamibo.com/data/attachment/profile/background.jpg"
            )
        )
    }
}
