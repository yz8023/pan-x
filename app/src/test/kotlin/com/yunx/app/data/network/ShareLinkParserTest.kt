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
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkParserTest {

    @Test
    fun parsesAllSupportedPlatforms() {
        val cases = listOf(
            "https://pan.quark.cn/s/Abc123?pwd=a1B2" to (SharePlatform.QUARK to "Abc123"),
            "https://drive.uc.cn/s/Abc123" to (SharePlatform.UC to "Abc123"),
            "https://pan.xunlei.com/s/Abc_123-xy" to (SharePlatform.XUNLEI to "Abc_123-xy"),
            "https://pan.baidu.com/s/1Abc_123-xy?pwd=9xYz" to (SharePlatform.BAIDU to "Abc_123-xy"),
            "https://yun.139.com/shareweb/#/w/i/Abc_123" to (SharePlatform.C139 to "Abc_123"),
            "https://www.123pan.com/s/2785Vv-T4Ded" to (SharePlatform.PAN123 to "2785Vv-T4Ded"),
            "https://www.alipan.com/s/Abc123XyZ" to (SharePlatform.ALIPAN to "Abc123XyZ"),
            "https://www.aliyundrive.com/s/Abc123XyZ" to (SharePlatform.ALIPAN to "Abc123XyZ"),
            "https://115.com/s/sw6pw793wfp?password=w816" to (SharePlatform.P115 to "sw6pw793wfp"),
            "https://pan.lanzoui.com/iZz123abc" to (SharePlatform.LANZOU to "pan.lanzoui.com/iZz123abc"),
            "https://mypikpak.com/s/Abc_123XyZ" to (SharePlatform.PIKPAK to "Abc_123XyZ")
        )

        cases.forEach { (text, expected) ->
            val parsed = ShareLinkParser.parse(text)!!
            assertEquals(expected.first, parsed.platform)
            assertEquals(expected.second, parsed.shareId)
        }
    }

    @Test
    fun explicitTextPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://drive.uc.cn/s/Abc123 提取码：a1B2")!!
        assertEquals("a1B2", parsed.pwd)
    }

    @Test
    fun alipanTextPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://www.alipan.com/s/Abc123XyZ 提取码：9yZ1")!!
        assertEquals(SharePlatform.ALIPAN, parsed.platform)
        assertEquals("9yZ1", parsed.pwd)
    }

    @Test
    fun p115UrlPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://115.com/s/sw6pw793wfp?password=w816")!!
        assertEquals(SharePlatform.P115, parsed.platform)
        assertEquals("w816", parsed.pwd)
    }

    @Test
    fun lanzouUrlPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("https://pan.lanzoui.com/iZz123abc 访问码：a1b2")!!
        assertEquals(SharePlatform.LANZOU, parsed.platform)
        assertEquals("a1b2", parsed.pwd)
    }

    @Test
    fun lanzouMultiDomainIsSupported() {
        val parsed = ShareLinkParser.parse("https://www.lanzouj.com/iZz123abc")!!
        assertEquals(SharePlatform.LANZOU, parsed.platform)
        assertEquals("www.lanzouj.com/iZz123abc", parsed.shareId)
    }

    @Test
    fun pikpakUrlPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("https://mypikpak.com/s/Abc_123XyZ 提取码：9yZ1")!!
        assertEquals(SharePlatform.PIKPAK, parsed.platform)
        assertEquals("9yZ1", parsed.pwd)
    }

    @Test
    fun rejectsUnrelatedUrl() {
        assertNull(ShareLinkParser.parse("https://example.com/s/Abc123"))
    }
}
