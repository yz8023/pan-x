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

/**
 * PikPak 分享解析常量（参照 alist `pikpak_share` 驱动，Web 平台匿名）。
 */
object PikPakConstants {

    /** 分享/文件 API */
    const val API_BASE = "https://api-drive.mypikpak.net/drive/v1"

    /** 分享详情/列表 */
    const val SHARE_DETAIL_URL = "$API_BASE/share/detail"

    /** 分享元信息（带提取码换取 pass_code_token） */
    const val SHARE_URL = "$API_BASE/share"

    /** 单文件下载信息 */
    const val FILE_INFO_URL = "$API_BASE/share/file_info"

    /** captcha token 获取 */
    const val CAPTCHA_INIT_URL = "https://user.mypikpak.net/v1/shield/captcha/init"

    // ---------- Web 平台凭证（alist pikpak_share meta） ----------
    const val CLIENT_ID = "YUMx5nI8ZU8Ap8pm"
    const val CLIENT_SECRET = "dbw2OtmVEeuUvIptb1Coyg"
    const val CLIENT_VERSION = "2.0.0"
    const val PACKAGE_NAME = "mypikpak.com"

    /** 分享根目录 fid（share/detail 根目录传空） */
    const val ROOT_DIR_ID = ""

    /** 分享每页条数 */
    const val PAGE_SIZE = 100

    /** Web 平台算法链（alist WebAlgorithms） */
    val ALGORITHMS = listOf(
        "C9qPpZLN8ucRTaTiUMWYS9cQvWOE",
        "+r6CQVxjzJV6LCV",
        "F",
        "pFJRC",
        "9WXYIDGrwTCz2OiVlgZa90qpECPD6olt",
        "/750aCr4lm/Sly/c",
        "RB+DT/gZCrbV",
        "",
        "CyLsf7hdkIRxRm215hl",
        "7xHvLi2tOYP0Y92b",
        "ZGTXXxu8E/MIWaEDB+Sm/",
        "1UI3",
        "E7fP5Pfijd+7K+t6Tg/NhuLq0eEUVChpJSkrKxpO",
        "ihtqpG6FMt65+Xk+tWUH2",
        "NhXXU9rg4XXdzo7u5o"
    )

    /** Web 浏览器 UA */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36"

    /** 设备签名用 appkey 常量（generateDeviceSign 硬编码） */
    const val DEVICE_SIGN_KEY = "appkey"
}
