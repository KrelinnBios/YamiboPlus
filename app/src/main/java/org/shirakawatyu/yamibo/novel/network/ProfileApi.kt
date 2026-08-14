package org.shirakawatyu.yamibo.novel.network

import okhttp3.ResponseBody
import retrofit2.http.GET

interface ProfileApi {
    @GET("/api/mobile/index.php?module=profile&version=4")
    suspend fun getUserProfile(): ResponseBody

    @GET("/home.php?mod=space&do=profile&mycenter=1&mobile=2")
    suspend fun getUserProfileHtml(): ResponseBody
}
