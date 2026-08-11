package org.shirakawatyu.yamibo.novel.ui.vm

import org.junit.Assert.assertEquals
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.Favorite

class FavoriteTypeResolverTest {
    @Test
    fun legacyOtherWithoutSourceFidNeedsReprobe() {
        val favorite = Favorite(
            title = "旧版误判漫画",
            url = "forum.php?mod=viewthread&tid=1",
            type = 3,
            sourceFid = null
        )

        assertEquals(0, FavoriteTypeResolver.reliableType(favorite))
    }

    @Test
    fun manuallySelectedOtherRemainsReliable() {
        val favorite = Favorite(
            title = "手动设为其他",
            url = "forum.php?mod=viewthread&tid=2",
            type = 3,
            sourceFid = FavoriteTypeResolver.MANUAL_SOURCE_FID
        )

        assertEquals(3, FavoriteTypeResolver.reliableType(favorite))
    }

    @Test
    fun mangaFidOverridesLegacyType() {
        val favorite = Favorite(
            title = "漫画",
            url = "forum.php?mod=viewthread&tid=3",
            type = 3,
            sourceFid = "30"
        )

        assertEquals(2, FavoriteTypeResolver.reliableType(favorite))
    }

    @Test
    fun novelFidIsNovelFavorite() {
        val favorite = Favorite(
            title = "小说",
            url = "forum.php?mod=viewthread&tid=4",
            type = 0,
            sourceFid = "49"
        )

        assertEquals(true, FavoriteTypeResolver.isNovelFavorite(favorite))
    }

    @Test
    fun novelTypeWithoutFidIsNovelFavorite() {
        val favorite = Favorite(
            title = "小说",
            url = "forum.php?mod=viewthread&tid=5",
            type = 1,
            sourceFid = null
        )

        assertEquals(true, FavoriteTypeResolver.isNovelFavorite(favorite))
    }

    @Test
    fun mangaFavoriteIsNotNovel() {
        val favorite = Favorite(
            title = "漫画",
            url = "forum.php?mod=viewthread&tid=6",
            type = 2,
            sourceFid = "30"
        )

        assertEquals(false, FavoriteTypeResolver.isNovelFavorite(favorite))
    }

    @Test
    fun unknownTypeIsNotNovel() {
        val favorite = Favorite(
            title = "未识别收藏",
            url = "forum.php?mod=viewthread&tid=7",
            type = 0,
            sourceFid = null
        )

        assertEquals(false, FavoriteTypeResolver.isNovelFavorite(favorite))
    }
}
