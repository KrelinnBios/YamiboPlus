package org.shirakawatyu.yamibo.novel.bean

/**
 * 收藏数据类，用于存储用户收藏的小说信息
 *
 * @param title 帖子的标题
 * @param url 帖子的链接地址
 * @param lastPage 最后阅读的页码，默认为0
 * @param lastView 最后阅读的帖子页数，默认为1
 * @param lastChapter 最后阅读的章节名称，可为空，默认为null
 * @param authorId 帖子作者ID，可为空，默认为null
 * @param lastMangaUrl 漫画书签URL
 * @param mangaCacheUrls 当前收藏已记录的漫画图片缓存键
 * @param pinAnchorUrl 置顶前的前驱条目 URL（""=原本就是第一个，null=未置顶）；供“取消置顶”回到原位
 */
data class Favorite(
    var title: String,
    var url: String,
    var lastPage: Int = 0,
    var lastView: Int = 1,
    var lastChapter: String? = null,
    var authorId: String? = null,
    var isHidden: Boolean = false,
    var type: Int = 0,
    var lastMangaUrl: String? = null,
    var favId: String? = null,
    var sourceFid: String? = null,
    var mangaCachedPages: Int = 0,
    var mangaCacheBytes: Long = 0,
    var mangaCacheUrls: List<String> = emptyList(),
    var pinAnchorUrl: String? = null,
)
