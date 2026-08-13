package org.shirakawatyu.yamibo.novel.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ForumApi {
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/index.php")
    suspend fun getForumHome(
        @Query("mobile") mobile: String = "2"
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/forum.php")
    suspend fun getForumDisplayPage(
        @Query("mod") mod: String = "forumdisplay",
        @Query("fid") forumId: String,
        @Query("mobile") mobile: String = "2"
    ): ResponseBody

    @GET("/api/mobile/index.php?module=forumindex&version=4")
    suspend fun getForumIndex(): ResponseBody

    @GET("/api/mobile/index.php?module=forumdisplay&version=4")
    suspend fun getForumThreads(
        @Query("fid") forumId: String,
        @Query("page") page: Int = 1,
        @Query("tpp") pageSize: Int = 20,
        @Query("orderby") orderBy: String? = null,
        @Query("filter") filter: String? = null,
        @Query("typeid") typeId: String? = null
    ): ResponseBody

    @GET("/api/mobile/index.php?module=viewthread&version=4")
    suspend fun getThreadPosts(
        @Query("tid") threadId: String,
        @Query("page") page: Int = 1,
        @Query("authorid") authorId: String? = null
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/forum.php")
    suspend fun getThreadPage(
        @Query("mod") mod: String = "viewthread",
        @Query("tid") threadId: String,
        @Query("page") page: Int = 1,
        @Query("mobile") mobile: String = "2"
    ): ResponseBody
}
