package com.example.ui

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.config.AppConfig
import com.example.webview.EkambaniBridge
import com.example.webview.EkambaniDownloadListener
import com.example.webview.EkambaniWebChromeClient
import com.example.webview.EkambaniWebViewClient
import com.example.webview.PopupController
import com.example.webview.WebViewConfig

/** The WebView with pull-to-refresh. It stays alive for the whole session (never replaced by an error screen). */
@Composable
fun EkambaniWebViewContainer(
    activity: Activity,
    popups: PopupController,
    initialOnline: Boolean,
    progress: Int,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefreshRequested: () -> Unit,
    onProgressUpdate: (Int) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: (Boolean) -> Unit,
    onMainFrameError: () -> Unit,
    onNavigationBlocked: () -> Unit,
    onRenderGone: () -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    onRequestCameraPermission: ((Boolean) -> Unit) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().testTag("webview_container")) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebViewConfig.configureServiceWorkers()
                val webView = WebView(context)
                webView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                WebViewConfig.apply(webView, initialOnline)

                val bridge = EkambaniBridge(activity) { webView }
                webView.addJavascriptInterface(bridge, EkambaniBridge.INTERFACE_NAME)
                webView.setDownloadListener(EkambaniDownloadListener(activity) { webView })

                webView.webChromeClient = EkambaniWebChromeClient(
                    activity = activity,
                    popups = popups,
                    onProgressUpdate = onProgressUpdate,
                    onOpenFileChooser = onOpenFileChooser,
                    onRequestCameraPermission = onRequestCameraPermission
                )
                webView.webViewClient = EkambaniWebViewClient(
                    context = activity,
                    onPageLoadStarted = {
                        bridge.innerScrolled = false
                        onPageStarted()
                    },
                    onPageLoadFinished = onPageFinished,
                    onMainFrameError = onMainFrameError,
                    onNavigationBlocked = onNavigationBlocked,
                    onRenderGone = onRenderGone
                )

                webView.loadUrl(AppConfig.WEBSITE_URL)
                onWebViewCreated(webView)

                SwipeRefreshLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setColorSchemeColors(android.graphics.Color.parseColor("#1F6B2C"))
                    isEnabled = AppConfig.ENABLE_PULL_TO_REFRESH
                    // Refresh only from the very top of the page and never while an inner list/table is scrolled.
                    setOnChildScrollUpCallback { _, _ ->
                        webView.scrollY > 0 || webView.canScrollVertically(-1) || bridge.innerScrolled
                    }
                    setOnRefreshListener { onRefreshRequested() }
                    addView(webView)
                }
            },
            update = { layout -> layout.isRefreshing = isRefreshing }
        )

        AnimatedVisibility(
            visible = isLoading && progress in 1..99,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
