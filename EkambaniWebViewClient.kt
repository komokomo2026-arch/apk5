package com.example.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.config.AppConfig

/** Link routing, trust boundaries and error handling. Never shows URLs and never clears data. */
class EkambaniWebViewClient(
    private val context: Context,
    private val onPageLoadStarted: () -> Unit,
    private val onPageLoadFinished: (success: Boolean) -> Unit,
    private val onMainFrameError: () -> Unit,
    private val onNavigationBlocked: () -> Unit,
    private val onRenderGone: () -> Unit
) : WebViewClient() {

    private var hasMainFrameError = false
    private var lastGoodUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return LinkRouter.route(context, uri)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        hasMainFrameError = false
        onPageLoadStarted()
        if (AppConfig.isPrimarySite(url)) view?.evaluateJavascript(EkambaniBridge.INIT_JS, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!hasMainFrameError) lastGoodUrl = url
        if (AppConfig.isPrimarySite(url)) view?.evaluateJavascript(EkambaniBridge.INIT_JS, null)
        CookieManager.getInstance().flush()
        onPageLoadFinished(!hasMainFrameError)
    }

    private fun mainFrameFailed(view: WebView?, failedUrl: String?) {
        hasMainFrameError = true
        // A link inside an already-working app failed (offline, not cached): go back instead of showing an error.
        if (view != null && view.canGoBack() && failedUrl != lastGoodUrl) {
            view.post { view.goBack() }
            onNavigationBlocked()
        } else if (lastGoodUrl != null) {
            onNavigationBlocked() // failed reload: keep what is on screen
        } else {
            onMainFrameError()
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) mainFrameFailed(view, request.url.toString())
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 500) {
            mainFrameFailed(view, request.url.toString())
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        mainFrameFailed(view, error?.url)
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        onRenderGone()
        return true
    }
}

/** Decides what happens to every link. Returns true when the link was handled outside the WebView. */
object LinkRouter {
    fun route(context: Context, uri: Uri): Boolean {
        val url = uri.toString()
        val scheme = uri.scheme?.lowercase() ?: ""

        if (AppConfig.isTrustedUrl(url)) return false
        if (scheme == "blob" || scheme == "data" || url == "about:blank") return false

        when (scheme) {
            "tel" -> return launch(context, Intent(Intent.ACTION_DIAL, uri))
            "mailto", "sms", "smsto", "mms", "mmsto", "whatsapp", "geo", "market" ->
                return launch(context, Intent(Intent.ACTION_VIEW, uri))
            "intent" -> {
                try {
                    val i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        component = null
                        selector = null
                    }
                    if (!launch(context, i)) {
                        i.getStringExtra("browser_fallback_url")?.let {
                            launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                    }
                } catch (_: Exception) {
                }
                return true
            }
            "http", "https" -> {
                if (AppConfig.OPEN_EXTERNAL_LINKS_IN_BROWSER) launch(context, Intent(Intent.ACTION_VIEW, uri))
                return true
            }
        }
        return true // anything else (file:, content:, javascript: from outside) is blocked
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
