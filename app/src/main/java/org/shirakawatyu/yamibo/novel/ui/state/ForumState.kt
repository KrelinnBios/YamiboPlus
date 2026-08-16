package org.shirakawatyu.yamibo.novel.ui.state

import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail

enum class ForumSort(val apiValue: String, val label: String) {
    LAST_REPLY("lastpost", "最新回复"),
    HOT("heat", "热门"),
   精华("digest", "精华"),
    NEW("dateline", "新帖")
}

data class ForumState(
    val categories: List<ForumCategory> = emptyList(),
    val banners: List<ForumBanner> = emptyList(),
    val selectedForum: ForumBoard? = null,
    val threads: List<ForumThread> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val sortBy: ForumSort = ForumSort.LAST_REPLY,
    val filterType: String? = null,
    val availableTypes: Map<String, String> = emptyMap()
)

data class ForumThreadState(
    val thread: ForumThreadDetail? = null,
    val posts: List<ForumPost> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val hasMore: Boolean = false,
    val onlyOriginalPoster: Boolean = false,
    val reverseOrder: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val verificationUrl: String? = null,
    val threadHtml: String = ""
)
