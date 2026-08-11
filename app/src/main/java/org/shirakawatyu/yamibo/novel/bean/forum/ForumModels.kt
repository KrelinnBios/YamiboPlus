package org.shirakawatyu.yamibo.novel.bean.forum

data class ForumCategory(
    val id: String,
    val name: String,
    val forums: List<ForumBoard>
)

data class ForumBoard(
    val id: String,
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
    val parentId: String? = null,
    val threadCount: Int = 0,
    val postCount: Int = 0,
    val todayPostCount: Int = 0,
    val subforums: List<ForumBoard> = emptyList()
)

data class ForumThread(
    val id: String,
    val subject: String,
    val authorId: String? = null,
    val authorName: String,
    val createdAt: String,
    val lastPostAt: String,
    val lastPoster: String,
    val replyCount: Int,
    val viewCount: Int,
    val displayOrder: Int,
    val typeId: String? = null,
    val typeName: String? = null
) {
    val isSticky: Boolean
        get() = displayOrder > 0

    val url: String
        get() = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$id&mobile=2"
}

data class ForumIndex(
    val categories: List<ForumCategory>
)

data class ForumThreadPage(
    val forum: ForumBoard,
    val threads: List<ForumThread>,
    val page: Int,
    val hasMore: Boolean
)

data class ForumPostAuthor(
    val id: String? = null,
    val name: String,
    val avatarUrl: String? = null,
    val isAnonymous: Boolean = false
)

data class ForumPostTextPart(
    val text: String,
    val url: String? = null
)

sealed interface ForumPostBlock {
    data class Text(val parts: List<ForumPostTextPart>) : ForumPostBlock
    data class Image(val url: String, val description: String = "") : ForumPostBlock
}

data class ForumPostAttachment(
    val id: String,
    val filename: String,
    val url: String,
    val isImage: Boolean
)

data class ForumPost(
    val id: String,
    val threadId: String,
    val author: ForumPostAuthor,
    val createdAt: String,
    val floor: Int,
    val isOriginalPost: Boolean,
    val blocks: List<ForumPostBlock>,
    val attachments: List<ForumPostAttachment> = emptyList()
)

data class ForumThreadDetail(
    val id: String,
    val forumId: String,
    val subject: String,
    val author: ForumPostAuthor,
    val replyCount: Int,
    val viewCount: Int,
    val isClosed: Boolean
)

data class ForumPostPage(
    val thread: ForumThreadDetail,
    val posts: List<ForumPost>,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean
)
