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

package com.yunx.app.data.prefs

import android.content.Context
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import org.json.JSONArray
import org.json.JSONObject

data class ResolveHistoryItem(
    val link: String,
    val password: String,
    val platformName: String,
    val shareId: String,
    val timestamp: Long
)

class ResolveHistoryRepository(
    context: Context
) {
    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): List<ResolveHistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index)
                        ?: continue

                    val link = obj.optString("link")
                    val shareId = obj.optString("shareId")

                    if (link.isBlank() || shareId.isBlank()) {
                        continue
                    }

                    add(
                        ResolveHistoryItem(
                            link = link,
                            password = obj.optString("password"),
                            platformName = obj.optString(
                                "platformName",
                                "网盘"
                            ),
                            shareId = shareId,
                            timestamp = obj.optLong(
                                "timestamp",
                                0L
                            )
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(
        text: String,
        password: String?
    ): List<ResolveHistoryItem> {
        val parsed = ShareLinkParser.parse(text)
            ?: return load()

        val url = URL_REGEX
            .find(text.trim())
            ?.value
            ?.trimEnd(
                '。',
                '，',
                ',',
                '；',
                ';',
                ')',
                ']',
                '}',
                '"',
                '\''
            )
            ?: text.trim()

        val item = ResolveHistoryItem(
            link = url,
            password = password
                ?.trim()
                .orEmpty()
                .ifBlank {
                    parsed.pwd.orEmpty()
                },
            platformName = platformLabel(
                parsed.platform
            ),
            shareId = parsed.shareId,
            timestamp = System.currentTimeMillis()
        )

        val updated = buildList {
            add(item)

            addAll(
                load().filterNot {
                    it.platformName == item.platformName &&
                        it.shareId == item.shareId
                }
            )
        }.take(MAX_HISTORY)

        save(updated)

        return updated
    }

    fun remove(
        item: ResolveHistoryItem
    ): List<ResolveHistoryItem> {
        val updated = load().filterNot {
            it.platformName == item.platformName &&
                it.shareId == item.shareId
        }

        save(updated)

        return updated
    }



    fun clear() {

        prefs
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun save(
        items: List<ResolveHistoryItem>
    ) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put(
                        "link",
                        item.link
                    )
                    .put(
                        "password",
                        item.password
                    )
                    .put(
                        "platformName",
                        item.platformName
                    )
                    .put(
                        "shareId",
                        item.shareId
                    )
                    .put(
                        "timestamp",
                        item.timestamp
                    )
            )
        }

        prefs
            .edit()
            .putString(
                KEY_HISTORY,
                array.toString()
            )
            .apply()
    }

    private fun platformLabel(
        platform: SharePlatform
    ): String = when (platform) {
        SharePlatform.QUARK -> "夸克网盘"
        SharePlatform.UC -> "UC 网盘"
        SharePlatform.XUNLEI -> "迅雷网盘"
        SharePlatform.BAIDU -> "百度网盘"
        SharePlatform.C139 -> "移动云盘"
        SharePlatform.PAN123 -> "123 云盘"
        SharePlatform.ALIPAN -> "阿里云盘"
        SharePlatform.P115 -> "115 网盘"
        SharePlatform.LANZOU -> "蓝奏云"
        SharePlatform.PIKPAK -> "PikPak"
    }

    private companion object {
        const val PREFS_NAME =
            "resolve_history"

        const val KEY_HISTORY =
            "history"

        const val MAX_HISTORY =
            30

        val URL_REGEX =
            Regex("""https?://[^\s]+""")
    }
}
