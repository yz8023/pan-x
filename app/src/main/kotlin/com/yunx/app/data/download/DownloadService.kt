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

package com.yunx.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.yunx.app.MainActivity
import com.yunx.app.R

/**
 * 下载前台服务：下载进行中保持前台运行。
 * 前台服务让系统将应用视为「前台」，避免 Doze/后台省电限速、防止进程被杀，
 * 从而保证切后台后下载速度不受影响。
 * 生命周期由 DownloadManager 驱动：任务开始 → start()，全部结束 → stop()。
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android 12+ 要求 startForegroundService 后必须在超时窗口内调用 startForeground，
        // 否则抛 ForegroundServiceDidNotStartInTimeException。任务可能极快完成/失败，
        // stopService 会先于 onStartCommand 触发，因此必须在 onCreate 尽早提升为前台。
        startAsForeground("下载中…", -1, "", true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "下载中…"
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1
                val speed = intent?.getStringExtra(EXTRA_SPEED) ?: ""
                val showSpeed = intent?.getBooleanExtra(EXTRA_SHOW_SPEED, true) ?: true
                startAsForeground(title, progress, speed, showSpeed)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun startAsForeground(title: String, progress: Int, speed: String, showSpeed: Boolean) {
        ensureChannel()
        // 通知构建异常不能阻断 startForeground，否则在 Android 12+ 会因超时未调用
        // startForeground 而触发 ForegroundServiceDidNotStartInTimeException 崩溃。
        val notification = try {
            buildNotification(title, progress, speed, showSpeed)
        } catch (t: Throwable) {
            minimalNotification(title)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** 最简前台通知：startForeground 兜底用，保证任何异常下都能完成前台提升 */
    private fun minimalNotification(title: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(title: String, progress: Int, speed: String, showSpeed: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            // 完整通知显示下载速度；简化模式仅提示下载中（且不显示进度条）
            .setContentText(
                if (showSpeed && speed.isNotBlank()) "下载速度 $speed"
                else "正在后台下载，完成前请勿关闭应用"
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // 仅「完整通知」模式显示进度条；简化模式隐藏进度条
        if (showSpeed && progress in 0..100) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "yunx_download"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.yunx.app.action.STOP_DOWNLOAD"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_SHOW_SPEED = "show_speed"

        /**
         * 防重入标记：stop() 可能被并发调用（多个任务同时结束时），
         * 只发送一次停止意图，避免服务被反复重建。
         */
        @Volatile
        private var stopPending = false

        /** 下载任务开始时调用（服务不存在则创建前台服务） */
        fun start(context: Context, title: String, progress: Int = 0) {
            start(context, title, progress, "", true)
        }

        /** 更新/启动前台通知（标题/进度/速度变化；调用方节流） */
        fun update(context: Context, title: String, progress: Int, speed: String, showSpeed: Boolean) {
            start(context, title, progress, speed, showSpeed)
        }

        private fun start(context: Context, title: String, progress: Int, speed: String, showSpeed: Boolean) {
            stopPending = false
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_SPEED, speed)
                .putExtra(EXTRA_SHOW_SPEED, showSpeed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 全部任务结束：停止前台服务。
         * ★ 严禁用 stopService()：任务跑在 Dispatchers.Default，stopService 可能先于主线程
         *   onCreate() 的 startForeground() 被系统处理，fgRequired 服务未前台即销毁，
         *   系统直接抛 ForegroundServiceDidNotStartInTimeException 崩溃。
         *   改为发送 ACTION_STOP 意图，由服务内 onStartCommand 在 startForeground 之后
         *   调用 stopSelf() 安全停止。
         */
        fun stop(context: Context) {
            if (stopPending) return
            stopPending = true
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 下载完成通知：独立于前台进度通知，用户可滑动关闭 */
        fun notifyCompleted(context: Context, fileName: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(CHANNEL_ID) == null
            ) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW)
                )
            }

            val contentIntent = PendingIntent.getActivity(
                context,
                fileName.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            val notification = builder
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("下载完成")
                .setContentText(fileName)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()

            nm.notify(2000 + (fileName.hashCode() and 0x3FFF), notification)
        }
    }
}