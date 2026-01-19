package com.example.irodori_wallpaperchanger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenOnReceiver(
    private val onScreenOn: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            Log.i("Irodori", "SCREEN_ON detected")
            onScreenOn()
        }
    }
}
