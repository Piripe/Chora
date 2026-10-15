package com.craftworks.music.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.craftworks.music.MainActivity
import com.craftworks.music.R
import com.craftworks.music.managers.SmartCacheManager
import com.craftworks.music.managers.SmartCacheState
import com.craftworks.music.managers.downloadStatusText
import com.craftworks.music.managers.statusText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the app alive while the smart cache refreshes or songs are downloaded, which can take
 * a long time, and shows the progress in a notification.
 */
@AndroidEntryPoint
class SmartCacheService : Service() {
    @Inject lateinit var smartCacheManager: SmartCacheManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called after every startForegroundService, even to cancel.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(smartCacheManager.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        if (intent?.action == ACTION_CANCEL) {
            smartCacheManager.cancelRefresh()
            smartCacheManager.cancelDownloads()
        }

        if (observeJob == null) {
            observeJob = scope.launch {
                val notificationManager = getSystemService(NotificationManager::class.java)
                launch {
                    smartCacheManager.state.collect { state ->
                        if (state.isActive) notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                        // The state is conflated, this only skips intermediate progress updates.
                        // Android drops the notifications of apps posting more than ~5 per second.
                        delay(1000)
                    }
                }
                smartCacheManager.state.first { !it.isActive }
                ServiceCompat.stopForeground(this@SmartCacheService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // Android 15+ limits how long a data sync service may run.
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timed out, stopping the refresh and the downloads")
        smartCacheManager.cancelRefresh()
        smartCacheManager.cancelDownloads()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: SmartCacheState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancel = PendingIntent.getService(
            this, 0,
            Intent(this, SmartCacheService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // The refresh first, the downloads requested by the user once it is done.
        val refreshing = state.isRunning || !state.isDownloading
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_download_24)
            .setContentTitle(getString(
                if (refreshing) R.string.smart_cache_notification_title else R.string.downloads_notification_title
            ))
            .setContentText(if (refreshing) state.statusText(resources) else state.downloadStatusText(resources))
            .apply {
                if (refreshing) setProgress(state.total, state.progress, state.total == 0)
                else setProgress(state.downloadTotal, state.downloadProgress, false)
            }
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.smart_cache_notification_cancel), cancel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private val SmartCacheState.isActive: Boolean get() = isRunning || isDownloading

    companion object {
        private const val TAG = "SmartCacheService"
        private const val CHANNEL_ID = "smart_cache"
        private const val NOTIFICATION_ID = 7342
        private const val ACTION_CANCEL = "com.craftworks.music.SMART_CACHE_CANCEL"

        fun start(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.smart_cache_notification_channel))
                    .build()
            )
            try {
                ContextCompat.startForegroundService(context, Intent(context, SmartCacheService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background: the refresh still runs while the app is alive.
                Log.w(TAG, "Can't start the smart cache service", e)
            }
        }
    }
}
