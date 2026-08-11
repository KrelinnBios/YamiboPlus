package org.shirakawatyu.yamibo.novel.ui.state

import org.shirakawatyu.yamibo.novel.bean.MangaHomeItem

data class MangaHomeState(
    val items: List<MangaHomeItem> = emptyList(),
    val selectedFid: String = "30",
    val query: String = "",
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)
