package org.shirakawatyu.yamibo.novel.bean

import com.alibaba.fastjson2.JSON
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MangaDirectory 持久化兼容性：权威目录标记的序列化与旧数据默认值。
 */
class MangaDirectoryJsonTest {

    @Test
    fun authoritativeLinksDefaultsToFalseForLegacyJson() {
        // 旧版本目录 JSON 没有 authoritativeLinks 字段，反序列化必须默认 false。
        // 样本用旧版真实写盘结构（由旧 bean 序列化而来）。
        val legacyDir = MangaDirectory(
            cleanBookName = "某作品",
            strategy = DirectoryStrategy.LINKS,
            sourceKey = "某作品",
            chapters = emptyList(),
            lastUpdateTime = 123L
        )
        val legacyJson = JSON.parseObject(JSON.toJSONString(legacyDir)).apply {
            remove("authoritativeLinks")
        }.toJSONString()
        val dir = JSON.parseObject(legacyJson, MangaDirectory::class.java)
        assertFalse(dir.authoritativeLinks)
    }

    @Test
    fun authoritativeLinksRoundTrips() {
        val dir = MangaDirectory(
            cleanBookName = "去你家/君の家まで（波虹）",
            strategy = DirectoryStrategy.LINKS,
            sourceKey = "去你家/君の家まで（波虹）",
            authoritativeLinks = true
        )
        val parsed = JSON.parseObject(JSON.toJSONString(dir), MangaDirectory::class.java)
        assertTrue(parsed.authoritativeLinks)
    }
}
