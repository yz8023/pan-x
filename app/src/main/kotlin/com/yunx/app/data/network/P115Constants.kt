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

object P115Constants {

    // ---------- BaseURL（alist 115_share / SheltonZhu 115driver 实证） ----------

    /** 分享列表 / 下载直链 API */
    const val SHARE_API_BASE = "https://115cdn.com"

    /** 二维码 token 获取 */
    const val QRCODE_TOKEN_URL = "https://qrcodeapi.115.com/api/1.0/web/1.0/token"

    /** 二维码状态轮询（返回 JSON，data.status: 0等待/1已扫/2已授权/-1过期/-2取消） */
    const val QRCODE_STATUS_URL = "https://qrcodeapi.115.com/get/status/"

    /** 二维码扫码授权（form: account=uid&app=web，返回 cookie: UID/CID/SEID/KID） */
    const val QRCODE_LOGIN_URL = "https://passportapi.115.com/app/1.0/web/1.0/login/qrcode"

    /** 二维码图片（115driver QRCodeByApi；备选，可自行渲染 qrcode 内容） */
    const val QRCODE_IMAGE_URL = "https://qrcodeapi.115.com/api/1.0/mac/1.0/qrcode?uid=%s"

    // ---------- API 路径 ----------

    /** 分享文件列表：GET /webapi/share/snap（匿名） */
    const val SHARE_SNAP_URL = "$SHARE_API_BASE/webapi/share/snap"

    /** 分享文件下载直链：GET /webapi/share/downurl（需登录 Cookie） */
    const val SHARE_DOWNURL_URL = "$SHARE_API_BASE/webapi/share/downurl"

    /** 网盘空间详情：GET /files/index_info（需登录 Cookie；返回 data.space_info.all_total/all_use 等） */
    const val INDEX_INFO_URL = "https://webapi.115.com/files/index_info"

    // ---------- 公共请求头 ----------

    /** 浏览器 UA（web 系抓包；分享接口需完整 UA，缺 UA 返回 405） */
    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"

    /** 115 登录会话 Cookie（二维码授权返回，落库凭证） */
    const val COOKIE_UID = "UID"
    const val COOKIE_CID = "CID"
    const val COOKIE_SEID = "SEID"
    const val COOKIE_KID = "KID"

    /** 分享根目录 cid（snap 接口 dirID 传空即根目录） */
    const val ROOT_DIR_ID = "0"

    /** 分享接口每页条数上限（115driver 默认 20，支持更大值） */
    const val PAGE_SIZE = 50

    /** 二维码状态轮询间隔（毫秒） */
    const val QRCODE_POLL_INTERVAL_MS = 1500L

    /** 二维码过期时间（秒，超过需重新获取 token） */
    const val QRCODE_EXPIRE_SEC = 180L
}
