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

import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 115 网盘 API 客户端。
 * 实现参照 alist `115_share` 驱动 + SheltonZhu/115driver 包。
 *
 * 分享解析（匿名）：snap 列表 + downurl 直链；
 * 登录（仅二维码）：token → 扫码 → status 轮询 → login 换 Cookie（UID/CID/SEID/KID）。
 * 注意：snap 列表匿名可用，但 downurl 直链需要登录 Cookie（实测返回「登录超时」）。
 */
class P115Api(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    /** 二维码会话（token 获取后用于展示与轮询） */
    data class QrSession(
        val qrContent: String,
        val sign: String,
        val time: Long,
        val uid: String
    )

    /** 二维码授权状态 */
    enum class QrStatus { WAITING, SCANNED, ALLOWED, EXPIRED, CANCELED, UNKNOWN }

    // ---------- 二维码登录 ----------

    /** 获取二维码会话：qrcode 内容（可渲染为二维码）+ uid/sign/time（轮询用） */
    suspend fun getQrSession(): QrSession = withContext(Dispatchers.IO) {
        val json = executeJson(
            Request.Builder()
                .url(P115Constants.QRCODE_TOKEN_URL)
                .header("User-Agent", P115Constants.WEB_UA)
                .build()
        )
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException(json.optString("message").ifBlank { "获取二维码失败" })
        QrSession(
            qrContent = data.optString("qrcode"),
            sign = data.optString("sign"),
            time = data.optLong("time"),
            uid = data.optString("uid")
        )
    }

    /** 轮询二维码状态 */
    suspend fun getQrStatus(session: QrSession): QrStatus = withContext(Dispatchers.IO) {
        val url = P115Constants.QRCODE_STATUS_URL +
            "?uid=${session.uid}&time=${session.time}&sign=${session.sign}"
        val json = executeJson(
            Request.Builder()
                .url(url)
                .header("User-Agent", P115Constants.WEB_UA)
                .build()
        )
        val data = json.optJSONObject("data") ?: return@withContext QrStatus.UNKNOWN
        when (data.optInt("status")) {
            0 -> QrStatus.WAITING
            1 -> QrStatus.SCANNED
            2 -> QrStatus.ALLOWED
            -1 -> QrStatus.EXPIRED
            -2 -> QrStatus.CANCELED
            else -> QrStatus.UNKNOWN
        }
    }

    /**
     * 扫码授权确认后换 Cookie：POST /app/1.0/web/1.0/login/qrcode（form: account=uid&app=web）。
     * 返回完整 Cookie 串（UID=..;CID=..;SEID=..;KID=..），失败抛错（未授权时提示扫码确认）。
     */
    suspend fun qrLogin(session: QrSession): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("account", session.uid)
            .add("app", "web")
            .build()
        val json = executeJson(
            Request.Builder()
                .url(P115Constants.QRCODE_LOGIN_URL)
                .header("User-Agent", P115Constants.WEB_UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build()
        )
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException(json.optString("error").ifBlank { "扫码登录失败" })
        val cookie = data.optJSONObject("cookie") ?: throw IllegalStateException("扫码登录返回数据异常")
        val uid = cookie.optString(P115Constants.COOKIE_UID)
        val cid = cookie.optString(P115Constants.COOKIE_CID)
        val seid = cookie.optString(P115Constants.COOKIE_SEID)
        val kid = cookie.optString(P115Constants.COOKIE_KID)
        if (cid.isBlank() || seid.isBlank()) throw IllegalStateException("扫码登录未获取到有效会话")
        buildString {
            append("${P115Constants.COOKIE_UID}=$uid;")
            append("${P115Constants.COOKIE_CID}=$cid;")
            append("${P115Constants.COOKIE_SEID}=$seid;")
            append("${P115Constants.COOKIE_KID}=$kid")
        }
    }

    // ---------- 分享解析 ----------

    /**
     * 单页分享文件列表：GET /webapi/share/snap（匿名，需完整 UA + referer）。
     * @param shareCode 分享码（115.com/s/{code}）
     * @param receiveCode 提取码
     * @param dirId 目录 cid；根目录传空串
     * @param offset 翻页偏移
     * @return (文件列表, 是否还有更多)
     */
    suspend fun listSharePage(
        shareCode: String,
        receiveCode: String,
        dirId: String,
        offset: Int
    ): Pair<List<ShareFile>, Boolean> = withContext(Dispatchers.IO) {
        val url = P115Constants.SHARE_SNAP_URL +
            "?share_code=$shareCode&receive_code=$receiveCode" +
            "&cid=$dirId&limit=${P115Constants.PAGE_SIZE}&asc=0&offset=$offset&format=json"
        val json = executeJson(
            shareRequest(url)
        )
        checkState(json, "获取分享列表失败")
        val data = json.optJSONObject("data") ?: return@withContext Pair(emptyList(), false)
        val total = data.optInt("count")
        val arr = data.optJSONArray("list") ?: JSONObject.NULL
        val files = buildList {
            if (arr is org.json.JSONArray) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    // fc=0 目录（无 fid 字段，用 cid 作 fid），fc=1 文件（用 fid 字段）
                    val isDir = item.optInt("fc") == 0
                    add(
                        ShareFile(
                            fid = if (isDir) item.optString("cid") else item.optString("fid"),
                            fname = item.optString("n"),
                            fsize = item.optLong("s"),
                            isdir = isDir,
                            pdirFid = item.optString("pid"),
                            fidToken = "",
                            modifyTime = item.optString("t")
                        )
                    )
                }
            }
        }
        Pair(files, offset + files.size < total)
    }

    /** 分享列表翻页：循环取页直到末页，返回该目录全部文件 */
    suspend fun listShareFiles(
        shareCode: String,
        receiveCode: String,
        dirId: String
    ): List<ShareFile> {
        val all = mutableListOf<ShareFile>()
        var offset = 0
        repeat(200) {   // 封顶 200 页，防异常死循环
            val (files, hasMore) = listSharePage(shareCode, receiveCode, dirId, offset)
            all += files
            if (!hasMore) return all
            offset += files.size
        }
        return all
    }

    /**
     * 分享文件下载直链：GET /webapi/share/downurl（需登录 Cookie）。
     * @param file 列表项（fid 即 115 文件 fid）
     * @return 真实下载直链；下载时需带 Referer（115cdn 分享页）与完整 UA
     */
    suspend fun getShareDownloadLink(
        shareCode: String,
        receiveCode: String,
        file: ShareFile
    ): String = withContext(Dispatchers.IO) {
        val url = P115Constants.SHARE_DOWNURL_URL +
            "?share_code=$shareCode&receive_code=$receiveCode&file_id=${file.fid}&dl=1"
        val json = executeJson(
            shareRequest(url)
        )
        checkState(json, "获取下载链接失败")
        val data = json.optJSONObject("data") ?: throw IllegalStateException("获取下载链接失败")
        data.optString("url")
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("获取下载链接失败")
    }

    // ---------- 网盘空间详情 ----------

    /** 网盘空间详情：GET /files/index_info（需登录 Cookie；data.space_info.all_total.size 总容量 / all_use.size 已用，字节） */
    suspend fun getQuota(cookie: String): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val json = executeJson(
                Request.Builder()
                    .url(P115Constants.INDEX_INFO_URL)
                    .header("User-Agent", P115Constants.WEB_UA)
                    .header("Cookie", cookie)
                    .build()
            )
            checkState(json, "获取空间详情失败")
            val space = json.optJSONObject("data")?.optJSONObject("space_info")
                ?: return@runCatching null
            val total = space.optJSONObject("all_total")?.optLong("size") ?: 0L
            val used = space.optJSONObject("all_use")?.optLong("size") ?: 0L
            if (total <= 0) null else QuotaInfo(used = used, total = total)
        }.getOrNull()
    }

    // ---------- 内部工具 ----------

    /** 115 分享接口请求：完整 UA + 分享页 referer（缺 UA 返回 405；referer 缺失时分享接口 401） */
    private fun shareRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", P115Constants.WEB_UA)
        .header("Referer", P115Constants.SHARE_API_BASE)
        .header("Accept", "application/json")
        .build()

    /** 成功判定：115 用 state=true 表示成功，false 时 error/errno 给出原因 */
    private fun checkState(json: JSONObject, fallback: String) {
        if (json.optBoolean("state", false)) return
        val err = json.optString("error").ifBlank { fallback }
        throw IllegalStateException(err)
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
