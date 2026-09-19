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

import com.yunx.app.data.network.AlipanApi
import com.yunx.app.data.network.AlipanConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

/**
 * 阿里云盘分享解析仓库：分享 token → 列表 → 分享下载直链。
 * 分享接口（get_share_token / list / get_share_link_download_url）免设备签名，仅需登录 access_token 取直链。
 * 个人云盘列表/转存需要设备签名，本次不实现（分享解析即可下载，无需转存）。
 */
class AlipanResolveRepository(
    private val api: AlipanApi,
    private val tokenProvider: suspend () -> String?
) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        return runCatching {
            // 提取码优先级：用户手输 > 链接/文案自带
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val shareToken = api.getShareToken(parsed.shareId, sharePwd)
            val (title, _) = api.getShareInfo(parsed.shareId)
            ShareSession(
                shareId = parsed.shareId,
                stoken = shareToken,
                title = title.ifBlank { parsed.shareId }
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val parent = dirFid.ifBlank { AlipanConstants.ROOT_FILE_ID }
            api.listShareFiles(session.shareId, session.stoken, parent)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    /** 阿里云盘分享解析无需转存（分享直链直接可下载） */
    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("阿里云盘分享无需转存"))

    /** 阿里云盘分享解析无需转存（保存到个人云盘需设备签名，本次不实现） */
    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("阿里云盘暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("阿里云盘分享请使用 getShareDownloadLink"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        // cookie 参数即登录 access_token（ResolveViewModel.currentCredential 返回）；惰性刷新兜底
        val token = cookie.ifBlank { tokenProvider() ?: "" }
        if (token.isBlank()) throw IllegalStateException("请先登录阿里云盘")
        val link = api.getShareDownloadLink(session.shareId, file, session.stoken, token)
            ?: throw IllegalStateException("获取下载链接失败")
        link.copy(filename = file.fname.ifBlank { link.filename })
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
