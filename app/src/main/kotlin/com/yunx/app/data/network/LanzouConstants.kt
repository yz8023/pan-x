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
 * 蓝奏云分享解析常量（参照 alist `lanzou` 驱动）。
 * 分享链接域名多变（lanzoui/lanzouj/lanzoux/pan.lanzoui 等），解析时从链接取实际 host。
 */
object LanzouConstants {

    /** 反爬校验计算用的异或 Key（alist help.go HexXor 硬编码） */
    const val ACW_KEY = "3000176000856006061501533003690027800375"

    /** 反爬校验页面特征：arg1='<hex>' */
    const val ACW_ARG_REGEX = "arg1='([0-9A-Z]+)'"

    /** 蓝奏云 UA（分享页/下载均要求） */
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.39 (KHTML, like Gecko) Chrome/89.0.4389.111 Safari/537.39"

    /** ajax 请求 Referer 基准 */
    const val REFERER_BASE = "https://pc.woozooo.com"

    /** 下载重定向需带的 accept-language */
    const val ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"

    /** 分享根目录 fid（shareId 即根；listFiles 根目录传空） */
    const val ROOT_DIR_ID = ""
}
