package org.shirakawatyu.yamibo.novel.util.favorite

import androidx.datastore.preferences.core.stringPreferencesKey
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.util.DataStoreUtil
import org.jsoup.parser.Parser
import kotlin.coroutines.resume

/**
 * 收藏管理工具
 */
class FavoriteUtil {
    companion object {
        private val key = stringPreferencesKey("yamibo_favorite")
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var saveJob: Job? = null
        private val writeMutex = Mutex()
        private var pendingFavMap: LinkedHashMap<String, Favorite>? = null

        fun getFavoriteFlow(): Flow<List<Favorite>> {
            val dataStore = GlobalData.Companion.dataStore
                ?: throw IllegalStateException("DataStore not initialized")
            return dataStore.data.map { preferences ->
                writeMutex.withLock {
                    pendingFavMap?.let {
                        return@withLock it.values.toList()
                    }

                    val jsonString = preferences[key]
                    if (jsonString != null) {
                        try {
                            jsonToHashMap(jsonString).values.toList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            }
        }

        fun saveFavoriteOrder(orderedList: List<Favorite>) {
            ioScope.launch {
                writeMutex.withLock {
                    val favMap = LinkedHashMap<String, Favorite>()

                    for (fav in orderedList) {
                        favMap[fav.url] = fav
                    }

                    pendingFavMap = favMap

                    saveJob?.cancel()
                    saveJob = launch {
                        delay(1500L)
                        writeMutex.withLock {
                            pendingFavMap?.let {
                                DataStoreUtil.Companion.addData(JSON.toJSONString(it), key)
                                pendingFavMap = null
                            }
                        }
                    }
                }
            }
        }

        suspend fun updateHiddenStatus(urls: Set<String>, isHidden: Boolean) {
            writeMutex.withLock {
                val oldMap = getFavoriteMapSuspend()
                var changed = false
                urls.forEach { url ->
                    oldMap[url]?.let { fav ->
                        if (fav.isHidden != isHidden) {
                            fav.isHidden = isHidden
                            changed = true
                        }
                    }
                }
                if (changed) {
                    pendingFavMap = null
                    suspendCancellableCoroutine { cont ->
                        DataStoreUtil.Companion.addData(
                            JSON.toJSONString(oldMap),
                            key
                        ) { cont.resume(Unit) }
                    }
                }
            }
        }

        suspend fun mergeFavoritesProgressiveSuspend(pageList: List<Favorite>): Boolean {
            return writeMutex.withLock {
                val oldMap = getFavoriteMapSuspend()
                var hasNewItems = false
                var hasUpdatedMetadata = false
                val pureNewItems = mutableListOf<Favorite>()

                for (netFav in pageList) {
                    val oldFav = oldMap[netFav.url]
                    if (oldFav == null) {
                        pureNewItems.add(netFav)
                        hasNewItems = true
                    } else {
                        // 发现老数据：同步论坛标题和缺失的 favId，保留本地阅读进度等字段。
                        val decodedTitle = decodeTitle(netFav.title)
                        if (decodedTitle.isNotBlank() && oldFav.title != decodedTitle) {
                            oldFav.title = decodedTitle
                            hasUpdatedMetadata = true
                        }
                        if (oldFav.favId != netFav.favId && !netFav.favId.isNullOrEmpty()) {
                            oldFav.favId = netFav.favId
                            hasUpdatedMetadata = true
                        }
                    }
                }

                val newMap = mergeNewFavoritesPreservingPins(oldMap, pureNewItems)

                if (hasNewItems || hasUpdatedMetadata) {
                    pendingFavMap = null
                    suspendCancellableCoroutine { cont ->
                        DataStoreUtil.Companion.addData(
                            JSON.toJSONString(newMap),
                            key
                        ) { cont.resume(Unit) }
                    }
                }
                hasNewItems
            }
        }

        /**
         * 新收藏排在普通旧收藏之前，但不能越过用户明确置顶的条目。
         * 置顶项和普通项各自保持原有顺序，新收藏保持论坛返回顺序。
         */
        internal fun mergeNewFavoritesPreservingPins(
            oldMap: LinkedHashMap<String, Favorite>,
            newItems: List<Favorite>
        ): LinkedHashMap<String, Favorite> {
            val merged = LinkedHashMap<String, Favorite>()
            oldMap.forEach { (url, favorite) ->
                if (favorite.pinAnchorUrl != null) merged[url] = favorite
            }
            newItems.forEach { favorite -> merged[favorite.url] = favorite }
            oldMap.forEach { (url, favorite) ->
                if (favorite.pinAnchorUrl == null) merged[url] = favorite
            }
            return merged
        }

        /**
         * 展示收藏时始终让置顶项优先，同时保持置顶组和普通组各自的原始顺序。
         *
         * 持久化顺序可能来自旧数据或同步中的中间状态，不能仅依赖 map 顺序判断置顶位置。
         */
        internal fun orderPinnedFavoritesFirst(items: List<Favorite>): List<Favorite> {
            if (items.none { it.pinAnchorUrl != null }) return items
            return items.filter { it.pinAnchorUrl != null } +
                    items.filter { it.pinAnchorUrl == null }
        }

        /**
         * Discuz 收藏接口中的标题偶尔会被重复转义，例如 &amp;amp;。
         * 最多解码两层，既修正显示，也避免对异常输入无限循环。
         */
        internal fun decodeTitle(rawTitle: String): String {
            var decoded = rawTitle
            repeat(2) {
                val next = Parser.unescapeEntities(decoded, false)
                if (next == decoded) return decoded
                decoded = next
            }
            return decoded
        }

        suspend fun cleanupDeletedFavoritesSuspend(fullNetworkList: List<Favorite>) {
            val removedUrls = mutableSetOf<String>()
            writeMutex.withLock {
                val oldMap = getFavoriteMapSuspend()
                val networkUrls = fullNetworkList.map { it.url }.toSet()
                val cleanedMap = LinkedHashMap<String, Favorite>()

                oldMap.forEach { (url, fav) ->
                    if (networkUrls.contains(url)) {
                        cleanedMap[url] = fav
                    } else {
                        removedUrls.add(url)
                    }
                }

                if (cleanedMap.size != oldMap.size) {
                    pendingFavMap = null
                    DataStoreUtil.Companion.addData(JSON.toJSONString(cleanedMap), key)
                }
            }
            removedUrls.forEach { url ->
                org.shirakawatyu.yamibo.novel.util.updateCheck.NovelUpdateCheckUtil.removeProfileSuspend(url)
                org.shirakawatyu.yamibo.novel.util.updateCheck.MangaUpdateCheckUtil.removeProfileSuspend(url)
            }
        }

        suspend fun updateFavoriteSuspend(favorite: Favorite) {
            writeMutex.withLock {
                val map = getFavoriteMapSuspend()
                if (map.containsKey(favorite.url)) {
                    map[favorite.url] = favorite
                    pendingFavMap = null
                    suspendCancellableCoroutine { cont ->
                        DataStoreUtil.Companion.addData(JSON.toJSONString(map), key) {
                            cont.resume(
                                Unit
                            )
                        }
                    }
                }
            }
        }

        suspend fun checkAndUpdateTitleSuspend(url: String, title: String?) {
            if (title.isNullOrBlank()) return
            val decodedTitle = decodeTitle(title)
            writeMutex.withLock {
                val map = getFavoriteMapSuspend()
                map[url]?.let { fav ->
                    if (fav.title != decodedTitle) {
                        map[url] = fav.copy(title = decodedTitle)
                        pendingFavMap = null
                        suspendCancellableCoroutine { cont ->
                            DataStoreUtil.Companion.addData(
                                JSON.toJSONString(map),
                                key
                            ) { cont.resume(Unit) }
                        }
                    }
                }
            }
        }

        internal fun jsonToHashMap(text: String): LinkedHashMap<String, Favorite> {
            val map = LinkedHashMap<String, Favorite>()
            try {
                val jsonObject: JSONObject = JSON.parseObject(text)
                jsonObject.values.forEach {
                    val obj = it as JSONObject
                    val rawUrl = obj.getString("url") ?: ""
                    val normalizedUrl = normalizeUrl(rawUrl)
                    val fav = Favorite(
                        title = decodeTitle(obj.getString("title") ?: ""),
                        url = normalizedUrl,
                        lastPage = obj.getIntValue("lastPage"),
                        lastView = obj.getIntValue("lastView"),
                        lastChapter = obj.getString("lastChapter"),
                        authorId = obj.getString("authorId"),
                        isHidden = obj.getBooleanValue("isHidden"),
                        type = obj.getIntValue("type"),
                        lastMangaUrl = obj.getString("lastMangaUrl"),
                        favId = obj.getString("favId"),
                        sourceFid = obj.getString("sourceFid"),
                        mangaCachedPages = obj.getIntValue("mangaCachedPages"),
                        mangaCacheBytes = obj.getLongValue("mangaCacheBytes"),
                        mangaCacheUrls = obj.getJSONArray("mangaCacheUrls")
                            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                            .orEmpty(),
                        pinAnchorUrl = obj.getString("pinAnchorUrl")
                    )
                    map[fav.url] = fav
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return map
        }

        fun normalizeUrl(url: String): String {
            val tid = Regex("tid=(\\d+)").find(url)?.groupValues?.get(1)
                ?: Regex("thread-(\\d+)-").find(url)?.groupValues?.get(1)
            return if (tid != null) "forum.php?mod=viewthread&tid=$tid" else url
        }

        suspend fun getFavoriteMapSuspend(): LinkedHashMap<String, Favorite> =
            suspendCancellableCoroutine { cont ->
                pendingFavMap?.let {
                    cont.resume(LinkedHashMap(it))
                    return@suspendCancellableCoroutine
                }

                DataStoreUtil.Companion.getData(key, callback = {
                    try {
                        val favMap = jsonToHashMap(it)
                        cont.resume(favMap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        cont.resume(LinkedHashMap())
                    }
                }, onNull = {
                    cont.resume(LinkedHashMap())
                })
            }

        suspend fun moveUrlToTopSuspend(url: String) {
            writeMutex.withLock {
                val map = getFavoriteMapSuspend()
                if (map.containsKey(url)) {
                    // 记录置顶前的前驱条目 URL，供“取消置顶”回到原位；原本就是第一个则记空串。
                    // 已置顶过的保留首次记录的原位，避免重复置顶把原位覆盖掉。
                    val keys = map.keys.toList()
                    val idx = keys.indexOf(url)
                    val existingAnchor = map[url]?.pinAnchorUrl
                    val anchor = existingAnchor ?: if (idx > 0) keys[idx - 1] else ""
                    val fav = map.remove(url)!!.copy(pinAnchorUrl = anchor)
                    val newMap = LinkedHashMap<String, Favorite>()
                    newMap[url] = fav
                    newMap.putAll(map)
                    pendingFavMap = null
                    suspendCancellableCoroutine { cont ->
                        DataStoreUtil.Companion.addData(
                            JSON.toJSONString(newMap),
                            key
                        ) { cont.resume(Unit) }
                    }
                }
            }
        }

        // 取消置顶：把该条放回置顶前的原位（紧跟在记录的前驱之后；前驱为空则回到最前，
        // 前驱已被删除则退回末尾兜底），并清除置顶标记。
        suspend fun restoreUrlToOriginalSuspend(url: String) {
            writeMutex.withLock {
                val map = getFavoriteMapSuspend()
                val fav = map[url] ?: return
                val anchor = fav.pinAnchorUrl ?: return
                map.remove(url)
                val restored = fav.copy(pinAnchorUrl = null)
                val newMap = LinkedHashMap<String, Favorite>()
                when {
                    anchor.isEmpty() -> {
                        newMap[url] = restored
                        newMap.putAll(map)
                    }
                    map.containsKey(anchor) -> {
                        for ((k, v) in map) {
                            newMap[k] = v
                            if (k == anchor) newMap[url] = restored
                        }
                    }
                    else -> {
                        newMap.putAll(map)
                        newMap[url] = restored
                    }
                }
                pendingFavMap = null
                suspendCancellableCoroutine { cont ->
                    DataStoreUtil.Companion.addData(
                        JSON.toJSONString(newMap),
                        key
                    ) { cont.resume(Unit) }
                }
            }
        }

        suspend fun resetMangaCacheCountsSuspend() {
            writeMutex.withLock {
                val map = getFavoriteMapSuspend()
                var changed = false
                map.replaceAll { _, favorite ->
                    if (favorite.mangaCachedPages > 0 || favorite.mangaCacheUrls.isNotEmpty()) {
                        changed = true
                        favorite.copy(
                            mangaCachedPages = 0,
                            mangaCacheBytes = 0,
                            mangaCacheUrls = emptyList()
                        )
                    } else {
                        favorite
                    }
                }
                if (changed) {
                    pendingFavMap = null
                    suspendCancellableCoroutine { cont ->
                        DataStoreUtil.Companion.addData(
                            JSON.toJSONString(map),
                            key
                        ) { cont.resume(Unit) }
                    }
                }
            }
        }
    }
}
