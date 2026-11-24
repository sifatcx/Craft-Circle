package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ProjectActivityAlert

object CraftNotificationHelper {

    const val EXTRA_PROJECT_ID = "extra_project_id"

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val channelId = appContext.getString(R.string.notification_channel_id)
        val channelName = appContext.getString(R.string.notification_channel_name)
        val channelDescription = appContext.getString(R.string.notification_channel_description)

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = channelDescription
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    fun showActivityNotification(context: Context, alert: ProjectActivityAlert) {
        val appContext = context.applicationContext
        if (!hasNotificationPermission(appContext)) return
        ensureNotificationChannel(appContext)

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PROJECT_ID, alert.projectId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            alert.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = appContext.getString(R.string.notification_channel_id)
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_craft)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(alert.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked between check and notify
        }
    }
}
