package org.shirakawatyu.yamibo.novel.network

import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MangaApi {
    @GET("/api/mobile/index.php?module=forumdisplay&version=4")
    suspend fun getForumDisplay(
        @Query("fid") fid: String,
        @Query("page") page: Int = 1
    ): ResponseBody

    // 获取Tag列表页
    // 设置mobile=no并伪装PC端User-Agent
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @GET("/misc.php?mod=tag&type=thread&mobile=no")
    suspend fun getTagPageHtml(
        @Query("id") tagId: String,
        @Query("page") page: Int = 1,
        @Header("Cookie") cookie: String? = null
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @FormUrlEncoded
    @POST("/search.php?mod=forum&mobile=no")
    suspend fun searchForum(
        @Field("formhash") formHash: String,
        @Field("srchfid[]") fids: List<String>,
        @Field("srchtxt") keyword: String,
        @Field("srchtype") type: String = "title",
        @Field("searchsubmit") searchSubmit: String = "yes",
        @Header("Cookie") cookie: String? = null
    ): ResponseBody

    // 用于搜索结果的后续翻页
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @GET("/search.php?mod=forum&orderby=dateline&ascdesc=desc&searchsubmit=yes&mobile=no")
    suspend fun searchForumPage(
        @Query("searchid") searchid: String,
        @Query("page") page: Int,
        @Header("Cookie") cookie: String? = null
    ): ResponseBody
    // 获取帖子详情
    @GET("/api/mobile/index.php?module=viewthread&version=4")
    suspend fun getThreadDetailApi(
        @Query("tid") tid: String,
        @Query("page") page: Int = 1
    ): ResponseBody

    // 获取帖子 PC 版 HTML（SEO URL 格式，含 #threadindex 目录，移动版会去除）
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @GET("/thread-{tid}-1-1.html")
    suspend fun getThreadPcHtml(
        @Path("tid") tid: String
    ): ResponseBody

    // 获取"只看楼主"过滤后的帖子 HTML（PC 版，用 forum.php 格式以便加 authorid）
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @GET("/forum.php")
    suspend fun getThreadHtmlByAuthor(
        @Query("mod") mod: String = "viewthread",
        @Query("tid") tid: String,
        @Query("authorid") authorId: String,
        @Query("page") page: Int = 1
    ): ResponseBody
}
