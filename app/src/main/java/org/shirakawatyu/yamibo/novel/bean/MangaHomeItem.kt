package org.shirakawatyu.yamibo.novel.bean

data class MangaHomeItem(
    val tid: String,
    val title: String,
    val url: String,
    val authorName: String = "",
    val date: String = "",
    val forumName: String = "",
    val coverUrl: String? = null
)
