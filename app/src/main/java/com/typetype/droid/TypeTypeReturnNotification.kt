package com.typetype.droid

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

object TypeTypeReturnNotification {
    const val ACTION_SHOW_INPUT_METHOD_PICKER = "com.typetype.droid.action.SHOW_INPUT_METHOD_PICKER"

    private const val CHANNEL_ID = "typetype_return_controls"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_OPEN_SETTINGS = 10
    private const val REQUEST_SHOW_PICKER = 11

    fun show(context: Context) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openSettingsIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_SETTINGS,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingIntentFlags(),
        )
        val showPickerIntent = PendingIntent.getActivity(
            context,
            REQUEST_SHOW_PICKER,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_SHOW_INPUT_METHOD_PICKER
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingIntentFlags(),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keyboard_line)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_body))
            .setContentIntent(openSettingsIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_switch_line,
                context.getString(R.string.choose_input_method),
                showPickerIntent,
            )
            .addAction(
                R.drawable.ic_settings_24,
                context.getString(R.string.open_typetype_settings),
                openSettingsIntent,
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_controls),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_controls_desc)
                setShowBadge(false)
            },
        )
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
