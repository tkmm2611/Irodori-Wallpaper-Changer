package com.example.irodori_wallpaperchanger

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object ImageUtils {

    fun getImageUris(context: Context, treeUri: Uri): List<Uri> {
        val result = mutableListOf<Uri>()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        context.contentResolver.query(
            childrenUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val mime = cursor.getString(1)

                if (mime.startsWith("image/")) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        docId
                    )
                    result.add(uri)
                }
            }
        }

        return result
    }

    fun countImages(context: Context, treeUri: Uri): Int {
        var count = 0

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        context.contentResolver.query(
            childrenUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mime = cursor.getString(0)
                if (mime.startsWith("image/")) {
                    count++
                }
            }
        }

        return count
    }

    fun pickRandomImage(
        context: Context,
        folderUri: Uri
    ): Uri? {

        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )

        val imageUris = mutableListOf<Uri>()

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val mime = cursor.getString(1)

                if (mime.startsWith("image/")) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        folderUri,
                        docId
                    )
                    imageUris.add(docUri)
                }
            }
        }

        if (imageUris.isEmpty()) return null

        // 連続回避（前回と同じ画像を避ける）
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lastUri = prefs.getString("last_image_uri", null)

        val candidates = imageUris.filter { it.toString() != lastUri }
            .ifEmpty { imageUris }

        val selected = candidates.random()

        prefs.edit().putString("last_image_uri", selected.toString()).apply()

        return selected
    }

}
