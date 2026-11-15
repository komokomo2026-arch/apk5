package com.example.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.config.AppConfig

/** One place that configures every WebView (main page and sign-in popups). Never clears any data. */
object WebViewConfig {

    fun cacheModeFor(online: Boolean): Int =
        if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, online: Boolean) {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage + IndexedDB
            databaseEnabled = true
            cacheMode = cacheModeFor(online)  // offline: use cached copy first
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)   // Google sign-in popup
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            if (AppConfig.GOOGLE_AUTH_COMPAT_USER_AGENT) {
                userAgentString = userAgentString
                    .replace("; wv", "")
                    .replace("Version/4.0 ", "")
            }
        }
        WebView.setWebContentsDebuggingEnabled(false)
    }

    /** Make sure the website's PWA service worker can install and serve offline. */
    fun configureServiceWorkers() {
        try {
            ServiceWorkerController.getInstance().serviceWorkerWebSettings.apply {
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = false
                allowFileAccess = false
                blockNetworkLoads = false
            }
        } catch (_: Throwable) {
        }
    }
}
