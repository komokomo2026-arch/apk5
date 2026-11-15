package com.example.webview

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream

object FileSaver {

    fun sanitize(name: String?, mime: String?): String {
        var n = (name ?: "").substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim()
        if (n.isEmpty()) {
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
            n = "download.$ext"
        }
        return n
    }

    /** Saves [src] into the public Downloads folder (app-specific folder on Android 9 and older). */
    fun saveToDownloads(context: Context, src: File, name: String, mime: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(src).use { it.copyTo(out) }
                } ?: return false
                true
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
                dir.mkdirs()
                var target = File(dir, name)
                var i = 1
                while (target.exists()) {
                    target = File(dir, "${name.substringBeforeLast('.')}($i).${name.substringAfterLast('.', "")}")
                    i++
                }
                src.copyTo(target)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
