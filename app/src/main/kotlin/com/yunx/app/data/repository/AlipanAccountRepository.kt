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

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.yunx.app.data.db.AlipanAccountDao
import com.yunx.app.data.db.AlipanAccountEntity
import com.yunx.app.data.network.AlipanApi
import com.yunx.app.data.network.AlipanConstants
import kotlinx.coroutines.flow.Flow

/**
 * 阿里云盘账号仓库：网页登录提取 refresh_token 落库，API 请求前惰性换 access_token。
 * 凭证 = refresh_token（网页登录 localStorage `token` JSON 中提取）；access_token 由 refresh_token 换新。
 */
class AlipanAccountRepository(
    private val dao: AlipanAccountDao,
    private val api: AlipanApi
) {

    /** 上次主动刷新 access_token 的时间戳（进程内；跨进程重启后首次取链会因间隔超时触发刷新） */
    private var lastRefreshTs = 0L

    fun observeAccount(): Flow<AlipanAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): AlipanAccountEntity? = dao.getAccount()

    /**
     * 网页登录凭证（refresh_token）校验并落库：先换 access_token 确认有效并取昵称，成功返回 true。
     * @param refreshToken 阿里云盘网页 localStorage `token` JSON 中的 refresh_token
     */
    suspend fun saveRefreshToken(refreshToken: String): Boolean {
        val t = refreshToken.trim()
        if (t.isBlank()) return false
        val pair = api.refreshToken(t) ?: return false
        dao.upsert(
            AlipanAccountEntity(
                id = "alipan",
                accessToken = pair.accessToken,
                refreshToken = pair.refreshToken,
                account = "",
                nickname = pair.nickname.ifBlank { "阿里云盘用户" }
            )
        )
        // 重置刷新计时：新登录的 access_token 是新鲜的，避免立刻触发一次无谓刷新
        lastRefreshTs = System.currentTimeMillis()
        return true
    }

    /**
     * 返回「保证未过期」的 access_token（分享取链前调用）：
     * - 距上次刷新超过 ACCESS_TOKEN_REFRESH_INTERVAL_MS 时，先用 refresh_token 换新再落库；
     * - 刷新失败则回退返回当前 access_token（不让取链直接崩）；无账号返回 null。
     */
    suspend fun getFreshAccessToken(): String? {
        val acc = dao.getAccount() ?: return null
        val need = System.currentTimeMillis() - lastRefreshTs > AlipanConstants.ACCESS_TOKEN_REFRESH_INTERVAL_MS
        if (!need) return acc.accessToken
        val pair = api.refreshToken(acc.refreshToken)
        return if (pair != null) {
            dao.upsert(
                acc.copy(
                    accessToken = pair.accessToken,
                    refreshToken = pair.refreshToken,
                    nickname = pair.nickname.ifBlank { acc.nickname },
                    updatedAt = System.currentTimeMillis()
                )
            )
            lastRefreshTs = System.currentTimeMillis()
            pair.accessToken
        } else {
            acc.accessToken
        }
    }

    /** 校验当前 refresh_token 是否仍有效（失败自动清库，下次重新登录） */
    suspend fun validate(): Boolean {
        val acc = dao.getAccount() ?: return false
        val ok = api.refreshToken(acc.refreshToken) != null
        if (!ok) dao.clear()
        return ok
    }

    /**
     * 退出登录：清库 + 清理 WebView 登录态（Cookie 与 localStorage/DOM 存储）。
     * 网页登录态保存在 WebView 里；不清理的话，用户再次打开登录页时，页面残留的
     * token JSON 会被自动登录检测直接登回旧账号，导致「退出登录」形同虚设。
     * WebView 存储方法须在带 Looper 的线程（主线程）调用——本方法由 viewModelScope（Main）执行。
     */
    suspend fun logout() {
        // 清 Cookie：顺带清掉 WebView 里其他平台的会话（与百度/夸克等登出行为一致，均为全量清理）
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        // 清 localStorage/DOM 存储：token JSON 就存在这里
        runCatching { WebStorage.getInstance().deleteAllData() }
        dao.clear()
    }
}
