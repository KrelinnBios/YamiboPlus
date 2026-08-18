package org.shirakawatyu.yamibo.novel.ui.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarPolicyTest {
    @Test
    fun mangaWebReaderKeepsReturnBar() {
        assertTrue(BottomBarPolicy.shouldShowBottomBar("MangaWebPage/encoded"))
    }

    @Test
    fun nativeReadersRemainImmersive() {
        assertFalse(BottomBarPolicy.shouldShowBottomBar("NativeMangaPage?url=encoded"))
        assertFalse(BottomBarPolicy.shouldShowBottomBar("ReaderPage/encoded"))
    }
}
