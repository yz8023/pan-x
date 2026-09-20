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

package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 115 网盘登录凭证：二维码扫码授权换取的 Cookie 会话（UID/CID/SEID/KID）。
 * Cookie 有效期不定（长期会话），失效时重新扫码登录。
 * cookie 加密存储（SecureAccountDaos）。
 */
@Entity(tableName = "p115_account")
data class P115AccountEntity(
    @PrimaryKey
    val id: String = "p115",
    /** 登录会话 Cookie（已加密；形如 UID=..;CID=..;SEID=..;KID=..） */
    val cookie: String = "",
    /** 登录账号（115 用户名，脱敏后由后端返回；展示用） */
    val account: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
