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
 * 阿里云盘登录凭证：网页登录提取 refresh_token 落库，API 请求前用 refresh_token 惰性换 access_token。
 * access_token 有效期约 2h；refresh_token 长期有效（约 3 个月），失效时重新走网页登录。
 * access_token / refresh_token 均加密存储（SecureAccountDaos）。
 */
@Entity(tableName = "alipan_account")
data class AlipanAccountEntity(
    @PrimaryKey
    val id: String = "alipan",
    /** access_token（已加密；有效期约 2h，经 refresh_token 惰性刷新） */
    val accessToken: String = "",
    /** refresh_token（已加密；长期有效，网页登录提取） */
    val refreshToken: String = "",
    /** 登录账号（网页登录拿不到手机号，留空；账号页展示时回退昵称） */
    val account: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
