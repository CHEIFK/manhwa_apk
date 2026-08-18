package com.example.data.downloader

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
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class DownloadService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: "Manga Series"
        startForegroundServiceNotification(seriesTitle)
      }
      ACTION_CANCEL -> {
        DownloadManager.getInstance(this).cancelCurrentDownload()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
      ACTION_STOP -> {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun startForegroundServiceNotification(title: String) {
    val notificationIntent = Intent(this, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
      this, 0, notificationIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val cancelIntent = Intent(this, DownloadService::class.java).apply {
      action = ACTION_CANCEL
    }
    val cancelPendingIntent = PendingIntent.getService(
      this, 1, cancelIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Manwa Manager Downloader")
      .setContentText("Downloading: $title")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentIntent(pendingIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Manwa Manager Downloads",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Shows progress of active manga & manhwa chapter downloads"
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val CHANNEL_ID = "manwa_download_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START = "com.manwamanager.action.START_DOWNLOAD"
    const val ACTION_STOP = "com.manwamanager.action.STOP_DOWNLOAD"
    const val ACTION_CANCEL = "com.manwamanager.action.CANCEL_DOWNLOAD"
    const val EXTRA_SERIES_TITLE = "extra_series_title"
  }
}
