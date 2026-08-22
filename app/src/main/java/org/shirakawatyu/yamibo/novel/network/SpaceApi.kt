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

interface SpaceApi {
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @GET("/home.php")
    suspend fun getSpacePage(
        @Query("mod") mod: String = "space",
        @Query("uid") uid: String? = null,
        @Query("do") doParam: String,
        @Query("view") view: String? = null,
        @Query("type") type: String? = null,
        @Query("classid") classId: String? = null,
        @Query("fuid") friendUid: String? = null,
        @Query("page") page: Int? = null,
        @Query("perpage") perPage: Int = 20,
        @Query("mobile") mobile: String = "no"
    ): ResponseBody

    /** 加载「下一页」等由页面给出的完整 URL（仍走统一 Cookie/UA 拦截器）。 */
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    @GET
    suspend fun getPageByUrl(
        @Url url: String,
        @Header("Referer") referer: String? = null
    ): ResponseBody

    /** 发送私信：Discuz 私信表单提交（spacecp&ac=pm&op=send）。 */
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
    @FormUrlEncoded
    @POST("/home.php")
    suspend fun sendPrivateMessage(
        @Query("mod") mod: String = "spacecp",
        @Query("ac") ac: String = "pm",
        @Query("op") op: String = "send",
        @Query("pmid") pmid: String = "",
        @Query("daterange") daterange: String = "0",
        @Query("pmsubmit") pmsubmit: String = "yes",
        @Query("mobile") mobile: String = "2",
        @Field("formhash") formHash: String,
        @Field("touid") touid: String,
        @Field("message") message: String,
        @Header("Referer") referer: String? = null
    ): ResponseBody
}
