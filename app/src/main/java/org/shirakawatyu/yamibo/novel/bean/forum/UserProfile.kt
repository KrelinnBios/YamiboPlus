package org.shirakawatyu.yamibo.novel.bean.forum

data class UserProfile(
    val uid: String,
    val username: String,
    val avatarUrl: String?,
    val groupTitle: String?,
    val credits: Int = 0,
    val posts: Int = 0,
    val threads: Int = 0,
    val digestCount: Int = 0,
    val formhash: String = ""
)
