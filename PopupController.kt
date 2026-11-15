package com.example.webview

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.config.AppConfig

/**
 * Opens window.open() popups (e.g. the Google sign-in window) in a separate full-screen WebView,
 * so the website keeps its own page and can receive the sign-in result.
 */
class PopupController(private val activity: Activity) {

    private var dialog: Dialog? = null
    private var child: WebView? = null

    fun open(resultMsg: Message): Boolean {
        close()
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val wv = WebView(activity)
        WebViewConfig.apply(wv, true)
        wv.settings.setSupportMultipleWindows(false)
        wv.setDownloadListener(EkambaniDownloadListener(activity) { wv })
        wv.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                close()
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val handled = LinkRouter.route(activity, uri)
                if (handled) close()
                return handled
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val u = url ?: return
                if ((u.startsWith("http://") || u.startsWith("https://")) && !AppConfig.isTrustedUrl(u)) {
                    view?.stopLoading()
                    LinkRouter.route(activity, android.net.Uri.parse(u))
                    close()
                }
            }
        }
        child = wv
        transport.webView = wv
        resultMsg.sendToTarget()

        val d = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        d.setContentView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        d.setOnDismissListener { release() }
        dialog = d
        d.show()
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return true
    }

    fun close() {
        val d = dialog
        dialog = null
        try { d?.dismiss() } catch (_: Exception) {}
        release()
    }

    private fun release() {
        val c = child ?: return
        child = null
        try {
            c.stopLoading()
            (c.parent as? ViewGroup)?.removeView(c)
            c.destroy()
        } catch (_: Exception) {
        }
    }
}
