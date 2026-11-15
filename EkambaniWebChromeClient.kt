package com.example.webview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.example.config.AppConfig

/** Camera permission (barcode / QR scanning), file chooser, popups and URL-free JavaScript dialogs. */
class EkambaniWebChromeClient(
    private val activity: Activity,
    private val popups: PopupController,
    private val onProgressUpdate: (Int) -> Unit,
    private val onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    private val onRequestCameraPermission: ((Boolean) -> Unit) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressUpdate(newProgress)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        // Only the Ekambani website itself may use the camera.
        if (!AppConfig.isPrimarySite(request.origin.toString())) {
            request.deny()
            return
        }
        val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (!wantsCamera) {
            request.deny()
            return
        }
        val grant = arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(grant)
        } else {
            onRequestCameraPermission { granted ->
                if (granted) request.grant(grant) else request.deny()
            }
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        onOpenFileChooser(filePathCallback, fileChooserParams)
        return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        if (resultMsg == null) return false
        return popups.open(resultMsg)
    }

    // Dialogs without the "The page at https://… says" title, so the address is never shown.
    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (result == null) return false
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (result == null) return false
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        if (result == null) return false
        val input = EditText(activity).apply { setText(defaultValue ?: "") }
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }
}
