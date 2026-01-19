package com.example.irodori_wallpaperchanger

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File

object WallpaperSetter {

    fun set(context: Context, imageUri: Uri, fitLandscape: Boolean): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(imageUri)
            val original = BitmapFactory.decodeStream(input)
            input?.close()

            val (screenW, screenH) = getScreenSize(context)

            val finalBitmap =
                when {
                    // 横長 + スイッチON → 画面に収める
                    fitLandscape && original.width > original.height ->
                        fitWithBlackBars(original, screenW, screenH)

                    // 縦長で黒帯が必要 → 従来どおり
                    needsBlackBars(original, screenW) ->
                        fitWithBlackBars(original, screenW, screenH)

                    // それ以外 → 収まる範囲でリサイズ
                    else ->
                        resizeToFitScreen(original, screenW, screenH)
                }

            val manager = WallpaperManager.getInstance(context)
            manager.setBitmap(
                finalBitmap,
                Rect(0, 0, finalBitmap.width, finalBitmap.height),
                true,
                WallpaperManager.FLAG_SYSTEM
            )

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setFromFile(context: Context, file: File, fitLandscape: Boolean): Boolean {
        if (!file.exists()) return false

        val uri = Uri.fromFile(file)
        return set(context, uri, fitLandscape)
    }

    // ─────────────────────────────
    // 判定：黒帯が必要なケースか
    // ─────────────────────────────
    private fun needsBlackBars(bitmap: Bitmap, screenWidth: Int): Boolean {
        return bitmap.height > bitmap.width && bitmap.width > screenWidth
    }

    // ─────────────────────────────
    // 画面に収める（拡大しない）
    // ─────────────────────────────
    private fun resizeToFitScreen(
        bitmap: Bitmap,
        screenWidth: Int,
        screenHeight: Int
    ): Bitmap {

        val widthRatio = screenWidth.toFloat() / bitmap.width
        val heightRatio = screenHeight.toFloat() / bitmap.height
        val scale = minOf(widthRatio, heightRatio)

        if (scale >= 1f) return bitmap

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ─────────────────────────────
    // 黒帯付きで完全一画面にする
    // ─────────────────────────────
    private fun fitWithBlackBars(
        bitmap: Bitmap,
        screenWidth: Int,
        screenHeight: Int
    ): Bitmap {

        val result = Bitmap.createBitmap(
            screenWidth,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val scale = screenWidth.toFloat() / bitmap.width
        val newWidth = screenWidth
        val newHeight = (bitmap.height * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            newWidth,
            newHeight,
            true
        )

        val top = (screenHeight - newHeight) / 2f
        canvas.drawBitmap(scaled, 0f, top, null)

        return result
    }

    // ─────────────────────────────
    // 画面サイズ取得
    // ─────────────────────────────
    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
}
