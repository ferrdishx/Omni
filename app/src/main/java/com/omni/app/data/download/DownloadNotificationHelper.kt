package com.omni.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.omni.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "downloads_channel"
        const val CHANNEL_NAME = "Downloads"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val iconCache = mutableMapOf<String, Bitmap>()

    fun showProgressNotification(id: String, title: String, progress: Int, speed: String, thumbnailUrl: String? = null) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Downloading... $progress% ($speed)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val cachedBitmap = iconCache[id]
        if (cachedBitmap != null) {
            builder.setLargeIcon(cachedBitmap)
            notificationManager.notify(id.hashCode(), builder.build())
        } else if (thumbnailUrl != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = fetchBitmap(thumbnailUrl)
                if (bitmap != null) {
                    iconCache[id] = bitmap
                    builder.setLargeIcon(bitmap)
                    notificationManager.notify(id.hashCode(), builder.build())
                }
            }
        }

        notificationManager.notify(id.hashCode(), builder.build())
    }

    private suspend fun fetchBitmap(url: String): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        
        val result = loader.execute(request)
        return if (result is SuccessResult) {
            (result.drawable as? BitmapDrawable)?.bitmap
        } else null
    }

    fun showCompletedNotification(id: String, title: String) {
        notificationManager.cancel(id.hashCode())

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Download completed")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)

        val cachedBitmap = iconCache[id]
        if (cachedBitmap != null) {
            builder.setLargeIcon(cachedBitmap)
        }

        notificationManager.notify(id.hashCode(), builder.build())
        iconCache.remove(id)
    }

    fun showFailedNotification(id: String, title: String, error: String?) {
        notificationManager.cancel(id.hashCode())

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Failed: $title")
            .setContentText(error ?: "Unknown error occurred")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)

        val cachedBitmap = iconCache[id]
        if (cachedBitmap != null) {
            builder.setLargeIcon(cachedBitmap)
        }

        notificationManager.notify(id.hashCode(), builder.build())
        iconCache.remove(id)
    }

    fun cancelNotification(id: String) {
        notificationManager.cancel(id.hashCode())
        iconCache.remove(id)
    }
}
