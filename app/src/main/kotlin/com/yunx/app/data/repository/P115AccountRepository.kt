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

package com.yunx.app.data.repository

import com.yunx.app.data.db.P115AccountDao
import com.yunx.app.data.db.P115AccountEntity
import com.yunx.app.data.network.P115Api
import kotlinx.coroutines.flow.Flow

/**
 * 115 网盘账号仓库：二维码扫码授权换取 Cookie 落库。
 * 115 无 refresh_token OAuth，登录凭证 = Cookie（UID/CID/SEID/KID）。
 * Cookie 有效期不定，失效时重新扫码。
 */
class P115AccountRepository(
    private val dao: P115AccountDao,
    private val api: P115Api
) {

    fun observeAccount(): Flow<P115AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): P115AccountEntity? = dao.getAccount()

    /** 校验当前 Cookie 是否仍有效（失败自动清库，下次重新扫码） */
    suspend fun validate(): Boolean {
        val acc = dao.getAccount() ?: return false
        val ok = acc.cookie.isNotBlank()
        if (!ok) dao.clear()
        return ok
    }

    /** 保存二维码授权 Cookie */
    suspend fun saveCookie(cookie: String, nickname: String = ""): Boolean {
        val c = cookie.trim()
        if (c.isBlank() || !c.contains("SEID")) return false
        dao.upsert(
            P115AccountEntity(
                id = "p115",
                cookie = c,
                account = "",
                nickname = nickname.ifBlank { "115 用户" }
            )
        )
        return true
    }

    /** 退出登录：清库 */
    suspend fun logout() {
        dao.clear()
    }
}
