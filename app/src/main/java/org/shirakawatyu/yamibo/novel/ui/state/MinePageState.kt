package org.shirakawatyu.yamibo.novel.ui.state

import org.shirakawatyu.yamibo.novel.bean.forum.UserProfile

data class MinePageState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val cacheSize: Long = 0L,
    val isClearingCache: Boolean = false
)
