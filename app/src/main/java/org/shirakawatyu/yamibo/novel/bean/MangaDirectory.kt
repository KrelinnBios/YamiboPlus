package org.shirakawatyu.yamibo.novel.bean

import com.alibaba.fastjson2.annotation.JSONCreator
import com.alibaba.fastjson2.annotation.JSONField

/**
 * 整部漫画的目录档案
 */
data class MangaDirectory @JSONCreator constructor(
    @JSONField(name = "cleanBookName")
    val cleanBookName: String,               // 纯净书名

    @JSONField(name = "strategy")
    val strategy: DirectoryStrategy,         // 更新策略

    @JSONField(name = "sourceKey")
    val sourceKey: String,                   // 策略的依据：如果是TAG，存TagId；如果是SEARCH，存书名

    @JSONField(name = "chapters")
    val chapters: List<MangaChapterItem> = emptyList(), // 章节列表

    @JSONField(name = "isOneShot")
    val isOneShot: Boolean = false,           // 是否被判定为短篇/单本

    @JSONField(name = "lastUpdateTime")
    var lastUpdateTime: Long = 0L,

    @JSONField(name = "searchKeyword")
    val searchKeyword: String? = null,

    @JSONField(name = "sourceFid")
    val sourceFid: String? = null,

    @JSONField(name = "translationGroup")
    val translationGroup: String? = null,

    // 原作者识别已整体移除（老是识别错误）；旧目录 JSON 里的 originalAuthor 字段会被 fastjson2 忽略。

    @JSONField(name = "publisherUid")
    val publisherUid: String? = null,

    @JSONField(name = "publisherName")
    val publisherName: String? = null,

    // 首楼编号跨作品链接列表构成的权威目录：章节以帖内列表为准（保持列表顺序和标题），
    // 搜索更新不得覆盖/重排/扩展，打开原帖时按首楼列表刷新。旧目录 JSON 缺该字段时默认 false。
    @JSONField(name = "authoritativeLinks")
    val authoritativeLinks: Boolean = false
)
