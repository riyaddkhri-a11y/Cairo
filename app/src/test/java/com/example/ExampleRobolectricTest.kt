package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.browser.WebViewConfig
import com.example.data.BrowserConstants
import com.example.data.BrowserSettings
import com.example.data.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Arabic localization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("متصفح سطح المكتب", appName)
    }

    @Test
    fun `all core Arabic UI terminology strings are localized`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("الرئيسية", context.getString(R.string.cd_home))
        assertEquals("رجوع", context.getString(R.string.cd_back))
        assertEquals("تقدم", context.getString(R.string.cd_forward))
        assertEquals("تحديث", context.getString(R.string.cd_refresh))
        assertEquals("الإعدادات", context.getString(R.string.settings_title))
        assertEquals("علامات التبويب", context.getString(R.string.tabs_title))
        assertEquals("التنزيلات", context.getString(R.string.downloads_title))
        assertEquals("وضع سطح المكتب", context.getString(R.string.settings_desktop_mode_title))
        assertEquals("وضع الهاتف", context.getString(R.string.badge_mobile))
        assertEquals("حجم عرض المواقع", context.getString(R.string.settings_zoom_title))
        assertEquals("إعادة ضبط الحجم", context.getString(R.string.settings_zoom_reset))
        assertEquals("الصفحة الرئيسية", context.getString(R.string.settings_homepage_label))
        assertEquals("حفظ", context.getString(R.string.settings_save_homepage))
        assertEquals("إلغاء", context.getString(R.string.settings_cancel))
        assertEquals("مسح بيانات التصفح", context.getString(R.string.settings_clear_data_title))
        assertEquals("جارٍ التحميل", context.getString(R.string.loading_status))
        assertEquals("خطأ في الاتصال", context.getString(R.string.error_title))
        assertEquals("فتح الموقع", context.getString(R.string.error_open_home))
        assertEquals("جافاسكريبت", context.getString(R.string.settings_javascript_title))
        assertEquals("إغلاق علامة التبويب", context.getString(R.string.tabs_close_tab))
        assertEquals("علامة تبويب جديدة", context.getString(R.string.tabs_new_tab))
    }

    @Test
    fun `default homepage is TikTok Studio`() {
        assertEquals("https://www.tiktok.com/tiktokstudio", BrowserConstants.DEFAULT_HOMEPAGE_URL)
    }

    @Test
    fun `homepage normalization handles bare domain and rejects spaces`() {
        assertEquals(
            "https://www.tiktok.com/tiktokstudio",
            UrlUtils.normalizeHomepageUrl("www.tiktok.com/tiktokstudio")
        )
        assertEquals(
            "https://example.com/studio",
            UrlUtils.normalizeHomepageUrl("https://example.com/studio")
        )
        assertNull(UrlUtils.normalizeHomepageUrl(""))
        assertNull(UrlUtils.normalizeHomepageUrl("invalid url with spaces"))
    }

    @Test
    fun `address bar normalization distinguishes URLs and search queries`() {
        assertEquals(
            "https://tiktok.com",
            UrlUtils.normalizeAddressBarInput("tiktok.com")
        )
        assertTrue(
            UrlUtils.normalizeAddressBarInput("desktop browser test")
                .startsWith("https://www.google.com/search?q=")
        )
        assertTrue(UrlUtils.isSecureUrl("https://www.tiktok.com/tiktokstudio"))
        assertFalse(UrlUtils.isSecureUrl("http://example.com"))
    }

    @Test
    fun `website zoom snaps to 10 percent steps within 50 to 200 range`() {
        assertEquals(100, BrowserSettings().websiteZoomPercent)
        assertEquals(50, BrowserConstants.snapZoomPercent(10f))
        assertEquals(50, BrowserConstants.snapZoomPercent(54f))
        assertEquals(60, BrowserConstants.snapZoomPercent(55f))
        assertEquals(130, BrowserConstants.snapZoomPercent(133f))
        assertEquals(200, BrowserConstants.snapZoomPercent(250f))
    }

    @Test
    fun `zoom script preserves desktop viewport and scales full page`() {
        val zoomedDesktop = WebViewConfig.buildViewportAndZoomScript(
            BrowserSettings(desktopModeEnabled = true, websiteZoomPercent = 150)
        )
        assertTrue(zoomedDesktop.contains("width=1280, user-scalable=yes"))
        assertTrue(zoomedDesktop.contains("root.style.zoom = '1.5'"))

        val shrunkDesktop = WebViewConfig.buildViewportAndZoomScript(
            BrowserSettings(desktopModeEnabled = true, websiteZoomPercent = 50)
        )
        assertTrue(shrunkDesktop.contains("width=1280, user-scalable=yes"))
        assertTrue(shrunkDesktop.contains("root.style.zoom = '0.5'"))
        assertTrue(shrunkDesktop.contains("2560px"))

        val resetDesktop = WebViewConfig.buildViewportAndZoomScript(
            BrowserSettings(desktopModeEnabled = true, websiteZoomPercent = 100)
        )
        assertTrue(resetDesktop.contains("removeProperty('zoom')"))
    }

    @Test
    fun `zoom setting strings match requested Arabic labels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("حجم عرض المواقع", context.getString(R.string.settings_zoom_title))
        assertEquals("إعادة ضبط الحجم", context.getString(R.string.settings_zoom_reset))
    }
}
