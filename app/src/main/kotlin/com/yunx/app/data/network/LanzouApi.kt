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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 蓝奏云分享解析 API 客户端（匿名）。
 * 实现参照 alist `lanzou` 驱动：
 * - acw_sc__v2 反爬：识别 arg1 挑战页 → Unbox+HexXor 计算 cookie → 带 cookie 重试；
 * - 单文件分享：分享页 → iframe 下载页 → ajaxm.php 表单 → 302 重定向取真实直链；
 * - 文件夹分享：分享页 data 参数 + 子文件夹 + filemoreajax.php 分页列表。
 */
class LanzouApi(
    clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client: OkHttpClient = run {
        // 蓝奏需要持久 cookie（acw_sc__v2 等），且下载重定向需 NoRedirect
        val base = clientProvider()
        base.newBuilder()
            .cookieJar(InMemoryCookieJar())
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** 目录/文件列表项 */
    data class Entry(
        val id: String,
        val name: String,
        val size: Long,
        val isFolder: Boolean,
        val modifyTime: String = ""
    )

    /** 下载信息（直链 + 真实文件名） */
    data class Download(
        val url: String,
        val filename: String
    )

    /** 分享根页面：scheme://host（不含路径） */
    fun baseUrlOf(sharePageUrl: String): String {
        val idx = sharePageUrl.lastIndexOf('/')
        return if (idx > "https://".length) sharePageUrl.substring(0, idx) else sharePageUrl
    }

    /** 分享根 id（路径最后一段） */
    fun idOf(sharePageUrl: String): String = sharePageUrl.substringAfterLast('/')

    // ---------- 分享列表 ----------

    /**
     * 获取分享页列表（文件或文件夹）。
     * @param pageUrl 分享页完整 URL（根：baseUrl/id；子目录：baseUrl/{href}）
     * @param pwd 提取码（可为空）
     * @return 列表项；单文件分享返回单元素列表，文件夹分享返回子目录+文件
     */
    suspend fun listShare(pageUrl: String, pwd: String?): List<Entry> = withContext(Dispatchers.IO) {
        val html = getSharePage(pageUrl)
        if (isFilePage(html)) {
            // 单文件分享：解析名称与大小
            val name = parseFileName(html) ?: "未知文件"
            val size = parseFileSize(html)
            listOf(Entry(id = idOf(pageUrl), name = name, size = size, isFolder = false, modifyTime = parseFileTime(html)))
        } else {
            listFolder(pageUrl, pwd ?: "", html)
        }
    }

    /**
     * 解析文件夹分享：子目录（分享页内嵌）+ 文件（filemoreajax 分页）。
     */
    private fun listFolder(pageUrl: String, pwd: String, html: String): List<Entry> {
        val baseUrl = baseUrlOf(pageUrl)
        val entries = mutableListOf<Entry>()

        // 子目录：href="/<id>" 带名称
        SUB_FOLDER_REGEX.findAll(html).forEach { m ->
            val href = m.groupValues[1]
            val name = m.groupValues[2].trim()
            if (href.isNotBlank() && name.isNotBlank()) {
                entries.add(Entry(id = href, name = name, size = 0, isFolder = true))
            }
        }

        // 文件：htmlJsonToMap 拿到 form 参数 + pwd + pg 分页
        val from = htmlJsonToMap(html) ?: return entries
        from["pwd"] = pwd
        var page = 1
        var hasMore = true
        while (hasMore) {
            from["pg"] = page.toString()
            val resp = postForm("$baseUrl/filemoreajax.php", from, pageUrl)
                ?: break
            val text = resp.optJSONArray("text") ?: break
            var count = 0
            for (i in 0 until text.length()) {
                val item = text.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                entries.add(
                    Entry(
                        id = id,
                        name = item.optString("name_all").ifBlank { "未知文件" },
                        size = parseSizeString(item.optString("size")),
                        isFolder = false,
                        modifyTime = item.optString("time")
                    )
                )
                count++
            }
            hasMore = count > 0
            page++
            Thread.sleep(1000) // alist 每页间隔 1s 防限
        }
        return entries
    }

    // ---------- 下载直链 ----------

    /**
     * 解析分享文件直链（单文件分享与文件夹内文件共用）。
     * @param pageUrl 该文件的分享页 URL（根单文件：baseUrl/id；文件夹内文件：baseUrl/{fileId}）
     * @param pwd 提取码（可为空）
     */
    suspend fun getDownloadUrl(pageUrl: String, pwd: String?): Download = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlOf(pageUrl)
        val sharePageData = getSharePage(pageUrl)
        resolveFileDownload(baseUrl, pageUrl, pwd ?: "", sharePageData)
    }

    private fun resolveFileDownload(baseUrl: String, pageUrl: String, pwd: String, rawHtml: String): Download {
        val html = RemoveNotes(rawHtml)
        val cleanHtml = RemoveJSComment(html)
        var name: String? = parseFileName(cleanHtml)
        var downloadUrl: String?

        // 需要密码：down_p 函数内 data 参数 + p 字段
        if (cleanHtml.contains("pwdload") || cleanHtml.contains("passwddiv")) {
            val downFn = getJSFunctionByName(cleanHtml, "down_p")
                ?: throw IOException("蓝奏云：分享页缺少密码校验函数")
            val param = htmlJsonToMap(downFn) ?: throw IOException("蓝奏云：解析密码参数失败")
            param["p"] = pwd
            val ajaxPath = ajaxPathOf(downFn)
            val resp = postAjax("$baseUrl$ajaxPath", param, pageUrl, xhr = true)
                ?: throw IOException("蓝奏云：获取下载接口失败")
            val dom = resp.optString("dom")
            val path = resp.optString("url")
            downloadUrl = "$dom/file/$path"
            val inf = resp.optString("inf")
            if (inf.isNotBlank()) name = inf
        } else {
            // 无密码：iframe 下载页 → ajaxm.php
            val urlpaths = IFrameSrcRegex.find(html)
            val iframeSrc = urlpaths?.groupValues?.getOrNull(1)
                ?: throw IOException("蓝奏云：未找到下载页面")
            val nextPageData = getHtmlSync("$baseUrl$iframeSrc")
            val param = htmlJsonToMap(nextPageData) ?: throw IOException("蓝奏云：解析下载参数失败")
            val resp = postAjax("$baseUrl${ajaxPathOf(nextPageData)}", param, pageUrl, xhr = false)
                ?: throw IOException("蓝奏云：获取下载接口失败")
            val dom = resp.optString("dom")
            val path = resp.optString("url")
            downloadUrl = "$dom/file/$path"
        }

        if (downloadUrl.isNullOrBlank()) throw IOException("蓝奏云：获取下载链接失败")

        // 302 重定向取真实直链（遇 acw 挑战则算 cookie 重试）
        val response = httpGetWithAcw(
            downloadUrl,
            mapOf("accept-language" to LanzouConstants.ACCEPT_LANGUAGE)
        )
        var body = response.body
        var realUrl = response.location
        if (realUrl.isNullOrBlank()) {
            // 非 302：可能需 el=2 二次验证
            val param = htmlJsonToMap(body) ?: throw IOException("蓝奏云：下载链接解析失败")
            param["el"] = "2"
            Thread.sleep(2000)
            val data = postForm("$baseUrl/ajax.php", param, pageUrl) ?: throw IOException("蓝奏云：下载链接获取失败")
            realUrl = data.optString("url").ifBlank { throw IOException("蓝奏云：下载链接为空") }
        }

        return Download(url = realUrl, filename = name ?: idOf(pageUrl))
    }

    // ---------- 底层请求 ----------

    private fun getSharePage(pageUrl: String): String {
        val html = getHtmlSync(pageUrl)
        if (html.contains("取消分享")) throw IOException("蓝奏云：分享已取消")
        if (html.contains("文件不存在")) throw IOException("蓝奏云：文件不存在")
        return html
    }

    private fun getHtmlSync(url: String): String = getWithAcwRetry(url, emptyMap())

    /** 请求并在命中 acw_sc__v2 挑战时计算 cookie 自动重试，返回最终响应（保留 302 Location） */
    private fun httpGetWithAcw(url: String, extraHeaders: Map<String, String>): HttpResponse {
        repeat(3) {
            val resp = httpGet(url, extraHeaders)
            if (ACW_REGEX.containsMatchIn(resp.body)) {
                val m = ACW_REGEX.find(resp.body)
                val arg = m?.groupValues?.getOrNull(1) ?: return resp
                CookieJarHolder.acw = calcAcwScV2(arg)
            } else {
                return resp
            }
        }
        return HttpResponse(0, "", null)
    }

    private fun getWithAcwRetry(url: String, extraHeaders: Map<String, String>): String {
        var acw: String? = null
        repeat(3) {
            val resp = httpGet(url, extraHeaders)
            if (ACW_REGEX.containsMatchIn(resp.body)) {
                val m = ACW_REGEX.find(resp.body)
                val arg = m?.groupValues?.getOrNull(1) ?: return resp.body
                acw = calcAcwScV2(arg)
                CookieJarHolder.acw = acw
            } else {
                return resp.body
            }
        }
        return throw IOException("蓝奏云：反爬校验失败")
    }

    private fun httpGet(url: String, extraHeaders: Map<String, String>): HttpResponse {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", LanzouConstants.UA)
            .header("Referer", LanzouConstants.REFERER_BASE)
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        CookieJarHolder.acw?.let { builder.header("Cookie", "acw_sc__v2=$it") }
        client.newCall(builder.build()).execute().use { resp ->
            val location = resp.header("Location") ?: resp.header("location")
            return HttpResponse(
                code = resp.code,
                body = resp.body?.string() ?: "",
                location = location
            )
        }
    }

    private fun postForm(url: String, form: Map<String, String>, referer: String): JSONObject? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", LanzouConstants.UA)
            .header("Referer", referer)
        val body = form.entries.joinToString("&") { (k, v) -> "${k.encode()}=${v.encode()}" }
        builder.header("Content-Type", "application/x-www-form-urlencoded")
            .post(okhttp3.RequestBody.create("application/x-www-form-urlencoded".toMediaType(), body))
        CookieJarHolder.acw?.let { builder.header("Cookie", "acw_sc__v2=$it") }
        client.newCall(builder.build()).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: return null
            return runCatching { JSONObject(bodyStr) }.getOrNull()
        }
    }

    private fun postAjax(url: String, form: Map<String, String>, referer: String, xhr: Boolean): JSONObject? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", LanzouConstants.UA)
            .header("Referer", referer)
            .header("Origin", baseUrlOf(referer))
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
        if (xhr) builder.header("X-Requested-With", "XMLHttpRequest")
        val body = form.entries.joinToString("&") { (k, v) -> "${k.encode()}=${v.encode()}" }
        builder.header("Content-Type", "application/x-www-form-urlencoded")
            .post(okhttp3.RequestBody.create("application/x-www-form-urlencoded".toMediaType(), body))
        CookieJarHolder.acw?.let { builder.header("Cookie", "acw_sc__v2=$it") }
        client.newCall(builder.build()).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: return null
            return runCatching { JSONObject(bodyStr) }.getOrNull()
        }
    }

    private fun String.encode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }

    private data class HttpResponse(val code: Int, val body: String, val location: String?)

    // ---------- acw_sc__v2 算法（alist help.go 移植） ----------

    private fun calcAcwScV2(arg: String): String = hexXor(unbox(arg), LanzouConstants.ACW_KEY)

    private fun unbox(hex: String): String {
        val box = intArrayOf(6, 28, 34, 31, 33, 18, 30, 23, 9, 8, 19, 38, 17, 24, 0, 5, 32, 21, 10, 22, 25, 14, 15, 3, 16, 27, 13, 35, 2, 29, 11, 26, 4, 36, 1, 39, 37, 7, 20, 12)
        val newBox = CharArray(hex.length)
        for (i in box.indices) {
            val j = box[i]
            if (j < newBox.size) newBox[j] = hex[i]
        }
        return String(newBox)
    }

    private fun hexXor(hex1: String, hex2: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 2 <= hex1.length && i + 2 <= hex2.length) {
            val v1 = hex1.substring(i, i + 2).toInt(16)
            val v2 = hex2.substring(i, i + 2).toInt(16)
            sb.append(String.format("%02x", v1 xor v2))
            i += 2
        }
        return sb.toString()
    }

    // ---------- HTML 解析 ----------

    /** 页面是否单文件分享 */
    private fun isFilePage(html: String): Boolean =
        html.contains("class=\"fileinfo\"") || html.contains("id=\"file\"") || html.contains("文件描述")

    /** 移除 HTML 注释与 JS 注释（alist RemoveNotes） */
    private fun RemoveNotes(html: String): String {
        return REMOVE_NOTES_REGEX.replace(html) { m ->
            val v = m.value
            if (v.length >= 3 && v.startsWith("//")) v.substring(0, 1) else "\n"
        }
    }

    /** 移除 JS 块注释/行注释（alist RemoveJSComment） */
    private fun RemoveJSComment(data: String): String {
        val sb = StringBuilder()
        var inBlock = false
        var inLine = false
        var i = 0
        while (i < data.length) {
            val c = data[i]
            if (inLine) {
                if (c == '\n' || c == '\r') { inLine = false; sb.append(c) }
                i++
                continue
            }
            if (inBlock) {
                if (c == '*' && i + 1 < data.length && data[i + 1] == '/') { inBlock = false; i += 2; continue }
                i++
                continue
            }
            if (c == '/' && i + 1 < data.length) {
                val next = data[i + 1]
                if (next == '*') { inBlock = true; i += 2; continue }
                if (next == '/') { inLine = true; i += 2; continue }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** 取 JS 全局方法体（alist getJSFunctionByName） */
    private fun getJSFunctionByName(html: String, name: String): String? {
        JS_FUNC_ALL_REGEX.findAll(html).forEach { m ->
            val fn = m.value
            if (Regex("function\\s+$name[()\\s]+\\{").containsMatchIn(fn)) return fn
        }
        return null
    }

    /** 解析 JS 变量 `var key = 'value'` */
    private fun findJSVar(key: String, data: String): String {
        val reg = Regex("""var\s+${Regex.escape(key)}\s*=\s*(?:'([^']*)'|"([^"]*)"|([^;\s]+))\s*;""")
        val matches = reg.findAll(data).toList()
        fun value(m: MatchResult): String = (1..3).firstNotNullOfOrNull { m.groupValues[it].ifBlank { null } } ?: ""
        if (key == "sasign" && matches.size == 3) return value(matches[1])
        for (m in matches.asReversed()) {
            val v = value(m)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    /** 解析 html 中 `data:{...}` 内嵌 KV 到 map（alist htmlJsonToMap + jsonToMap） */
    private fun htmlJsonToMap(html: String): MutableMap<String, String>? {
        val m = DATA_REGEX.find(html) ?: return null
        val data = m.groupValues[1]
        val param = mutableMapOf<String, String>()
        KV_REGEX.findAll(data).forEach { kv ->
            val k = kv.groupValues[1]
            val v = kv.groupValues[3]
            if (v.isEmpty() || kv.groupValues[2].contains("'") || v.toLongOrNull() != null) {
                param[k] = v
            } else {
                param[k] = findJSVar(v, html)
            }
        }
        return param
    }

    /** ajaxm/ajaxfile 路径或兜底 */
    private fun ajaxPathOf(data: String): String {
        AJAX_PATH_REGEX.find(data)?.groupValues?.getOrNull(1)?.let { return it }
        FILE_ID_VAR_REGEX.find(data)?.groupValues?.getOrNull(1)?.let { return "/ajaxm.php?file=$it" }
        return "/ajaxm.php"
    }

    private fun parseFileName(html: String): String? {
        val m = NAME_REGEX.find(html) ?: return null
        for (i in 1..m.groupValues.size - 1) {
            val v = m.groupValues[i].trim()
            if (v.isNotBlank()) return v
        }
        return null
    }

    private fun parseFileSize(html: String): Long {
        val m = SIZE_REGEX.find(html) ?: return 0
        return parseSizeString(m.groupValues[1])
    }

    private fun parseFileTime(html: String): String = TIME_REGEX.find(html)?.value ?: ""

    /** 解析 "12.5MB"/"1.2GB"/"300KB" 字符串为字节数 */
    private fun parseSizeString(size: String): Long {
        val m = SIZE_SPLIT_REGEX.find(size.trim()) ?: return 0
        val num = m.groupValues[1].toDoubleOrNull() ?: return 0
        return when (m.groupValues[2].uppercase()) {
            "B" -> num.toLong()
            "K" -> (num * (1 shl 10)).toLong()
            "M" -> (num * (1 shl 20)).toLong()
            "G" -> (num * (1 shl 30)).toLong()
            else -> 0
        }
    }

    companion object {
        private val ACW_REGEX = Regex(LanzouConstants.ACW_ARG_REGEX)
        private val SUB_FOLDER_REGEX = Regex("""(?i)(?:folderlink|mbxfolder).+?href="/(.+?)"(?:.+?filename")?>(.+?)<""")
        private val IFrameSrcRegex = Regex("""<iframe.*?src="(.+?)"""")
        private val AJAX_PATH_REGEX = Regex("""(?i)(/ajax(?:m|file)\.php\?file=\d+\b)""")
        private val FILE_ID_VAR_REGEX = Regex("""(?i)\b(?:f_id|fid)\s*=\s*['"]?(\d+)['"]?\s*;""")
        private val REMOVE_NOTES_REGEX = Regex("""<!--.*?-->|[^:]//.*|/\*.*?\*/""")
        private val JS_FUNC_ALL_REGEX = Regex("""(?is)function[^{]+""")
        private val DATA_REGEX = Regex("""data[:\s]+(\{[^}]+\})""")
        private val KV_REGEX = Regex("""'(.+?)':('?([^' },]*)'?)""")
        private val NAME_REGEX = Regex(
            """<title>(.+?) - 蓝奏云</title>|id="filenajax">(.+?)</div>|var filename = '(.+?)';|<div style="font-size.+?>([^<>].+?)</div>|<div class="filethetext".+?>([^<>]+?)</div>"""
        )
        private val SIZE_REGEX = Regex("""(?i)大小\W*([0-9.]+\s*[bkm]+)""")
        private val TIME_REGEX = Regex("""\d+\s*[秒天分小][钟时]?前|[昨前]天|\d{4}-\d{2}-\d{2}""")
        private val SIZE_SPLIT_REGEX = Regex("""(?i)([0-9.]+)\s*([bkmg]+)""")
    }
}

/** 内存 Cookie 存储（acw_sc__v2 反爬 Cookie 持久） */
private object CookieJarHolder {
    var acw: String? = null
}

/** 简化内存 CookieJar：仅透传 cookie 请求头由 OkHttp 管理（本项目手动注入，这里保持空实现避免报错） */
private class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies.toList()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
}
