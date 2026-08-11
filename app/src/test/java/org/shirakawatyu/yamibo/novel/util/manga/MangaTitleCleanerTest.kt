package org.shirakawatyu.yamibo.novel.util.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaTitleCleanerTest {
    @Test
    fun getCleanBookName_keepsJiWhenPartOfCollectionSuffix() {
        assertEquals(
            "沉溺于罪恶的犯罪百合短篇集",
            MangaTitleCleaner.getCleanBookName(
                "[短篇漫畫]【提灯喵汉化组】沉溺于罪恶的犯罪百合短篇集[原作：るぅ1mm]天使的抉择"
            )
        )
    }

    @Test
    fun getDisplayChapterTitle_dropsOnlyBookNamePrefixNotCollectionSuffix() {
        val bookName = "沉溺于罪恶的犯罪百合短篇集"
        assertEquals(
            "天使的抉择",
            MangaTitleCleaner.getDisplayChapterTitle(
                "[短篇漫畫]【提灯喵汉化组】沉溺于罪恶的犯罪百合短篇集[原作：るぅ1mm]天使的抉择",
                bookName,
                "2"
            )
        )
        assertEquals(
            "崇拜的尽头",
            MangaTitleCleaner.getDisplayChapterTitle(
                "[短篇漫畫]【提灯喵汉化组】沉溺于罪恶的犯罪百合短篇集[原作：瀬尾みいのすけ]崇拜的尽头",
                bookName,
                "1"
            )
        )
    }

    @Test
    fun getCleanBookName_stillTruncatesGenuineChapterMarkers() {
        assertEquals(
            "某漫画",
            MangaTitleCleaner.getCleanBookName("【汉化组】某漫画 番外1")
        )
        assertEquals(
            "某漫画",
            MangaTitleCleaner.getCleanBookName("【汉化组】某漫画 第3话")
        )
    }

    @Test
    fun isTruncatedCleanBookName_detectsMissingCollectionSuffix() {
        assertTrue(
            MangaTitleCleaner.isTruncatedCleanBookName(
                "沉溺于罪恶的犯罪百合",
                "沉溺于罪恶的犯罪百合短篇集"
            )
        )
    }

    @Test
    fun isTruncatedCleanBookName_ignoresUnrelatedOrManualNames() {
        assertFalse(MangaTitleCleaner.isTruncatedCleanBookName("某漫画", "某漫画"))
        assertFalse(MangaTitleCleaner.isTruncatedCleanBookName("某漫画(完结)", "某漫画"))
        assertFalse(MangaTitleCleaner.isTruncatedCleanBookName("某漫画", "另一部漫画短篇集"))
    }

    @Test
    fun getCleanBookName_dropsLeadingParodyAnnotation() {
        // 开头的"(原作名!#N)"是原作标记，真实作品名在后面；同 parody 的不同作品
        // 必须清洗出不同书名，否则会全部撞进同一个目录
        assertEquals(
            "ぼ喜多・が・ろっく",
            MangaTitleCleaner.getCleanBookName(
                "【大友同好會】( ぼっち・ざ・おんりー!#2)[まぜもの(いちみ)]ぼ喜多・が・ろっく2"
            )
        )
        assertEquals(
            "波喜多摇滚！/ぼ喜多・が・ろっく！",
            MangaTitleCleaner.getCleanBookName(
                "【透明声彩汉化组】(ぼっち・ざ・おんりー!#2) [まぜもの (いちみ)] 波喜多摇滚！/ぼ喜多・が・ろっく！-02"
            )
        )
        assertEquals(
            "去你家/君の家まで（波虹）",
            MangaTitleCleaner.getCleanBookName(
                "【偷摸铃汉化组】(ぼっち・ざ・おんりー!) [ろっとすたじお (ろっと)] 去你家/君の家まで（波虹）"
            )
        )
        assertEquals(
            "一直想见你/ずっと君に会いたかったんだ（波虹）",
            MangaTitleCleaner.getCleanBookName(
                "【透明声彩汉化组】(ぼっち・ざ・おんりー!) [Surface Tension (折口ヒラタ)] 一直想见你/ずっと君に会いたかったんだ（波虹）"
            )
        )
    }

    @Test
    fun getCleanBookName_stripsUnpairedParenResidue() {
        // 截断章节标记后残留未配对的左括号要剥掉
        assertEquals(
            "乐队少女",
            MangaTitleCleaner.getCleanBookName("【汉化组】(乐队少女 第3话")
        )
        // 配对括号是书名一部分，不能动
        assertEquals(
            "特别な中途半端(特别的半吊子)",
            MangaTitleCleaner.getCleanBookName(
                "[長篇連載] 【大友同好會】 [ドスコイ]特别な中途半端(特别的半吊子) 52+52.5"
            )
        )
    }

    @Test
    fun extractReleaseGroup_fallsBackToLeadingBracketTag() {
        // 显式汉化组名优先
        assertEquals(
            "透明声彩汉化组",
            MangaTitleCleaner.extractReleaseGroup(
                "【透明声彩汉化组】(ぼっち・ざ・おんりー!#3) [まぜもの (いちみ)] 波喜多摇滚！/ぼ喜多・が・ろっく！-03"
            )
        )
        // 不带"汉化"字样的制作组取第一个【】段兜底；[] 是分类/原作者不参与
        assertEquals(
            "大友同好會",
            MangaTitleCleaner.extractReleaseGroup(
                "【大友同好會】( ぼっち・ざ・おんりー!#2)[まぜもの(いちみ)]ぼ喜多・が・ろっく2"
            )
        )
        assertEquals(
            "大友同好會",
            MangaTitleCleaner.extractReleaseGroup(
                "[長篇連載] 【大友同好會】 [ドスコイ]特别な中途半端(特别的半吊子) 52+52.5"
            )
        )
        // 个人发布标注不算组名
        assertEquals(
            "",
            MangaTitleCleaner.extractReleaseGroup("【个人翻译】《上伊那牡丹，酒醉身姿似百合花般》动漫")
        )
        assertEquals("", MangaTitleCleaner.extractReleaseGroup("(某作品) 无组名标题 第3话"))
    }

    @Test
    fun matchesTranslationGroup_normalizesTraditionalVariants() {
        // 同一组的繁简写法要互认
        assertTrue(MangaTitleCleaner.matchesTranslationGroup("【大友同好会】某作品 第2话", "大友同好會"))
        assertTrue(MangaTitleCleaner.matchesTranslationGroup("【某某漢化組】某作品", "某某汉化组"))
        assertFalse(
            MangaTitleCleaner.matchesTranslationGroup(
                "【透明声彩汉化组】(ぼっち・ざ・おんりー!#2) 波喜多摇滚！-02",
                "大友同好會"
            )
        )
    }

    @Test
    fun isParodyResidueCleanBookName_detectsParodyPrefixDirectories() {
        val rawTitle = "【大友同好會】( ぼっち・ざ・おんりー!#2)[まぜもの(いちみ)]ぼ喜多・が・ろっく2"
        // 旧版把原作标记当书名（带不带括号残渣都算）
        assertTrue(
            MangaTitleCleaner.isParodyResidueCleanBookName(
                "( ぼっち・ざ・おんりー", rawTitle, "ぼ喜多・が・ろっく"
            )
        )
        assertTrue(
            MangaTitleCleaner.isParodyResidueCleanBookName(
                "ぼっち・ざ・おんりー", rawTitle, "ぼ喜多・が・ろっく"
            )
        )
        // 名字一致 / 用户手动起的名字（不在原作标记里）不迁移
        assertFalse(
            MangaTitleCleaner.isParodyResidueCleanBookName(
                "ぼ喜多・が・ろっく", rawTitle, "ぼ喜多・が・ろっく"
            )
        )
        assertFalse(
            MangaTitleCleaner.isParodyResidueCleanBookName(
                "我的收藏合集", rawTitle, "ぼ喜多・が・ろっく"
            )
        )
    }

    @Test
    fun getDisplayChapterTitle_dropsParenGroupContainingBookName() {
        // 书名整体位于括号段内时，删掉整个括号段而不是只删书名，避免残留"!#2)"
        assertEquals(
            "ぼ喜多・が・ろっく2",
            MangaTitleCleaner.getDisplayChapterTitle(
                "【大友同好會】( ぼっち・ざ・おんりー!#2)[まぜもの(いちみ)]ぼ喜多・が・ろっく2",
                "ぼっち・ざ・おんりー",
                "2"
            )
        )
        assertEquals(
            "一直想见你/ずっと君に会いたかった",
            MangaTitleCleaner.getDisplayChapterTitle(
                "【大友同好會】(ぼっち・ざ・おんりー!) 一直想见你/ずっと君に会いたかった",
                "ぼっち・ざ・おんりー",
                "1"
            )
        )
        // (C102) 这类场刊编号也不该留在章节标题里
        assertEquals(
            "ぼ喜多・が・ろっく1",
            MangaTitleCleaner.getDisplayChapterTitle(
                "【大友同好會】(C102)[まぜもの(いちみ)]ぼ喜多・が・ろっく1",
                "ぼっち・ざ・おんりー",
                "1"
            )
        )
        // 旧目录名尚未迁移（书名仍带"( "前缀）时，也要能剥掉"!#2)"残渣
        assertEquals(
            "ぼ喜多・が・ろっく2",
            MangaTitleCleaner.getDisplayChapterTitle(
                "【大友同好會】( ぼっち・ざ・おんりー!#2)[まぜもの(いちみ)]ぼ喜多・が・ろっく2",
                "( ぼっち・ざ・おんりー",
                "2"
            )
        )
    }

    @Test
    fun isParenResidueCleanBookName_detectsUnpairedParenResidue() {
        assertTrue(
            MangaTitleCleaner.isParenResidueCleanBookName(
                "( ぼっち・ざ・おんりー",
                "ぼっち・ざ・おんりー"
            )
        )
        // 名字一致 / 用户手动加的文字后缀 / 完全不同的名字都不迁移
        assertFalse(MangaTitleCleaner.isParenResidueCleanBookName("某漫画", "某漫画"))
        assertFalse(MangaTitleCleaner.isParenResidueCleanBookName("某漫画 (完结)", "某漫画"))
        assertFalse(MangaTitleCleaner.isParenResidueCleanBookName("别的书", "某漫画"))
        assertFalse(MangaTitleCleaner.isParenResidueCleanBookName("( 某漫画", ""))
    }

    @Test
    fun isIndividualRelease_matchesPersonalOrAdHocCredits() {
        assertTrue(
            MangaTitleCleaner.isIndividualRelease(
                "[个人汉化](犬兎ねこ)となりの席の地味な奴(邻座那个朴素的女孩)第53话"
            )
        )
        assertTrue(
            MangaTitleCleaner.isIndividualRelease(
                "【个人翻译】《上伊那牡丹，酒醉身姿似百合花般》动漫 —— 《谈话室笔记9-12》"
            )
        )
        assertTrue(MangaTitleCleaner.isIndividualRelease("【個人漢化】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【自翻】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【代发】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【渣翻】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【中字渣翻】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【个人渣翻】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【个人渣改翻】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【转载】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【授权转载】某漫画"))
        assertTrue(MangaTitleCleaner.isIndividualRelease("【授權轉載】某漫画"))
    }

    @Test
    fun isIndividualRelease_doesNotMatchNamedGroups() {
        assertFalse(
            MangaTitleCleaner.isIndividualRelease(
                "[短篇漫畫]【提灯喵汉化组】沉溺于罪恶的犯罪百合短篇集[原作：るぅ1mm]天使的抉择"
            )
        )
    }
}
