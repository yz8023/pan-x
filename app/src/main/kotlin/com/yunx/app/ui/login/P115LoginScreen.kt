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

package com.yunx.app.ui.login

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.yunx.app.data.network.P115Api
import com.yunx.app.data.network.P115Constants
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.viewmodel.P115AccountViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 115 网盘扫码登录页：
 * - 进入即获取二维码会话（token），WebView 加载 115 官方二维码图片；
 * - 轮询二维码状态（等待/已扫/已授权），授权后换 Cookie 落库自动完成登录；
 * - 支持手动刷新二维码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P115LoginScreen(
    viewModel: P115AccountViewModel,
    api: P115Api,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var qrContent by remember { mutableStateOf<String?>(null) }
    var qrSession by remember { mutableStateOf<P115Api.QrSession?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码…") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var useQrLogin by remember { mutableStateOf(true) }
    var cookieInput by remember { mutableStateOf("") }

    /** 校验并保存粘贴的 Cookie */
    fun loginWithCookie() {
        val cookie = cookieInput.trim()
        if (cookie.isBlank()) {
            statusText = "请先粘贴 Cookie"
            viewModel.setQrStatus(statusText)
            return
        }
        scope.launch {
            isSaving = true
            statusText = "正在校验 Cookie…"
            viewModel.setQrStatus(statusText)
            runCatching { api.validateCookie(cookie) }
                .onSuccess { valid ->
                    if (!valid) {
                        statusText = "Cookie 无效或已过期，请重新从浏览器复制"
                        viewModel.setQrStatus(statusText)
                        isSaving = false
                        return@onSuccess
                    }
                    val saved = viewModel.saveCookie(cookie)
                    isSaving = false
                    if (saved) {
                        SnackbarController.show("115 网盘登录成功")
                        onSaved()
                    } else {
                        statusText = "登录态保存失败，请重试"
                        viewModel.setQrStatus(statusText)
                    }
                }
                .onFailure {
                    isSaving = false
                    statusText = "Cookie 校验失败：${it.message}"
                    viewModel.setQrStatus(statusText)
                }
        }
    }

    /** 获取二维码会话并加载图片 */
    fun loadQr() {
        scope.launch {
            isLoading = true
            statusText = "正在获取二维码…"
            runCatching { api.getQrSession() }
                .onSuccess {
                    qrSession = it
                    qrContent = it.qrContent
                    statusText = "请使用 115 客户端或 115 网页扫码登录"
                    viewModel.setQrStatus(statusText)
                }
                .onFailure {
                    statusText = "获取二维码失败：${it.message}"
                    viewModel.setQrStatus(statusText)
                }
            isLoading = false
        }
    }

    // 进入即获取二维码
    LaunchedEffect(Unit) { loadQr() }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.WHITE)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 图片加载完成
                }
            }
        }
    }

    // 二维码图片随会话刷新
    LaunchedEffect(qrSession) {
        qrSession?.let {
            webView.loadUrl(String.format(P115Constants.QRCODE_IMAGE_URL, it.uid))
        }
    }

    // 页面销毁时释放 WebView
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    // 轮询二维码状态：等待 → 已扫 → 已授权 → 换 Cookie 登录
    LaunchedEffect(qrSession) {
        val session = qrSession ?: return@LaunchedEffect
        var expired = false
        var start = System.currentTimeMillis()
        while (!expired) {
            delay(P115Constants.QRCODE_POLL_INTERVAL_MS)
            val status = runCatching { api.getQrStatus(session) }.getOrNull() ?: continue
            when (status) {
                P115Api.QrStatus.WAITING -> {
                    if (System.currentTimeMillis() - start > P115Constants.QRCODE_EXPIRE_SEC * 1000) {
                        statusText = "二维码已过期，请点击刷新"
                        expired = true
                    } else {
                        statusText = "请使用 115 客户端或 115 网页扫码登录"
                    }
                }
                P115Api.QrStatus.SCANNED -> statusText = "已扫码，请在手机上确认登录"
                P115Api.QrStatus.ALLOWED -> {
                    statusText = "登录成功，正在保存…"
                    isSaving = true
                    runCatching { api.qrLogin(session) }
                        .onSuccess { cookie ->
                            val saved = viewModel.saveCookie(cookie)
                            isSaving = false
                            if (saved) {
                                SnackbarController.show("115 网盘登录成功")
                                onSaved()
                            } else {
                                statusText = "登录态保存失败，请重试"
                                viewModel.setQrStatus(statusText)
                                isSaving = false
                            }
                        }
                        .onFailure {
                            isSaving = false
                            statusText = "登录失败：${it.message}"
                            viewModel.setQrStatus(statusText)
                        }
                    expired = true
                }
                P115Api.QrStatus.EXPIRED -> {
                    statusText = "二维码已过期，请点击刷新"
                    expired = true
                }
                P115Api.QrStatus.CANCELED -> {
                    statusText = "已取消登录，请重新扫码"
                }
                else -> {
                    // UNKNOWN：继续轮询
                }
            }
            viewModel.setQrStatus(statusText)
        }
    }

    // 系统返回键 → 返回主页（保存中禁用）
    BackHandler(enabled = !isSaving) { onBack() }

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("115 网盘登录", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadQr() },
                        enabled = !isSaving
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "刷新二维码",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "使用 115 客户端 / 网页扫码登录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "仅用于获取下载直链，凭证本地加密保存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = useQrLogin,
                    onClick = { useQrLogin = true },
                    label = { Text("扫码登录") },
                    enabled = !isSaving
                )
                FilterChip(
                    selected = !useQrLogin,
                    onClick = { useQrLogin = false },
                    label = { Text("粘贴 Cookie") },
                    enabled = !isSaving
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (useQrLogin) {
                // 二维码卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || qrContent == null) {
                            CircularProgressIndicator()
                        } else {
                            AndroidView(
                                factory = { webView },
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 状态提示
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { loadQr() },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("刷新二维码")
                }
            } else {
                // 粘贴 Cookie 登录
                Text(
                    text = "在电脑浏览器登录 https://115.com 后，从开发者工具（F12）或 Cookie 插件复制完整 Cookie（含 UID/CID/SEID/KID 等字段）粘贴到下方：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("115 Cookie") },
                    minLines = 3,
                    enabled = !isSaving
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clip.primaryClip?.getItemAt(0)?.text?.let { cookieInput = it.toString() }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("从剪贴板粘贴")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { loginWithCookie() },
                    enabled = !isSaving && cookieInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "登录中…" else "登录")
                }
            }
        }
    }
}
