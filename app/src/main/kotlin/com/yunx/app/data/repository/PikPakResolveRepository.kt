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

import com.yunx.app.data.network.PikPakApi
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

/**
 * PikPak 分享解析仓库（匿名）。
 * 解析流程：captcha token（设备签名）→ 提取码换 pass_code_token → 列表 / 下载直链。
 * session.stoken 存 pass_code_token；无需登录，cookie 参数忽略。
 */
class PikPakResolveRepository(
    private val api: PikPakApi
) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        return runCatching {
            // 每次解析初始化 captcha token（设备签名固定，token 复用）
            api.setCaptchaToken(api.refreshCaptchaToken(PikPakApi.ACTION))
            val passCodeToken = api.getSharePassToken(parsed.shareId, pwd)
            ShareSession(
                shareId = parsed.shareId,
                stoken = passCodeToken,
                title = parsed.shareId
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            api.listShareFiles(session.shareId, session.stoken, dirFid)
                .map { f ->
                    ShareFile(
                        fid = f.id,
                        fname = f.name,
                        fsize = f.size,
                        isdir = f.isFolder,
                        pdirFid = dirFid,
                        fidToken = "",
                        modifyTime = f.modifyTime
                    )
                }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    /** PikPak 分享解析无需转存 */
    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("PikPak 分享无需转存"))

    /** PikPak 分享解析无需转存 */
    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("PikPak 暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("PikPak 分享请使用 getShareDownloadLink"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val download = api.getDownloadLink(session.shareId, session.stoken, file.fid)
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
