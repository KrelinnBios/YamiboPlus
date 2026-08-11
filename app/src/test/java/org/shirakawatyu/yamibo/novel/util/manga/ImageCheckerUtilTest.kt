package org.shirakawatyu.yamibo.novel.util.manga

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertSame
import org.junit.Test

class ImageCheckerUtilTest {
    @Test
    fun imageRedirect_isLeftForOkHttpToFollow() {
        val request = Request.Builder()
            .url("https://bbs.yamibo.com/data/attachment/forum/image.jpg")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(302)
            .message("Found")
            .header("Location", request.url.toString())
            .header("Content-Type", "text/html")
            .body("".toResponseBody("text/html".toMediaType()))
            .build()

        assertSame(
            response,
            ImageCheckerUtil.interceptAndCheckImageStream(response, request.url.toString())
        )
    }
}
