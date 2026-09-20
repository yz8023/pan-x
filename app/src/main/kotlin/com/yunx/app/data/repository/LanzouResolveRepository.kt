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

import com.yunx.app.data.network.LanzouApi
import com.yunx.app.data.network.LanzouConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

/**
 * 蓝奏云分享解析仓库（匿名）。
 * - 单文件分享：列表返回该文件一项，下载直链走分享页 ajaxm + 302 重定向；
 * - 文件夹分享：分享页子目录 + filemoreajax 分页；文件夹内文件下载共用分享页流程。
 * 无需登录，cookie 参数忽略；session.shareId 存分享页完整 URL（含 host），stoken 存提取码。
 */
class LanzouResolveRepository(
    private val api: LanzouApi
) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        return runCatching {
            val sharePageUrl = "https://${parsed.shareId}"
            ShareSession(
                shareId = sharePageUrl,
                stoken = pwd.orEmpty(),
                title = api.idOf(sharePageUrl)
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            // 根目录用分享页 URL；子目录用分享页 URL 去掉 id 后拼 href
            val pageUrl = if (dirFid.isBlank()) {
                session.shareId
            } else {
                api.baseUrlOf(session.shareId) + "/" + dirFid
            }
            api.listShare(pageUrl, session.stoken)
                .map { e ->
                    ShareFile(
                        fid = e.id,
                        fname = e.name,
                        fsize = e.size,
                        isdir = e.isFolder,
                        pdirFid = dirFid,
                        fidToken = "",
                        modifyTime = e.modifyTime
                    )
                }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    /** 蓝奏分享解析无需转存 */
    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("蓝奏云分享无需转存"))

    /** 蓝奏分享解析无需转存 */
    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("蓝奏云暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("蓝奏云分享请使用 getShareDownloadLink"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        // 文件直链解析页：文件 fid 即该文件的分享页 id
        val pageUrl = api.baseUrlOf(session.shareId) + "/" + file.fid
        val download = api.getDownloadUrl(pageUrl, session.stoken)
        DownloadLink(
            fid = file.fid,
            filename = download.filename.ifBlank { file.fname.ifBlank { "unknown" } },
            downloadUrl = download.url,
            size = file.fsize
        )
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
