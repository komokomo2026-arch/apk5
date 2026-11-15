package com.example.config

import android.net.Uri

/**
 * EKAMBANI MAIN - Android packaging configuration.
 * The only thing the app does is show the existing website below; the website itself is never modified.
 */
object AppConfig {

    const val WEBSITE_URL: String = "https://komokomo2026-arch.github.io/pos12/"

    const val APP_NAME: String = "Ekambani Main"

    const val ENABLE_PULL_TO_REFRESH: Boolean = true

    const val OPEN_EXTERNAL_LINKS_IN_BROWSER: Boolean = true

    /**
     * Google refuses OAuth sign-in inside an Android WebView when the user agent contains "; wv".
     * With this on, the app reports a standard mobile Chrome user agent so the website's existing
     * Google Drive sign-in popup has a chance to work. Google may still block it; see README.
     */
    const val GOOGLE_AUTH_COMPAT_USER_AGENT: Boolean = true

    val trustedHost: String by lazy {
        try {
            Uri.parse(WEBSITE_URL).host ?: "komokomo2026-arch.github.io"
        } catch (_: Exception) {
            "komokomo2026-arch.github.io"
        }
    }

    /** Google sign-in / Drive API hosts that may load inside the app. */
    val trustedAuthDomains: Set<String> = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "oauth2.googleapis.com",
        "apis.google.com",
        "www.googleapis.com",
        "content.googleapis.com",
        "drive.google.com",
        "docs.google.com",
        "ssl.gstatic.com",
        "www.gstatic.com",
        "myaccount.google.com"
    )

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)
            if (!"https".equals(uri.scheme, ignoreCase = true)) null else uri.host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /** True only for the Ekambani website itself. Used to decide who may talk to the Android bridge. */
    fun isPrimarySite(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return host == trustedHost.lowercase()
    }

    /** True for the website or a Google sign-in/Drive host: allowed to stay inside the app. */
    fun isTrustedUrl(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        if (host == trustedHost.lowercase() || host.endsWith("." + trustedHost.lowercase())) return true
        return trustedAuthDomains.any { host == it || host.endsWith(".$it") }
    }
}
