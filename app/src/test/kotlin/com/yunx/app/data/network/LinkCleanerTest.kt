/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCleanerTest {

    @Test
    fun normalLinkIsUnchanged() {
        val input = "https://pan.quark.cn/s/Abc123?pwd=a1B2"
        assertEquals(input, LinkCleaner.clean(input))
    }

    @Test
    fun bracketEmojiInsideLinkIsRemoved() {
        assertEquals(
            "https://pan.quark.cn/s/xxxx",
            LinkCleaner.clean("https://pan.quark.cn/s/xx[防检测]xx")
        )
    }

    @Test
    fun chineseBracketInsideLinkIsRemoved() {
        assertEquals(
            "https://pan.quark.cn/s/xxxx",
            LinkCleaner.clean("https://pan.quark.cn/s/xx【表情】xx")
        )
    }

    @Test
    fun zeroWidthCharsInsideLinkAreRemoved() {
        assertEquals(
            "https://pan.quark.cn/s/xxxx",
            LinkCleaner.clean("https://pan.quark.cn/s/xx\u200Bxx")
        )
    }

    @Test
    fun inlineChineseJunkInPathIsRemovedAndSlashNormalized() {
        assertEquals(
            "https://pan.quark.cn/s/xxxx",
            LinkCleaner.clean("https://pan.quark.cn/防检测/s/xxxx")
        )
    }

    @Test
    fun tailPasswordTextIsPreserved() {
        assertEquals(
            "链接 https://pan.quark.cn/s/xxxx 提取码：a1B2",
            LinkCleaner.clean("链接 https://pan.quark.cn/s/xx【表情】xx 提取码：a1B2")
        )
    }

    @Test
    fun queryPasswordIsPreserved() {
        assertEquals(
            "https://pan.quark.cn/s/xxxx?pwd=a1B2",
            LinkCleaner.clean("https://pan.quark.cn/s/xx【表情】xx?pwd=a1B2")
        )
    }

    @Test
    fun schemeSlashIsNotCollapsed() {
        assertEquals(
            "https://pan.quark.cn/s/xxxx",
            LinkCleaner.clean("https://pan.quark.cn/防检测//s/xxxx")
        )
    }

    @Test
    fun cleanIsIdempotent() {
        val input = "https://pan.quark.cn/防检测/s/xx【表情】xx 提取码：a1B2"
        val once = LinkCleaner.clean(input)
        assertEquals(once, LinkCleaner.clean(once))
    }

    @Test
    fun textWithoutUrlIsUnchanged() {
        val input = "这里没有链接"
        assertEquals(input, LinkCleaner.clean(input))
    }

    @Test
    fun shortLinkStillParsable() {
        // 清理后仍能被 ShareLinkParser 识别
        val parsed = ShareLinkParser.parse(
            LinkCleaner.clean("https://pan.quark.cn/防检测/s/Abc123【表情】?pwd=a1B2")
        )!!
        assertEquals(SharePlatform.QUARK, parsed.platform)
        assertEquals("Abc123", parsed.shareId)
        assertEquals("a1B2", parsed.pwd)
    }

    @Test
    fun ucAndBaiduObfuscatedLinksAreRecovered() {
        assertEquals(
            "https://drive.uc.cn/s/Abc123",
            LinkCleaner.clean("https://drive.uc.cn/s/A[隐藏]bc123")
        )
        assertEquals(
            "https://pan.baidu.com/s/1Abc_123-xy?pwd=9xYz",
            LinkCleaner.clean("https://pan.baidu.com/s/1A【隐藏】bc_123-xy?pwd=9xYz")
        )
    }
}
