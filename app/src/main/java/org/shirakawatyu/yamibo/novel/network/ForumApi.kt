package org.shirakawatyu.yamibo.novel.network

import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface ForumApi {
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/forum.php")
    suspend fun getForumHome(
        @Query("showmobile") showMobile: String = "yes",
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

    @GET("/api/mobile/index.php?module=myfavforum&version=3")
    suspend fun getFavoriteForums(): ResponseBody

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

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/forum.php")
    suspend fun getViewRatings(
        @Query("mod") mod: String = "misc",
        @Query("action") action: String = "viewratings",
        @Query("tid") threadId: String,
        @Query("pid") postId: String,
        @Query("mobile") mobile: String = "2",
        @Query("inajax") inAjax: String = "1"
    ): ResponseBody

    /**
     * “查看全部评分”桌面版弹窗（返回带积分/用户名/时间/理由四列的完整列表）。
     * 不走 mobile=2，与网页端 AJAX 弹窗一致，避免只拿到预览条目。
     */
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/forum.php")
    suspend fun getAllRatings(
        @Query("mod") mod: String = "misc",
        @Query("action") action: String = "viewratings",
        @Query("tid") threadId: String,
        @Query("pid") postId: String,
        @Query("infloat") infloat: String = "yes",
        @Query("handlekey") handleKey: String = "viewratings",
        @Query("inajax") inAjax: String = "1",
        @Query("ajaxtarget") ajaxTarget: String = "fwin_content_viewratings"
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @GET("/forum.php")
    suspend fun getRatePopout(
        @Query("mod") mod: String = "misc",
        @Query("action") action: String = "rate",
        @Query("tid") threadId: String,
        @Query("pid") postId: String,
        @Query("mobile") mobile: String = "2",
        @Query("infloat") infloat: String = "yes",
        @Query("handlekey") handleKey: String = "rate",
        @Query("inajax") inAjax: String = "1"
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @FormUrlEncoded
    @POST("/forum.php")
    suspend fun votePoll(
        @Query("mod") mod: String = "misc",
        @Query("action") action: String = "votepoll",
        @Query("fid") forumId: String,
        @Query("tid") threadId: String,
        @Field("formhash") formHash: String,
        @Field("pollanswers[]") optionIds: List<String>,
        @Field("pollsubmit") pollSubmit: String = "yes"
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @FormUrlEncoded
    @POST("/forum.php")
    suspend fun submitRate(
        @Query("mod") mod: String = "misc",
        @Query("action") action: String = "rate",
        @Query("ratesubmit") rateSubmit: String = "yes",
        @Query("infloat") infloat: String = "yes",
        @Query("handlekey") handleKey: String = "rateform",
        @Query("inajax") inAjax: String = "1",
        @Field("formhash") formHash: String,
        @Field("tid") threadId: String,
        @Field("pid") postId: String,
        @Field("referer") referer: String = "",
        @Field("handlekey") handleKeyBody: String = "rate",
        @Field("score1") score: String,
        @Field("reason") reason: String,
        @Field("sendreasonpm") sendReasonPm: String? = null
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @FormUrlEncoded
    @POST("/forum.php")
    suspend fun submitReply(
        @Query("mod") mod: String = "post",
        @Query("action") action: String = "reply",
        @Query("tid") threadId: String,
        @Query("repquote") repquote: String? = null,
        @Query("replysubmit") replySubmit: String = "yes",
        @Query("infloat") infloat: String = "yes",
        @Query("inajax") inAjax: String = "1",
        @Query("handlekey") handleKey: String = "fastpost",
        @Query("fid") forumIdQuery: String,
        @Field("fid") forumId: String,
        @Field("formhash") formHash: String,
        @Field("handlekey") handleKeyBody: String = "fastpost",
        @Field("posttime") postTime: String? = null,
        @Field("usesig") useSig: String = "1",
        @Field("subject") subject: String = "",
        @Field("message") message: String,
        @Header("Referer") referer: String
    ): ResponseBody

    /**
     * Discuz 移动 API 回复。返回 JSON，成功时 Message.messageval 为
     * `post_reply_succeed`，失败时携带明确的中文提示，判定比表单提交可靠。
     * 注意：该接口不支持 repquote，引用楼层时需把引用 BBCode 拼进正文；
     * `fid` 为必填字段，缺少时服务端可能误报成功但实际未写入楼层。
     */
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @FormUrlEncoded
    @POST("/api/mobile/index.php?module=sendreply&version=4")
    suspend fun sendReplyMobile(
        @Field("fid") forumId: String,
        @Field("tid") threadId: String,
        @Field("formhash") formHash: String,
        @Field("subject") subject: String = "",
        @Field("message") message: String,
        @Field("replysubmit") replySubmit: String = "yes",
        @Field("usesig") useSig: String = "1",
        @Header("Referer") referer: String
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @FormUrlEncoded
    @POST("/forum.php")
    suspend fun submitComment(
        @Query("mod") mod: String = "post",
        @Query("action") action: String = "reply",
        @Query("comment") comment: String = "yes",
        @Query("tid") threadId: String,
        @Query("pid") postId: String,
        @Query("extra") extra: String = "",
        @Query("page") page: Int = 1,
        @Query("commentsubmit") commentSubmit: String = "yes",
        @Query("infloat") infloat: String = "yes",
        @Query("inajax") inAjax: String = "1",
        @Query("handlekey") handleKey: String = "commentform",
        @Field("formhash") formHash: String,
        @Field("handlekey") handleKeyBody: String = "",
        @Field("message") message: String
    ): ResponseBody
}
