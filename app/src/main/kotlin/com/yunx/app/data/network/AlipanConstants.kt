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

object AlipanConstants {

    // ---------- BaseURL（alist aliyundrive_share / tickstep aliyunpan-web 实证） ----------

    /** 业务 API（分享 token / 列表 / 直链） */
    const val API_BASE = "https://api.alipan.com"

    /** OAuth token 刷新（refresh_token → access_token） */
    const val AUTH_BASE = "https://auth.alipan.com"

    /**
     * 网页登录页：阿里云盘官方登录页（账号密码 / 扫码 / 短信）。
     * ⚠️ 首页 https://www.alipan.com/ 是营销落地页，无登录表单；必须用 /sign/in 才展示登录选项。
     * 登录后 SPA 会向当前域 localStorage 写入键 `token` 的 JSON 登录态（含 refresh_token）。
     */
    const val WEB_LOGIN_URL = "https://www.alipan.com/sign/in"

    /** 网页登录态在 localStorage 中的键名：值为 JSON（含 refresh_token / access_token） */
    const val LOCAL_STORAGE_TOKEN_KEY = "token"

    // ---------- API 路径 ----------

    /** 刷新 access_token：POST /v2/account/token（refresh_token 换新 + 返回昵称） */
    const val TOKEN_REFRESH_URL = "$AUTH_BASE/v2/account/token"

    /** 获取分享 token：POST /v2/share_link/get_share_token（share_id + 可选 share_pwd） */
    const val SHARE_TOKEN_URL = "$API_BASE/v2/share_link/get_share_token"

    /** 分享信息（标题/是否有提取码）：POST /adrive/v3/share_link/get_share_by_anonymous */
    const val SHARE_INFO_URL = "$API_BASE/adrive/v3/share_link/get_share_by_anonymous"

    /** 分享文件列表：POST /adrive/v3/file/list（带 x-share-token + X-Canary，匿名） */
    const val SHARE_LIST_URL = "$API_BASE/adrive/v3/file/list"

    /** 分享下载直链：POST /v2/file/get_share_link_download_url（需 Authorization + x-share-token） */
    const val SHARE_DOWNLOAD_URL = "$API_BASE/v2/file/get_share_link_download_url"

    // ---------- 公共请求头 ----------

    /** 浏览器 UA（web 系抓包） */
    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

    /** 分享接口 X-Canary 头（alist 实证：解除分享接口速率限制） */
    const val CANARY_HEADER = "X-Canary"
    const val CANARY_VALUE = "client=web,app=share,version=v2.3.1"

    /** 分享下载真实 CDN 直链下载时必须携带的 Referer */
    const val DOWNLOAD_REFERER = "https://www.alipan.com/"

    // ---------- 分享语义常量 ----------

    /** 分享根目录的 parent_file_id（alist DefaultRoot="root" 实证） */
    const val ROOT_FILE_ID = "root"

    /** access_token 惰性刷新间隔（token 有效期约 2h，提前刷新留足余量） */
    const val ACCESS_TOKEN_REFRESH_INTERVAL_MS = 60 * 60 * 1000L

    /** 分享直链有效期（秒，请求体 expire_sec） */
    const val LINK_EXPIRE_SEC = 600
}
