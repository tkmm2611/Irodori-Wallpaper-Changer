package com.example.irodori_wallpaperchanger

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var firstLaunchHintText: TextView
    private lateinit var switchRow: View
    private lateinit var wallpaperSwitch: SwitchCompat
    private lateinit var statusText: TextView
    private lateinit var imageCountText: TextView
    private lateinit var folderSelectButton: Button
    private lateinit var stopImageButton: ImageButton
    private lateinit var fitLandscapeSwitchRow: View
    private lateinit var fitLandscapeSwitch: SwitchCompat

    private val prefs by lazy {
        getSharedPreferences("settings", MODE_PRIVATE)
    }

    // ───────── フォルダ選択 ─────────
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {

                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION

                contentResolver.takePersistableUriPermission(uri, flags)

                onFolderSelected(uri)
            }
        }

    // ───────── 停止用画像選択 ─────────
    private val pickStopImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                saveStopImageUri(uri)
                showStopImageThumbnail()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View binding
        firstLaunchHintText = findViewById(R.id.firstLaunchHintText)
        switchRow = findViewById(R.id.switchRow)
        wallpaperSwitch = findViewById(R.id.wallpaperSwitch)
        statusText = findViewById(R.id.switchStatusText)
        imageCountText = findViewById(R.id.imageCountText)
        folderSelectButton = findViewById(R.id.folderSelectButton)
        stopImageButton = findViewById(R.id.stopImageButton)
        fitLandscapeSwitchRow = findViewById(R.id.fitLandscapeSwitchRow)
        fitLandscapeSwitch = findViewById(R.id.fitLandscapeSwitch)

        val aboutRow = findViewById<TextView>(R.id.aboutRow)

        fitLandscapeSwitchRow.setOnClickListener {
            val newValue = !fitLandscapeSwitch.isChecked
            fitLandscapeSwitch.isChecked = newValue

            prefs.edit()
                .putBoolean("fit_landscape_to_screen", newValue)
                .apply()
        }

        // 初期状態
        val initialValue =
            prefs.getBoolean("fit_landscape_to_screen", false)

        fitLandscapeSwitch.isChecked = initialValue

        val enabled = prefs.getBoolean("wallpaper_enabled", false)
        wallpaperSwitch.isChecked = enabled
        updateSwitchUI(enabled)

        imageCountText.text = getString(R.string.image_count_initial)
        restoreStopImageThumbnail()

        // 行全体タップでスイッチ切替
        switchRow.setOnClickListener {
            wallpaperSwitch.isChecked = !wallpaperSwitch.isChecked
        }

        // スイッチ変更（唯一の制御点）
        wallpaperSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wallpaper_enabled", isChecked).apply()
            updateSwitchUI(isChecked)

            if (isChecked) {
                startWallpaperService()
            } else {
                stopWallpaperService()
                applyStopImageIfExists()
            }
        }

        // フォルダ選択
        folderSelectButton.setOnClickListener {
            openFolderPicker()
        }

        // 停止用画像選択
        stopImageButton.setOnClickListener {
            pickStopImageLauncher.launch("image/*")
        }

        // 長押しで停止画像クリア
        stopImageButton.setOnLongClickListener {

            // 内部保存画像を削除
            val file = File(filesDir, "stop_image.jpg")
            if (file.exists()) {
                file.delete()
            }

            // 見た目を未設定状態へ
            stopImageButton.setImageResource(R.drawable.ic_image_select)
            stopImageButton.imageTintList =
                getColorStateList(R.color.image_button_tint)

            true
        }

        prefs.getString("folder_uri", null)?.let {
            val uri = Uri.parse(it)
            val count = ImageUtils.countImages(this, uri)
            imageCountText.text = getString(R.string.image_count, count)
        }

        updateFirstLaunchHint()

        // About表示
        aboutRow.setOnClickListener {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_about, null)

            view.findViewById<TextView>(R.id.rateAppItem).setOnClickListener {
                openPlayStore()
                dialog.dismiss()
            }

            view.findViewById<TextView>(R.id.supportDevItem).setOnClickListener {
                openSupportPage()
                dialog.dismiss()
            }

            dialog.setContentView(view)
            dialog.show()
        }

    }

    // 初回起動時ヒント
    private fun updateFirstLaunchHint() {
        val folderSelected = prefs.contains("folder_uri")

        firstLaunchHintText.visibility =
            if (folderSelected) View.GONE else View.VISIBLE
    }

    // ─────────────────────────────
    // フォルダ選択
    // ─────────────────────────────
    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun onFolderSelected(uri: Uri) {
        prefs.edit().putString("folder_uri", uri.toString()).apply()

        val count = ImageUtils.countImages(this, uri)
        imageCountText.text = getString(R.string.image_count, count)

        Log.i("Irodori", "Folder selected: $count images")

        updateFirstLaunchHint()
    }

    // ─────────────────────────────
    // UI 更新
    // ─────────────────────────────
    private fun updateSwitchUI(enabled: Boolean) {
        if (enabled) {
            statusText.text = getString(R.string.status_running)
            statusText.setTextColor(getColor(R.color.switch_on_text))
        } else {
            statusText.text = getString(R.string.status_stopped)
            statusText.setTextColor(getColor(R.color.switch_off_text))
        }
    }

    // ─────────────────────────────
    // 停止用画像処理
    // ─────────────────────────────
    private fun saveStopImageUri(uri: Uri) {
        val input = contentResolver.openInputStream(uri) ?: return
        val file = File(filesDir, "stop_image.jpg")

        file.outputStream().use { output ->
            input.copyTo(output)
        }
        input.close()
    }

    private fun restoreStopImageThumbnail() {
        showStopImageThumbnail()
    }

    private fun showStopImageThumbnail() {
        val file = File(filesDir, "stop_image.jpg")
        if (!file.exists()) return

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val thumbnail =
            Bitmap.createScaledBitmap(bitmap, 128, 128, true)

        stopImageButton.clearColorFilter()
        stopImageButton.imageTintList = null
        stopImageButton.setImageBitmap(thumbnail)
    }

    private fun applyStopImageIfExists() {
        val file = File(filesDir, "stop_image.jpg")
        if (!file.exists()) return

        val fitLandscape =
            prefs.getBoolean("fit_landscape_to_screen", false)

        WallpaperSetter.setFromFile(
            context = this,
            file = file,
            fitLandscape = fitLandscape
        )
    }

    private fun openPlayStore() {
        val uri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun openSupportPage() {
        val uri = Uri.parse("https://github.com/tkmm2611/Irodori-Wallpaper-Changer")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    // ─────────────────────────────
    // Service 制御
    // ─────────────────────────────
    private fun startWallpaperService() {
        val intent = Intent(this, WallpaperForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun stopWallpaperService() {
        val intent = Intent(this, WallpaperForegroundService::class.java)
        stopService(intent)
    }
}
