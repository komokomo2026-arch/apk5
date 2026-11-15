package com.example.webview

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.example.R
import com.example.config.AppConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Small JavaScript bridge (window.AndroidBridge): printing, blob/PDF file saving and a pull-to-refresh hint.
 * Every sensitive method first checks that the calling page really is the Ekambani website.
 */
class EkambaniBridge(
    private val activity: Activity,
    private val webViewProvider: () -> WebView?
) {
    @Volatile
    var innerScrolled: Boolean = false

    private class Pending(val name: String, val mime: String, val file: File, val out: FileOutputStream)

    private val pending = ConcurrentHashMap<String, Pending>()

    private fun isTrustedCaller(): Boolean {
        val task = FutureTask<Boolean> { AppConfig.isPrimarySite(webViewProvider()?.url) }
        activity.runOnUiThread(task)
        return try {
            task.get(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
            false
        }
    }

    private fun toast(msg: String) = activity.runOnUiThread {
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
    }

    @JavascriptInterface
    fun printPage() {
        if (!isTrustedCaller()) return
        activity.runOnUiThread {
            val webView = webViewProvider() ?: return@runOnUiThread
            val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return@runOnUiThread
            val job = "${AppConfig.APP_NAME} Document"
            pm.print(
                job,
                webView.createPrintDocumentAdapter(job),
                PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
            )
        }
    }

    @JavascriptInterface
    fun setInnerScrolled(value: Boolean) {
        innerScrolled = value
    }

    @JavascriptInterface
    fun beginFile(id: String, name: String?, mime: String?) {
        if (!isTrustedCaller()) return
        try {
            val m = mime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            val f = File.createTempFile("dl", ".tmp", activity.cacheDir)
            pending[id] = Pending(FileSaver.sanitize(name, m), m, f, FileOutputStream(f))
        } catch (_: Exception) {
            toast(activity.getString(R.string.download_failed))
        }
    }

    @JavascriptInterface
    fun appendChunk(id: String, base64Chunk: String) {
        val p = pending[id] ?: return
        try {
            p.out.write(Base64.decode(base64Chunk, Base64.DEFAULT))
        } catch (_: Exception) {
            failFile(id)
        }
    }

    @JavascriptInterface
    fun endFile(id: String) {
        val p = pending.remove(id) ?: return
        val ok = try {
            p.out.close()
            FileSaver.saveToDownloads(activity, p.file, p.name, p.mime)
        } catch (_: Exception) {
            false
        }
        p.file.delete()
        toast(if (ok) activity.getString(R.string.download_saved, p.name) else activity.getString(R.string.download_failed))
    }

    @JavascriptInterface
    fun failFile(id: String) {
        pending.remove(id)?.let {
            try { it.out.close() } catch (_: Exception) {}
            it.file.delete()
        }
        toast(activity.getString(R.string.download_failed))
    }

    companion object {
        const val INTERFACE_NAME = "AndroidBridge"

        /** Injected into the website (only the website, never other hosts). */
        const val INIT_JS = """
            (function() {
              if (window.__ekInit) return;
              window.__ekInit = true;
              window.print = function() { try { AndroidBridge.printPage(); } catch (e) {} };
              var oc = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function() {
                try { if (this.hasAttribute('download')) window.__ekDlName = this.getAttribute('download'); } catch (e) {}
                return oc.apply(this, arguments);
              };
              document.addEventListener('click', function(e) {
                var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
                if (a) window.__ekDlName = a.getAttribute('download');
              }, true);
              function scrolled(el) {
                while (el && el !== document.body && el !== document.documentElement) {
                  if (el.scrollTop > 0) return true;
                  el = el.parentElement;
                }
                return false;
              }
              document.addEventListener('touchstart', function(e) {
                try { AndroidBridge.setInnerScrolled(scrolled(e.target)); } catch (x) {}
              }, { passive: true, capture: true });
            })();
        """
    }
}
