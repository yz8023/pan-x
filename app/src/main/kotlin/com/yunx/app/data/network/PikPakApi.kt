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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * PikPak 分享解析 API 客户端（匿名，Web 平台设备签名）。
 * 实现参照 alist `pikpak_share` 驱动：
 * - captcha token：POST /v1/shield/captcha/init（设备签名 + 算法链 captcha_sign）；
 * - 分享列表：GET /drive/v1/share/detail（需提取码 → pass_code_token）；
 * - 下载直链：GET /drive/v1/share/file_info（web_content_link 或 medias 转码地址）。
 */
class PikPakApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    private val deviceId: String = UUID.randomUUID().toString().replace("-", "")
    private val deviceSign: String = generateDeviceSign()

    /** 分享文件项 */
    data class File(
        val id: String,
        val name: String,
        val size: Long,
        val isFolder: Boolean,
        val modifyTime: String = ""
    )

    /** 下载信息 */
    data class Download(
        val url: String,
        val filename: String
    )

    /**
     * 获取分享元信息：带提取码时换取 pass_code_token（列表/下载请求头携带）。
     * @param shareId 分享 ID
     * @param pwd 提取码（可为空）
     */
    suspend fun getSharePassToken(shareId: String, pwd: String?): String = withContext(Dispatchers.IO) {
        val pwdStr = pwd.orEmpty()
        val url = PikPakConstants.SHARE_URL +
            "?share_id=$shareId&pass_code=$pwdStr&thumbnail_size=SIZE_LARGE&limit=${PikPakConstants.PAGE_SIZE}"
        val json = executeJson(request(url).get().build())
        val status = json.optString("share_status")
        if (status != "OK") {
            if (status == "PASS_CODE_EMPTY" || status == "PASS_CODE_ERROR") {
                throw IllegalStateException("提取码错误")
            }
            throw IllegalStateException(json.optString("share_status_text").ifBlank { "获取分享失败" })
        }
        json.optString("pass_code_token")
    }

    /**
     * 分享列表/详情（带 pass_code_token；分页直到末页）。
     * @param shareId 分享 ID
     * @param passCodeToken 提取码换取的 token
     * @param dirId 目录 fid；根目录传空串
     */
    suspend fun listShareFiles(shareId: String, passCodeToken: String, dirId: String): List<File> =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<File>()
            var pageToken = ""
            repeat(200) {
                val url = PikPakConstants.SHARE_DETAIL_URL +
                    "?parent_id=$dirId&share_id=$shareId&thumbnail_size=SIZE_LARGE" +
                    "&with_audit=true&limit=${PikPakConstants.PAGE_SIZE}" +
                    "&filters=" + "{\"phase\":{\"eq\":\"PHASE_TYPE_COMPLETE\"},\"trashed\":{\"eq\":false}}".encode() +
                    (if (pageToken.isBlank()) "" else "&page_token=$pageToken") +
                    "&pass_code_token=$passCodeToken"
                val json = executeJson(request(url).get().build())
                val status = json.optString("share_status")
                if (status != "OK") {
                    throw IllegalStateException(
                        json.optString("share_status_text").ifBlank { "获取分享列表失败" }
                    )
                }
                val arr = json.optJSONArray("files") ?: org.json.JSONObject.NULL
                if (arr is org.json.JSONArray) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val kind = item.optString("kind")
                        all.add(
                            File(
                                id = item.optString("id"),
                                name = item.optString("name"),
                                size = item.optString("size").toLongOrNull() ?: 0L,
                                isFolder = kind == "drive#folder",
                                modifyTime = item.optString("modified_time")
                            )
                        )
                    }
                }
                pageToken = json.optString("next_page_token")
                if (pageToken.isBlank()) return@withContext all
            }
            all
        }

    /**
     * 分享文件下载直链：web_content_link 优先，缺则用第一个 media 转码地址。
     */
    suspend fun getDownloadLink(shareId: String, passCodeToken: String, fileId: String): Download =
        withContext(Dispatchers.IO) {
            val url = PikPakConstants.FILE_INFO_URL +
                "?share_id=$shareId&file_id=$fileId&pass_code_token=$passCodeToken"
            val json = executeJson(request(url).get().build())
            val fileInfo = json.optJSONObject("file_info") ?: throw IllegalStateException("获取下载链接失败")
            val webContent = fileInfo.optString("web_content_link")
            val downloadUrl = if (webContent.isNotBlank()) {
                webContent
            } else {
                fileInfo.optJSONArray("medias")?.optJSONObject(0)
                    ?.optJSONObject("link")?.optString("url")
                    ?: throw IllegalStateException("获取下载链接失败")
            }
            Download(
                url = downloadUrl,
                filename = fileInfo.optString("name").ifBlank { "unknown" }
            )
        }

    // ---------- 设备签名 & captcha ----------

    /** devicesign：div101.{deviceId}{md5(sha1(deviceId + package + 1 + appkey))}（alist generateDeviceSign） */
    private fun generateDeviceSign(): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest("$deviceId${PikPakConstants.PACKAGE_NAME}1${PikPakConstants.DEVICE_SIGN_KEY}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val md5 = md5Hex(sha1)
        return "div101.$deviceId$md5"
    }

    /** captcha_sign：client_id+client_version+package_name+device_id+timestamp → 算法链 md5，前缀 "1." */
    private fun captchaSign(tsMs: String): String {
        var h = PikPakConstants.CLIENT_ID + PikPakConstants.CLIENT_VERSION +
            PikPakConstants.PACKAGE_NAME + deviceId + tsMs
        for (salt in PikPakConstants.ALGORITHMS) {
            h = md5Hex(h + salt)
        }
        return "1.$h"
    }

    /**
     * 获取 captcha token（每次解析开始调用一次）。
     * @param action 格式 "GET:/drive/v1/share:batch_file_info"
     */
    suspend fun refreshCaptchaToken(action: String): String = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis().toString()
        val sign = captchaSign(ts)
        val body = JSONObject()
            .put("action", action)
            .put("captcha_token", "")
            .put("client_id", PikPakConstants.CLIENT_ID)
            .put("device_id", deviceId)
            .put(
                "meta",
                JSONObject()
                    .put("client_version", PikPakConstants.CLIENT_VERSION)
                    .put("package_name", PikPakConstants.PACKAGE_NAME)
                    .put("user_id", "")
                    .put("timestamp", ts)
                    .put("captcha_sign", sign)
            )
            .toString()
        val json = executeJson(
            Request.Builder()
                .url(PikPakConstants.CAPTCHA_INIT_URL)
                .header("User-Agent", PikPakConstants.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("X-Client-ID", PikPakConstants.CLIENT_ID)
                .header("X-Device-ID", deviceId)
                .post(body.toRequestBody(jsonMediaType))
                .build()
        )
        json.optString("captcha_token")
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("获取验证 token 失败")
    }

    // ---------- 内部工具 ----------

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", PikPakConstants.USER_AGENT)
        .header("X-Client-ID", PikPakConstants.CLIENT_ID)
        .header("X-Device-ID", deviceId)
        .header("X-Captcha-Token", captchaToken)

    private var captchaToken: String = ""

    fun setCaptchaToken(token: String) {
        captchaToken = token
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun executeJson(request: Request): JSONObject = withContext(Dispatchers.IO) {
        val body = requestBody(request) ?: throw IllegalStateException("请求失败（HTTP 空响应）")
        val json = parseJson(body)
        val code = json.optLong("error_code", -1L)
        if (code != 9L) {
            if (code == 10L) throw IllegalStateException("操作频繁，请稍后再试")
            if (code != 0L && json.has("error_code")) {
                throw IllegalStateException(
                    json.optString("error_description").ifBlank { "请求失败（code $code）" }
                )
            }
            return@withContext json
        }
        // captcha token 过期：刷新后重试一次
        refreshCaptchaToken(ACTION)
        val retryBody = requestBody(request) ?: throw IllegalStateException("重试失败")
        val retryJson = parseJson(retryBody)
        val retryCode = retryJson.optLong("error_code", -1L)
        if (retryCode == 10L) throw IllegalStateException("操作频繁，请稍后再试")
        if (retryCode != 0L && retryJson.has("error_code")) {
            throw IllegalStateException(
                retryJson.optString("error_description").ifBlank { "请求失败（code $retryCode）" }
            )
        }
        retryJson
    }

    private fun requestBody(request: Request): String? =
        client.newCall(request).execute().use { response -> response.body?.string() }

    private fun parseJson(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("请求失败：响应解析失败")

    companion object {
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val ACTION = "GET:/drive/v1/share:batch_file_info"
    }
}
