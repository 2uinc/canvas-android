package com.instructure.student.offline.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.instructure.student.R
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.activity.DownloadQueueActivity

class OfflineModeService : Service() {

    private var mCurrentPercent = -1
    private var mNotificationManager: NotificationManager? = null
    private var mBuilder: NotificationCompat.Builder? = null

    private val mOfflineManager = Offline.getOfflineManager()

    private val mOfflineListener = object : OfflineManager.OfflineListener() {
        override fun onStateChanged(state: Int) {
            when (state) {
                OfflineManager.STATE_IDLE, OfflineManager.STATE_PAUSED -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        override fun onItemStartedDownload(key: String) {
            updateNotification()
        }

        override fun onProgressChanged(key: String, currentProgress: Int, allProgress: Int) {
            updateProgress(currentProgress, allProgress)
        }
    }

    override fun onCreate() {
        super.onCreate()

        isStarted = true

        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        updateNotification(true)
        mOfflineManager.addListener(mOfflineListener)
    }

    override fun onDestroy() {
        isStarted = false

        mOfflineManager.removeListener(mOfflineListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun updateProgress(currentProgress: Int, allProgress: Int) {
        if (currentProgress == mCurrentPercent) return
        mCurrentPercent = currentProgress

        mNotificationManager?.notify(
            NOTIFICATION_ID,
            getOfflineNotification(null, mCurrentPercent, allProgress, false)
        )
    }

    private fun updateNotification(isStart: Boolean = false) {
        if (!isStarted) return

        val creator = mOfflineManager.getCurrentDownloaderCreator() as? OfflineDownloaderCreator

        val notification = getOfflineNotification(
            creator?.offlineQueueItem?.keyItem?.title ?: getString(R.string.student_app_name),
            0, 0, true
        )
        if (isStart) {

            notification?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    startForeground(NOTIFICATION_ID, it)
                } else {
                    startForeground(
                        NOTIFICATION_ID, it, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            }

        } else {
            mNotificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getOfflineNotification(
        title: String?, progress: Int, max: Int, indeterminate: Boolean
    ): Notification? {
        createNotificationChannel()

        if (mBuilder == null) {
            val intent = Intent(this, DownloadQueueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mBuilder = NotificationCompat.Builder(this, CHANNEL_OFFLINE_MODE)
                .setSmallIcon(R.drawable.canvas_logo_white)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
        }
        if (title != null) mBuilder?.setContentTitle(title)
        return mBuilder?.setProgress(max, progress, indeterminate)?.build()
    }

    private fun createNotificationChannel() {
        if (mNotificationManager?.notificationChannels?.any { it.id == CHANNEL_OFFLINE_MODE } == true) return

        val name = "Offline mode notifications"

        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_OFFLINE_MODE, name, importance)
        channel.description = name
        channel.enableLights(false)
        channel.enableVibration(false)

        mNotificationManager?.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        private const val CHANNEL_OFFLINE_MODE = "offlineChannel"

        var isStarted = false
    }
}
