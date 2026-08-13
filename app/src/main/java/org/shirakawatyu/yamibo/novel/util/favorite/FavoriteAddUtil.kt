package org.shirakawatyu.yamibo.novel.util.favorite

import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.FavoriteApi

object FavoriteAddUtil {

    /**
     * 添加远端收藏（论坛帖子）
     * @param tid 帖子 ID
     * @return 添加是否成功
     */
    suspend fun addThreadFavorite(tid: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val api = YamiboRetrofit.getInstance().create(FavoriteApi::class.java)
                var formHash: String? = null
                val profileResponse = api.getFormHash().execute()
                val json = profileResponse.body()?.string() ?: ""
                try {
                    formHash = JSON.parseObject(json)?.getJSONObject("Variables")?.getString("formhash")
                } catch (_: Exception) { }
                if (formHash.isNullOrEmpty()) return@withContext false

                val response = api.addFavorite(formhash = formHash, id = tid).execute()

                val responseBody = if (response.isSuccessful) {
                    response.body()?.string()
                } else {
                    response.errorBody()?.string()
                }

                response.isSuccessful && parseAddFavoriteResponse(responseBody)
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun addForumFavorite(fid: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val api = YamiboRetrofit.getInstance().create(FavoriteApi::class.java)
                val profileResponse = api.getFormHash().execute()
                val json = profileResponse.body()?.string() ?: ""
                val formHash = JSON.parseObject(json)
                    ?.getJSONObject("Variables")
                    ?.getString("formhash")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withContext false
                val response = api.addForumFavorite(formHash, fid).execute()
                val body = if (response.isSuccessful) {
                    response.body()?.string()
                } else {
                    response.errorBody()?.string()
                }
                response.isSuccessful && parseAddFavoriteResponse(body)
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 解析添加收藏接口的响应。
     *
     * Discuz 不同入口返回的格式不一样：网页模板返回 HTML（含"收藏成功"），
     * 手机模板返回 XML（&lt;favorite&gt;1&lt;/favorite&gt;），接口可能返回 JSON
     * （Variables.favorite == 1），三种格式任一命中即视为成功。
     */
    internal fun parseAddFavoriteResponse(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        // 网页模板：响应文本直接包含结果提示
        if (body.contains("成功") || body.contains("succeed", ignoreCase = true)) return true
        // JSON 格式：Variables.favorite == 1
        if (body.trimStart().startsWith("{")) {
            return runCatching {
                JSON.parseObject(body)?.getJSONObject("Variables")?.getIntValue("favorite") == 1
            }.getOrDefault(false)
        }
        // XML 格式：<favorite>1</favorite>
        return Regex("<favorite>\\s*1\\s*</favorite>").containsMatchIn(body)
    }
}
