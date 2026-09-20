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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阿里云盘登录相关常量守卫测试。
 * 防止回归到旧营销首页（www.alipan.com/ 无登录表单，会导致用户无法登录）。
 */
class AlipanConstantsTest {

    @Test
    fun webLoginUrlPointsToSignInPage() {
        assertEquals("https://www.alipan.com/sign/in", AlipanConstants.WEB_LOGIN_URL)
        assertTrue(AlipanConstants.WEB_LOGIN_URL.endsWith("/sign/in"))
    }

    @Test
    fun localStorageTokenKeyIsToken() {
        assertEquals("token", AlipanConstants.LOCAL_STORAGE_TOKEN_KEY)
    }

    @Test
    fun tokenRefreshUrlUsesAuthBase() {
        assertEquals(
            "https://auth.alipan.com/v2/account/token",
            AlipanConstants.TOKEN_REFRESH_URL
        )
    }
}
