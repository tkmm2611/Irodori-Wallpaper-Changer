package com.example.irodori_wallpaperchanger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class WallpaperForegroundService : Service() {

    companion object {
        private const val TAG = "Irodori"
        private const val CHANNEL_ID = "wallpaper_service"
        private const val COOLDOWN_MS = 10_000L
    }

    private var lastChangeTime = 0L
    private lateinit var screenOnReceiver: ScreenOnReceiver

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")

        createNotificationChannel()
        startForeground(1, createNotification())

        screenOnReceiver = ScreenOnReceiver {
            onScreenOn()
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOnReceiver, filter)

        Log.i(TAG, "ScreenOnReceiver registered")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")

        unregisterReceiver(screenOnReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────
    // SCREEN ON 処理
    // ─────────────────────────────
    private fun onScreenOn() {
        Log.i(TAG, "SCREEN_ON detected")

        val now = System.currentTimeMillis()
        if (now - lastChangeTime < COOLDOWN_MS) {
            Log.i(TAG, "Cooldown active, skip")
            return
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val folderUriString = prefs.getString("folder_uri", null)

        Log.i(TAG, "folderUri = $folderUriString")

        if (folderUriString == null) {
            Log.i(TAG, "No folder selected, skip")
            return
        }

        val imageUri = ImageUtils.pickRandomImage(
            context = this,
            folderUri = android.net.Uri.parse(folderUriString)
        )

        if (imageUri == null) {
            Log.i(TAG, "No image found in folder")
            return
        }

        Log.i(TAG, "Setting wallpaper: $imageUri")

        val fitLandscape =
            prefs.getBoolean("fit_landscape_to_screen", false)

        WallpaperSetter.set(
            context = this,
            imageUri = imageUri,
            fitLandscape = fitLandscape
        )

        lastChangeTime = now
        prefs.edit().putLong("last_change_time", now).apply()

        Log.i(TAG, "Wallpaper set complete")
    }

    // ─────────────────────────────
    // Notification
    // ─────────────────────────────
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
