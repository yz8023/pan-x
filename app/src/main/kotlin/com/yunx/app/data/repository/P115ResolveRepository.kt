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

import com.yunx.app.data.network.P115Api
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

/**
 * 115 网盘分享解析仓库：snap 列表（匿名）→ downurl 直链（需登录 Cookie）。
 * 115 无 refresh_token，登录凭证为二维码换取的 Cookie（UID/CID/SEID/KID）。
 * 分享列表可匿名浏览，但取下载直链必须登录（实测返回「登录超时」）。
 */
class P115ResolveRepository(
    private val api: P115Api,
    private val tokenProvider: suspend () -> String?
) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            ShareSession(
                shareId = parsed.shareId,
                // 115 无分享 token；把提取码存进 stoken 字段随 session 传递（分享码在 shareId）
                stoken = sharePwd,
                title = parsed.shareId
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val dirId = dirFid.ifBlank { "0" }
            api.listShareFiles(session.shareId, session.stoken, dirId)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    /** 115 分享解析无需转存 */
    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("115 分享无需转存"))

    /** 115 分享解析无需转存 */
    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("115 暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("115 分享请使用 getShareDownloadLink"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val credential = cookie.ifBlank { tokenProvider() ?: "" }
        if (credential.isBlank()) throw IllegalStateException("请先登录 115 网盘")
        val url = api.getShareDownloadLink(session.shareId, session.stoken, file)
        DownloadLink(
            fid = file.fid,
            filename = file.fname.ifBlank { "unknown" },
            downloadUrl = url,
            size = file.fsize
        )
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
