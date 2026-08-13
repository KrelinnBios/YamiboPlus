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
    val headImageUrl: String? = null,
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

    val avatarUrl: String?
        get() = authorId?.let {
            "https://bbs.yamibo.com/uc_server/avatar.php?uid=$it&size=small"
        }

    val url: String
        get() = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$id&mobile=2"
}

data class ForumBanner(
    val imageUrl: String,
    val threadId: String? = null
)

data class ForumIndex(
    val categories: List<ForumCategory>
)

data class ForumThreadPage(
    val forum: ForumBoard,
    val threads: List<ForumThread>,
    val page: Int,
    val hasMore: Boolean,
    val availableTypes: Map<String, String> = emptyMap()
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

data class ForumPostRating(
    val userName: String,
    val score: String,
    val reason: String = "",
    val createdAt: String? = null
)

data class ForumPostRatingSummary(
    val participantText: String,
    val scoreText: String,
    val ratings: List<ForumPostRating> = emptyList(),
    val viewAllUrl: String? = null
)

data class ForumPost(
    val id: String,
    val threadId: String,
    val author: ForumPostAuthor,
    val createdAt: String,
    val floor: Int,
    val isOriginalPost: Boolean,
    val blocks: List<ForumPostBlock>,
    val attachments: List<ForumPostAttachment> = emptyList(),
    val ratingSummary: ForumPostRatingSummary? = null
)

data class ForumThreadDetail(
    val id: String,
    val forumId: String,
    val subject: String,
    val author: ForumPostAuthor,
    val replyCount: Int,
    val viewCount: Int,
    val isClosed: Boolean,
    val forumName: String = "",
    val lastPoster: String = ""
)

data class ForumPostPage(
    val thread: ForumThreadDetail,
    val posts: List<ForumPost>,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean
)
