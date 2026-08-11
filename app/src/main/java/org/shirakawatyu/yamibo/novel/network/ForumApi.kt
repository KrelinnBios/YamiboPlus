package org.shirakawatyu.yamibo.novel.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface ForumApi {
    @GET("/api/mobile/index.php?module=forumindex&version=4")
    suspend fun getForumIndex(): ResponseBody

    @GET("/api/mobile/index.php?module=forumdisplay&version=4")
    suspend fun getForumThreads(
        @Query("fid") forumId: String,
        @Query("page") page: Int = 1,
        @Query("tpp") pageSize: Int = 20
    ): ResponseBody

    @GET("/api/mobile/index.php?module=viewthread&version=4")
    suspend fun getThreadPosts(
        @Query("tid") threadId: String,
        @Query("page") page: Int = 1
    ): ResponseBody
}
