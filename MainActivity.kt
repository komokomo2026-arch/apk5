package com.example

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.config.AppConfig
import com.example.network.NetworkMonitor
import com.example.ui.EkambaniWebViewContainer
import com.example.ui.LoadingSplash
import com.example.ui.OfflineScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.webview.PopupController
import com.example.webview.WebViewConfig
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var popups: PopupController
    private var webView: WebView? = null

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraCallback: ((Boolean) -> Unit)? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else null
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingCameraCallback?.invoke(granted)
        pendingCameraCallback = null
        if (!granted) {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureDisplayCutout()
        applyImmersiveFullScreen()

        networkMonitor = NetworkMonitor(this)
        popups = PopupController(this)

        setContent {
            MyApplicationTheme {
                EkambaniApp(
                    activity = this,
                    popups = popups,
                    networkMonitor = networkMonitor,
                    onRegisterWebView = { webView = it },
                    onOpenFileChooser = { callback, params ->
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = callback
                        launchFileChooser(params)
                    },
                    onRequestCameraPermission = { callback ->
                        pendingCameraCallback = callback
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onRenderGone = { recreate() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        applyImmersiveFullScreen()
    }

    override fun onPause() {
        // Releases the camera and saves cookies/sessions; website data is never cleared.
        webView?.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullScreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersiveFullScreen()
    }

    override fun onDestroy() {
        networkMonitor.unregister()
        popups.close()
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        webView?.apply {
            stopLoading()
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun applyImmersiveFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun configureDisplayCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
    }

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams?) {
        try {
            val intent = params?.createIntent() ?: android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            fileChooserLauncher.launch(intent)
        } catch (_: Exception) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }
}

@Composable
fun EkambaniApp(
    activity: MainActivity,
    popups: PopupController,
    networkMonitor: NetworkMonitor,
    onRegisterWebView: (WebView) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    onRequestCameraPermission: ((Boolean) -> Unit) -> Unit,
    onRenderGone: () -> Unit
) {
    val isOnline by networkMonitor.isOnline.collectAsState()
    var showError by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var lastBackPress by remember { mutableStateOf(0L) }
    val initialOnline = remember { networkMonitor.isCurrentlyOnline() }

    BackHandler(enabled = !showError) {
        val wv = currentWebView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000) {
                activity.finish()
            } else {
                lastBackPress = now
                Toast.makeText(activity, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Online/offline: switch cache preference (the page and its service worker are never reloaded just because of this).
    LaunchedEffect(isOnline, currentWebView) {
        currentWebView?.settings?.cacheMode = WebViewConfig.cacheModeFor(isOnline)
        if (isOnline && showError) {
            showError = false
            currentWebView?.loadUrl(AppConfig.WEBSITE_URL)
        }
    }

    // Never leave the refresh spinner stuck on slow connections.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(20_000)
            isRefreshing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .displayCutoutPadding()
            .imePadding()
    ) {
        EkambaniWebViewContainer(
            activity = activity,
            popups = popups,
            initialOnline = initialOnline,
            progress = progress,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            onRefreshRequested = {
                if (!isRefreshing) {
                    isRefreshing = true
                    currentWebView?.reload()
                }
            },
            onProgressUpdate = {
                progress = it
                isLoading = it < 100
                canGoBack = currentWebView?.canGoBack() == true
            },
            onPageStarted = {
                isLoading = true
                showError = false
            },
            onPageFinished = { success ->
                isLoading = false
                isRefreshing = false
                canGoBack = currentWebView?.canGoBack() == true
                if (success) hasLoadedOnce = true
            },
            onMainFrameError = {
                isLoading = false
                isRefreshing = false
                showError = true
            },
            onNavigationBlocked = {
                isLoading = false
                isRefreshing = false
                Toast.makeText(activity, activity.getString(R.string.no_internet_title), Toast.LENGTH_SHORT).show()
            },
            onRenderGone = onRenderGone,
            onOpenFileChooser = onOpenFileChooser,
            onRequestCameraPermission = onRequestCameraPermission,
            onWebViewCreated = {
                currentWebView = it
                onRegisterWebView(it)
            }
        )

        if (!hasLoadedOnce && !showError) LoadingSplash()

        if (showError) {
            OfflineScreen(onRetry = {
                showError = false
                currentWebView?.loadUrl(AppConfig.WEBSITE_URL)
            })
        }
    }
}
