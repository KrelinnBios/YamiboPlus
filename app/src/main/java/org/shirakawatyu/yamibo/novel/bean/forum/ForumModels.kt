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
    val rank: Int? = null,
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
        get() = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$id&mobile=no"
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
    val totalPages: Int,
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
    val createdAt: String? = null,
    val authorUid: String? = null
)

data class ForumPostRatingSummary(
    val participantText: String,
    val scoreText: String,
    val ratings: List<ForumPostRating> = emptyList(),
    val viewAllUrl: String? = null
)

data class ForumPollOption(
    val text: String,
    val percent: Float? = null,
    val voteCount: Int? = null,
    val id: String? = null
)

data class ForumPoll(
    val typeText: String,
    val participantCount: Int?,
    val remainingText: String?,
    val options: List<ForumPollOption>,
    val statusText: String?,
    val formHash: String? = null,
    val actionUrl: String? = null,
    val isMultipleChoice: Boolean = false,
    val hasVoted: Boolean = false
)

data class ForumRateOption(
    val score: Int,
    val label: String
)

data class ForumRatePopout(
    val availableScores: List<ForumRateOption> = emptyList(),
    val defaultReasons: List<String> = emptyList(),
    val formHash: String? = null
)

data class ForumComment(
    val id: String,
    val authorName: String,
    val authorUid: String? = null,
    val authorAvatarUrl: String? = null,
    val createdAt: String,
    val message: String
)

data class ForumPostActionForm(
    val type: Type,
    val actionUrl: String,
    val formHash: String?
) {
    enum class Type { RATE, COMMENT }
}

data class ForumPost(
    val id: String,
    val threadId: String,
    val author: ForumPostAuthor,
    val createdAt: String,
    val editedAt: String? = null,
    val floor: Int,
    val isOriginalPost: Boolean,
    val blocks: List<ForumPostBlock>,
    val attachments: List<ForumPostAttachment> = emptyList(),
    val poll: ForumPoll? = null,
    val ratingSummary: ForumPostRatingSummary? = null,
    val comments: List<ForumComment> = emptyList(),
    val rateForm: ForumPostActionForm? = null,
    val commentForm: ForumPostActionForm? = null
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
    val hasMore: Boolean,
    val html: String = ""
)
