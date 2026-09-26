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
 * 隐藏链接提取：部分分享链接为躲避检测会在链接中插入杂文（表情括号、零宽字符、
 * 随机中文等），或经 QQ/微信转发后 URL 标点被自动转成全角，导致链接被打断而无法被
 * [ShareLinkParser] 识别。
 *
 * [clean] 在保留原文其余部分（如「提取码：xxxx」文案）的前提下，仅重建被打断的
 * URL 部分：
 * 1. 删除 `[...]` / `【...】` 括号内容；
 * 2. 删除零宽 / 不可见控制字符；
 * 3. 将全角标点映射回半角（`：／？．％` 等，对应 QQ/微信自动转换）；
 * 4. 在 URL 区间内按 URL 合法字符白名单剔除插入的杂文；
 * 5. 归一化路径中的重复斜杠（保留 `://`）。
 *
 * 该转换幂等，对正常链接无副作用，可在解析前安全调用。
 */
object LinkCleaner {

    /** 括号内容：`[...]` 与 `【...】`（表情 / 提示文字） */
    private val bracketRegex = Regex("""[\[【][^\]】]*[\]】]""")

    /** 零宽与不可见控制字符（常见防检测插入） */
    private val invisibleRegex =
        Regex("""[\u0000-\u001F\u007F\u00AD\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF\uFFF9-\uFFFD]""")

    /** URL 起始定位 */
    private val urlStartRegex = Regex("""https?://""", RegexOption.IGNORE_CASE)

    /** 全角标点 → 半角映射（QQ/微信转发会把 URL 标点转全角） */
    private val fullWidthMap = mapOf(
        '：' to ':', '／' to '/', '？' to '?', '．' to '.', '％' to '%',
        '～' to '~', '＃' to '#', '＆' to '&', '＝' to '=', '＠' to '@',
        '＿' to '_', '（' to '(', '）' to ')', '－' to '-', '＋' to '+',
        '＄' to '$', '＊' to '*', '；' to ';', '，' to ',', '＇' to '\''
    )

    /** 非 URL 合法字符（白名单外全部剔除，参考链接清理工具实现） */
    private val nonUrlCharRegex = Regex("""[^a-zA-Z0-9./:?=&#_@%~$+\-()!]""")

    /** 路径中重复斜杠归一（`(?<!:)` 保留 `://`） */
    private val duplicateSlashRegex = Regex("""(?<!:)//+""")

    /**
     * 清理被干扰文字打断的链接。
     * @param text 分享文案（可含提取码等额外文字）
     * @return 重建后的文案；未找到 URL 时原样返回
     */
    fun clean(text: String): String {
        var s = bracketRegex.replace(text, "")
        s = invisibleRegex.replace(s, "")
        // 全角→半角映射副本仅用于定位 URL（scheme 处也可能是全角 `https：//`）；实际替换只在 URL 区间内，尾部原文（如「提取码：」）保持不变
        val detectText = s.map { fullWidthMap[it] ?: it }.joinToString("")
        val match = urlStartRegex.find(detectText) ?: return s
        val start = match.range.first
        var end = start
        while (end < s.length && !s[end].isWhitespace()) end++
        if (end == start) return s
        val head = s.substring(0, start)
        val urlPart = s.substring(start, end).map { fullWidthMap[it] ?: it }.joinToString("")
        val tail = s.substring(end)
        val cleanUrl = duplicateSlashRegex.replace(nonUrlCharRegex.replace(urlPart, ""), "/")
        return head + cleanUrl + tail
    }
}
