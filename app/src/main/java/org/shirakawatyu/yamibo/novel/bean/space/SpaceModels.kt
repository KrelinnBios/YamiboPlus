package org.shirakawatyu.yamibo.novel.bean.space

/**
 * 原生「我的空间」列表页条目。
 * 覆盖消息（私信/提醒）、好友、记录、日志、我的主题/回复。
 */
sealed interface SpaceListItem {
    /** 私信会话条目 */
    data class PrivateMessage(
        val touid: String,
        val name: String,
        val time: String,
        val summary: String,
        val avatarUrl: String?,
        val url: String,
        val messageCount: String = "",
        val isUnread: Boolean = false
    ) : SpaceListItem

    /** 提醒条目 */
    data class Notice(
        val title: String,
        val time: String,
        val summary: String,
        val avatarUrl: String?,
        val url: String
    ) : SpaceListItem

    /** 好友条目 */
    data class Friend(
        val uid: String,
        val name: String,
        val avatarUrl: String?,
        val statusText: String,
        val spaceUrl: String,
        val pmUrl: String
    ) : SpaceListItem

    /** 记录（doing）条目 */
    data class Doing(
        val doId: String,
        val uid: String,
        val name: String,
        val avatarUrl: String?,
        val time: String,
        val content: String,
        val contentImages: List<ForumInlineImage> = emptyList(),
        val comments: List<DoingComment>,
        val spaceUrl: String,
        val replyUrl: String = "",
        val deleteUrl: String = ""
    ) : SpaceListItem

    /** 日志条目 */
    data class Blog(
        val blogId: String,
        val category: String,
        val title: String,
        val time: String,
        val summary: String,
        val authorName: String,
        val url: String,
        val authorUid: String = "",
        val authorAvatarUrl: String? = null,
        val visibilityText: String = "",
        val editUrl: String = "",
        val deleteUrl: String = "",
        val stickUrl: String = "",
        val isPinned: Boolean = false,
        val tags: List<String> = emptyList()
    ) : SpaceListItem

    /** 我的主题/回复条目 */
    data class UserThread(
        val tid: String,
        val title: String,
        val time: String,
        val forumName: String,
        val viewCount: String,
        val replyCount: String,
        val isClosed: Boolean,
        val isPoll: Boolean = false,
        val url: String,
        val replyExcerpt: String = "",
        val entryType: String = "",
        val postId: String = ""
    ) : SpaceListItem
}

data class DoingComment(
    val id: String,
    val authorName: String,
    val time: String,
    val content: String,
    val contentImages: List<ForumInlineImage> = emptyList(),
    val replyUrl: String = "",
    val deleteUrl: String = ""
)

/** 论坛正文中需要在原位置显示的图片（记录页目前主要是 comcom 表情）。 */
data class ForumInlineImage(
    val offset: Int,
    val url: String,
    val alternateText: String = "[表情]"
)

enum class SpacePageKind {
    PRIVATE_MESSAGE,
    NOTICE,
    FRIEND,
    DOING,
    BLOG,
    USER_THREAD
}

data class SpaceListRequest(
    val kind: SpacePageKind,
    val uid: String = "",
    val view: String = "",
    val type: String = "",
    val categoryId: String = "",
    val fuid: String = ""
)

data class SpaceTabSpec(
    val label: String,
    val request: SpaceListRequest
)

data class SpaceListPage(
    val items: List<SpaceListItem>,
    val previousUrl: String? = null,
    val nextUrl: String? = null,
    val categories: List<SpaceCategory> = emptyList(),
    val friendFilters: List<SpaceFriendFilter> = emptyList()
)

data class SpaceCategory(
    val id: String,
    val name: String
)

data class SpaceFriendFilter(
    val uid: String,
    val name: String
)

enum class BlogBatchOperation(val visibilityValue: String?) {
    PUBLIC("0"),
    FRIENDS("1"),
    PRIVATE("3"),
    PIN(null),
    DELETE(null)
}

data class BlogBatchActionResult(
    val succeeded: Int,
    val failed: Int
)

sealed interface BlogContentBlock {
    data class Text(val value: String) : BlogContentBlock
    data class Image(val url: String) : BlogContentBlock
    data class Quote(val value: String) : BlogContentBlock
}

data class BlogComment(
    val id: String,
    val authorName: String,
    val authorUid: String,
    val avatarUrl: String?,
    val time: String,
    val content: String,
    val quotedAuthor: String = "",
    val quotedContent: String = "",
    val replyUrl: String = "",
    val editUrl: String = "",
    val deleteUrl: String = ""
)

data class BlogDetail(
    val blogId: String,
    val ownerUid: String,
    val category: String,
    val title: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val time: String,
    val viewCount: String,
    val commentCount: String,
    val blocks: List<BlogContentBlock>,
    val comments: List<BlogComment>,
    val favoriteUrl: String = "",
    val shareUrl: String = "",
    val inviteUrl: String = "",
    val stickUrl: String = "",
    val editUrl: String = "",
    val deleteUrl: String = "",
    val reportUrl: String = "",
    val tags: List<String> = emptyList(),
    val visibilityText: String = "",
    val commentFormUrl: String = "",
    val commentFormHash: String = "",
    val commentReferer: String = ""
)

data class PrivateMessageBubble(
    val isSelf: Boolean,
    val authorName: String,
    val avatarUrl: String?,
    val content: String,
    val time: String
)

data class PrivateMessageConversation(
    val touid: String,
    val title: String,
    val pmid: String,
    val formHash: String,
    val messages: List<PrivateMessageBubble>,
    val previousUrl: String? = null,
    val nextUrl: String? = null
)
