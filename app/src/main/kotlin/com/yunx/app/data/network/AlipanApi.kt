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

import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 阿里云盘（alipan）API 客户端。
 * 实现参照 alist `aliyundrive_share` 驱动（分享接口免设备签名，稳健可移植）+ tickstep `aliyunpan-web`。
 *
 * 分享解析链路（均匿名/仅分享 token，无需登录 access_token）：
 *   分享 token → 分享信息 → 目录列表（marker 翻页）→ 分享下载直链
 * 个人云盘列表/容量需要设备签名（X-Signature），本次不实现（仅分享解析）。
 */
class AlipanApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    /** 每次请求动态获取全局客户端（忽略 SSL 开关切换即时生效） */
    private val client get() = clientProvider()

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    /** 刷新令牌结果（refresh_token 换 access_token + 新 refresh_token） */
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val nickname: String
    )

    // ---------- 登录（refresh_token → access_token） ----------

    /**
     * 用 refresh_token 换取 access_token（POST /v2/account/token，alist 实证）。
     * @return access_token + 轮换后的 refresh_token + 昵称；失败返回 null
     */
    suspend fun refreshToken(refreshToken: String): TokenPair? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("refresh_token", refreshToken)
                .put("grant_type", "refresh_token")
            val json = post(AuthRequest(url = AlipanConstants.TOKEN_REFRESH_URL, body = body.toString()))
            checkOk(json, "刷新登录态失败")
            val access = json.optString("access_token")
            val refreshed = json.optString("refresh_token").ifBlank { refreshToken }
            if (access.isBlank()) return@runCatching null
            val nickname = json.optString("nick_name")
                .ifBlank { json.optString("user_name") }
            TokenPair(accessToken = access, refreshToken = refreshed, nickname = nickname)
        }.getOrNull()
    }

    // ---------- 分享解析（免签名） ----------

    /**
     * 获取分享 token：POST /v2/share_link/get_share_token（alist 实证）。
     * 分享有提取码时必须带 share_pwd，否则返回错误；无提取码可不传。
     * @return share_token；失败抛错
     */
    suspend fun getShareToken(shareId: String, sharePwd: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("share_id", shareId)
        if (sharePwd.isNotBlank()) body.put("share_pwd", sharePwd)
        val json = post(
            AuthRequest(
                url = AlipanConstants.SHARE_TOKEN_URL,
                body = body.toString(),
                // alist 实证：get_share_token 无需任何鉴权头
                needAuth = false
            )
        )
        checkOk(json, "获取分享访问令牌失败")
        json.optString("share_token").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("获取分享访问令牌失败")
    }

    /**
     * 分享信息：POST /adrive/v3/share_link/get_share_by_anonymous（tickstep 实证）。
     * 返回分享标题与是否带提取码；取标题展示用。
     */
    suspend fun getShareInfo(shareId: String): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("share_id", shareId)
        val json = post(
            AuthRequest(
                url = "${AlipanConstants.SHARE_INFO_URL}?share_id=$shareId",
                body = body.toString(),
                needAuth = false
            )
        )
        checkOk(json, "获取分享信息失败")
        json.optString("share_title") to json.optBoolean("has_pwd", false)
    }

    /**
     * 单页分享文件列表：POST /adrive/v3/file/list（alist 实证，匿名 + x-share-token + X-Canary）。
     * @param parentFileId 目录 fid；分享根目录传 AlipanConstants.ROOT_FILE_ID（"root"）
     * @return (文件列表, 下一页 marker or null=末页)
     */
    suspend fun listSharePage(
        shareId: String,
        shareToken: String,
        parentFileId: String,
        marker: String
    ): Pair<List<ShareFile>, String?> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("image_thumbnail_process", "image/resize,w_160/format,jpeg")
            .put("image_url_process", "image/resize,w_1920/format,jpeg")
            .put("limit", 200)
            .put("order_by", "name")
            .put("order_direction", "DESC")
            .put("parent_file_id", parentFileId)
            .put("share_id", shareId)
            .put("video_thumbnail_process", "video/snapshot,t_1000,f_jpg,ar_auto,w_300")
            .put("marker", marker)
        val request = Request.Builder()
            .url(AlipanConstants.SHARE_LIST_URL)
            .header("x-share-token", shareToken)
            .header(AlipanConstants.CANARY_HEADER, AlipanConstants.CANARY_VALUE)
            .header("User-Agent", AlipanConstants.WEB_UA)
            .header("Content-Type", "application/json;charset=UTF-8")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        val json = executeJson(request)
        checkOk(json, "获取文件列表失败")
        val arr = json.optJSONArray("items") ?: return@withContext Pair(emptyList(), null)
        val files = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    ShareFile(
                        fid = item.optString("file_id"),
                        fname = item.optString("name"),
                        fsize = item.optLong("size"),
                        isdir = item.optString("type") == "folder",
                        pdirFid = item.optString("parent_file_id"),
                        // 下载直链需要 drive_id（分享列表项自带，同一分享内固定），编码进 fidToken
                        fidToken = item.optString("drive_id"),
                        modifyTime = item.optString("updated_at")
                    )
                )
            }
        }
        // marker 为空串表示末页；非空为下一页游标
        val nextMarker = json.optString("next_marker").takeIf { it.isNotBlank() }
        Pair(files, nextMarker)
    }

    /**
     * 分享文件下载直链：POST /v2/file/get_share_link_download_url（alist 实证）。
     * 需要登录 access_token（Authorization）+ 分享 token（x-share-token）。
     * @param file 列表项（fidToken 编码了 drive_id）
     * @return 真实 CDN 直链（下载需带 Referer: https://www.alipan.com/）
     */
    suspend fun getShareDownloadLink(
        shareId: String,
        file: ShareFile,
        shareToken: String,
        accessToken: String
    ): DownloadLink? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("drive_id", file.fidToken)
            .put("file_id", file.fid)
            .put("expire_sec", AlipanConstants.LINK_EXPIRE_SEC)
            .put("share_id", shareId)
        val request = Request.Builder()
            .url(AlipanConstants.SHARE_DOWNLOAD_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("x-share-token", shareToken)
            .header(AlipanConstants.CANARY_HEADER, AlipanConstants.CANARY_VALUE)
            .header("User-Agent", AlipanConstants.WEB_UA)
            .header("Content-Type", "application/json;charset=UTF-8")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        val json = executeJson(request)
        checkOk(json, "获取下载链接失败")
        val downloadUrl = json.optString("download_url")
            .ifBlank { json.optString("url") }
        if (downloadUrl.isBlank()) return@withContext null
        DownloadLink(
            fid = file.fid,
            filename = file.fname,
            downloadUrl = downloadUrl,
            size = file.fsize
        )
    }

    // ---------- 个人云盘（需设备签名，本次不实现） ----------

    /** 个人云盘容量：需设备签名，暂不支持；保留 null 占位（网盘页不显示容量条） */
    suspend fun getQuota(accessToken: String): QuotaInfo? = null

    // ---------- 内部工具 ----------

    /** 分享列表翻页：循环取页直到末页，返回该目录全部文件 */
    suspend fun listShareFiles(
        shareId: String,
        shareToken: String,
        parentFileId: String
    ): List<ShareFile> {
        val all = mutableListOf<ShareFile>()
        var marker = ""
        repeat(200) {   // 封顶 200 页，防异常死循环
            val (files, next) = listSharePage(shareId, shareToken, parentFileId, marker)
            all += files
            marker = next ?: return all
        }
        return all
    }

    /** 请求参数：url + body；分享免鉴权接口 needAuth=false（匿名） */
    private class AuthRequest(
        val url: String,
        val body: String,
        val needAuth: Boolean = false
    )

    /** 成功判定：alipan 错误内联在 HTTP 200 响应 JSON 的 code/message 字段 */
    private fun checkOk(json: JSONObject, fallback: String) {
        val code = json.optString("code")
        if (code.isBlank()) return   // 成功响应无 code 字段
        val msg = json.optString("message").ifBlank { fallback }
        throw IllegalStateException("$msg（$code）")
    }

    private fun post(req: AuthRequest): JSONObject {
        val builder = Request.Builder()
            .url(req.url)
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("User-Agent", AlipanConstants.WEB_UA)
        val request = builder
            .post(req.body.toRequestBody(jsonMediaType))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("请求失败：响应为空（${response.code}）")
            if (!response.isSuccessful && body.isBlank()) {
                throw IllegalStateException("请求失败（HTTP ${response.code}）")
            }
            return JSONObject(body)
        }
    }
}
