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

package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.P115AccountEntity
import com.yunx.app.data.repository.P115AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 115 网盘账号 ViewModel：二维码扫码登录状态管理，暴露登录态供主页/解析页共享。
 */
class P115AccountViewModel(
    private val repository: P115AccountRepository
) : ViewModel() {

    val p115Account: StateFlow<P115AccountEntity?> = repository.observeAccount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** 扫码登录状态（二维码页轮询结果） */
    private val _qrStatus = MutableStateFlow<String?>(null)
    val qrStatus: StateFlow<String?> = _qrStatus.asStateFlow()

    /** 保存二维码授权 Cookie；返回是否保存成功 */
    suspend fun saveCookie(cookie: String, nickname: String = ""): Boolean =
        repository.saveCookie(cookie, nickname)

    fun setQrStatus(msg: String?) {
        _qrStatus.value = msg
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    class Factory(
        private val repository: P115AccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(P115AccountViewModel::class.java))
            return P115AccountViewModel(repository) as T
        }
    }
}
