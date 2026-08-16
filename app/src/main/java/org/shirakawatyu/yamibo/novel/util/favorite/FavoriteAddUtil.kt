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
     * 取消远端板块收藏。
     * Discuz 删除收藏依赖收藏记录的 favid（而非板块 fid），因此先拉取「我的收藏板块」
     * 列表，找到 fid 对应的 favid 后再调用删除接口。
     * @param fid 板块 ID
     * @return 删除是否成功（未收藏该板块也视为成功，保证状态幂等）
     */
    suspend fun removeForumFavorite(fid: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val forumApi = YamiboRetrofit.getInstance()
                    .create(org.shirakawatyu.yamibo.novel.network.ForumApi::class.java)
                val listJson = forumApi.getFavoriteForums().string()
                val favId = findFavIdByForumId(listJson, fid)
                    // 远端列表里已无该板块：视为已取消，保持状态幂等
                    ?: return@withContext true

                FavoriteDeleteUtil.deleteFavoritesBatch(null, listOf(favId))
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 从 myfavforum 接口返回的 JSON 中解析板块 fid 对应的收藏记录 favid。
     * 不同接口版本里板块 ID 字段可能是 id 或 fid，两者都尝试匹配。
     */
    internal fun findFavIdByForumId(rawJson: String, fid: String): String? {
        if (rawJson.isBlank()) return null
        return runCatching {
            JSON.parseObject(rawJson)
                ?.getJSONObject("Variables")
                ?.getJSONArray("list")
                ?.filterIsInstance<com.alibaba.fastjson2.JSONObject>()
                ?.firstOrNull { item ->
                    item.getString("id") == fid || item.getString("fid") == fid
                }
                ?.getString("favid")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
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
