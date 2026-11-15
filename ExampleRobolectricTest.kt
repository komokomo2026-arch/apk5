package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ekambani Main", appName)
    }

    @Test
    fun testOfflineStringsResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.no_internet_title)
        val retry = context.getString(R.string.retry_button)
        assertEquals("NO INTERNET CONNECTION", title)
        assertEquals("Retry", retry)
    }

    @Test
    fun testDefaultWebsiteUrl() {
        assertEquals("https://komokomo2026-arch.github.io/pos12/", AppConfig.WEBSITE_URL)
        assertEquals("komokomo2026-arch.github.io", AppConfig.trustedHost)
    }

    @Test
    fun testTrustedUrlValidation() {
        // Trusted host
        assertTrue(AppConfig.isTrustedUrl("https://komokomo2026-arch.github.io/pos12/"))
        assertTrue(AppConfig.isTrustedUrl("https://komokomo2026-arch.github.io/pos12/dashboard.html"))

        // Google Drive and OAuth domains
        assertTrue(AppConfig.isTrustedUrl("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(AppConfig.isTrustedUrl("https://drive.google.com/drive/my-drive"))

        // Insecure or untrusted domains
        assertFalse(AppConfig.isTrustedUrl("http://komokomo2026-arch.github.io/pos12/")) // Non-HTTPS rejected
        assertFalse(AppConfig.isTrustedUrl("https://untrusted-external-site.com/"))
        assertFalse(AppConfig.isTrustedUrl("javascript:alert(1)"))
        assertTrue(AppConfig.isPrimarySite("https://komokomo2026-arch.github.io/pos12/"))
        assertFalse(AppConfig.isPrimarySite("https://accounts.google.com/"))
        assertFalse(AppConfig.isTrustedUrl(null))
        assertFalse(AppConfig.isTrustedUrl(""))
    }
}
