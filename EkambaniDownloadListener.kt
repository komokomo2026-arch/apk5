package com.example.webview

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import com.example.R
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

/** Handles http(s) downloads, data: URLs and blob: URLs (backups, CSV, PDF reports). */
class EkambaniDownloadListener(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) : DownloadListener {

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) return
        try {
            when {
                url.startsWith("blob:") -> saveBlob(url, contentDisposition, mimetype)
                url.startsWith("data:") -> saveDataUrl(url, contentDisposition)
                url.startsWith("http://") || url.startsWith("https://") ->
                    enqueue(url, userAgent, contentDisposition, mimetype)
            }
        } catch (_: Exception) {
            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun enqueue(url: String, ua: String?, cd: String?, mime: String?) {
        val name = FileSaver.sanitize(URLUtil.guessFileName(url, cd, mime), mime)
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            if (!ua.isNullOrBlank()) addRequestHeader("User-Agent", ua)
            if (!mime.isNullOrBlank()) setMimeType(mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } else {
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            }
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
    }

    private fun saveDataUrl(url: String, cd: String?) {
        val comma = url.indexOf(',')
        if (comma < 0) return
        val header = url.substring(5, comma)
        val payload = url.substring(comma + 1)
        val mime = header.substringBefore(';').ifBlank { "application/octet-stream" }
        val bytes = if (header.contains(";base64")) Base64.decode(payload, Base64.DEFAULT)
        else URLDecoder.decode(payload, "UTF-8").toByteArray()
        val name = FileSaver.sanitize(URLUtil.guessFileName("", cd, mime).takeIf { cd != null }, mime)
        val tmp = File.createTempFile("dl", ".tmp", context.cacheDir)
        tmp.writeBytes(bytes)
        val ok = FileSaver.saveToDownloads(context, tmp, name, mime)
        tmp.delete()
        Toast.makeText(
            context,
            if (ok) context.getString(R.string.download_saved, name) else context.getString(R.string.download_failed),
            Toast.LENGTH_LONG
        ).show()
    }

    /** blob: URLs only exist inside the page, so the page reads the blob and streams it to the bridge in chunks. */
    private fun saveBlob(url: String, cd: String?, mime: String?) {
        val wv = webViewProvider() ?: return
        val fallbackName = FileSaver.sanitize(
            if (cd != null) URLUtil.guessFileName(url, cd, mime) else null,
            mime
        )
        val id = "d" + System.currentTimeMillis()
        val js = """
            (function(u, fallback, mime, id) {
              fetch(u).then(function(r) { return r.blob(); }).then(function(b) {
                var name = window.__ekDlName || fallback;
                window.__ekDlName = null;
                AndroidBridge.beginFile(id, name, b.type || mime || 'application/octet-stream');
                var rd = new FileReader();
                rd.onloadend = function() {
                  try {
                    var s = rd.result; s = s.substring(s.indexOf(',') + 1);
                    var CH = 786432;
                    for (var p = 0; p < s.length; p += CH) AndroidBridge.appendChunk(id, s.substring(p, p + CH));
                    AndroidBridge.endFile(id);
                  } catch (e) { AndroidBridge.failFile(id); }
                };
                rd.onerror = function() { AndroidBridge.failFile(id); };
                rd.readAsDataURL(b);
              }).catch(function() { AndroidBridge.failFile(id); });
            })(${JSONObject.quote(url)}, ${JSONObject.quote(fallbackName)}, ${JSONObject.quote(mime ?: "")}, ${JSONObject.quote(id)});
        """.trimIndent()
        wv.post { wv.evaluateJavascript(js, null) }
    }
}
