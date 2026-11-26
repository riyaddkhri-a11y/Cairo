package com.example

import android.content.Context
import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import com.example.browser.TikTokSessionManager
import com.example.browser.WebViewConfig
import com.example.data.BrowserConstants
import com.example.data.BrowserPreferencesRepository
import com.example.data.BrowserSettings
import com.example.data.ProfileColor
import com.example.data.ProfileIcon
import com.example.data.ProfileSerialization
import com.example.data.SavedTabState
import com.example.data.UrlUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `app name matches Tok Studio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tok Studio", appName)
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
    fun `website zoom snaps to 10 percent steps within 50 to 400 range`() {
        assertEquals(100, BrowserSettings().websiteZoomPercent)
        assertEquals(50, BrowserConstants.snapZoomPercent(10f))
        assertEquals(50, BrowserConstants.snapZoomPercent(54f))
        assertEquals(60, BrowserConstants.snapZoomPercent(55f))
        assertEquals(130, BrowserConstants.snapZoomPercent(133f))
        assertEquals(250, BrowserConstants.snapZoomPercent(250f))
        assertEquals(400, BrowserConstants.snapZoomPercent(550f))
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

    @Test
    fun `welcome screen defaults to shown on first launch and matches Arabic text`() {
        assertFalse(BrowserSettings().hasCompletedOnboarding)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("مرحباً بك في Tok Studio 👋", context.getString(R.string.welcome_title))
        assertEquals("ابدأ الآن", context.getString(R.string.welcome_start_button))
    }

    @Test
    fun `tiktok login constants and Arabic strings match requirements`() {
        assertEquals("https://www.tiktok.com/login", BrowserConstants.TIKTOK_LOGIN_URL)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("تسجيل الدخول إلى TikTok", context.getString(R.string.settings_tiktok_login_button))
        assertEquals("تم تسجيل الدخول", context.getString(R.string.settings_tiktok_logged_in))
        assertEquals("لم يتم تسجيل الدخول", context.getString(R.string.settings_tiktok_logged_out))
        assertEquals("تسجيل الخروج", context.getString(R.string.settings_tiktok_logout_button))
    }

    @Test
    fun `tiktok session cookie detector distinguishes authenticated and anonymous cookies`() {
        assertFalse(TikTokSessionManager.hasActiveSessionCookies(null))
        assertFalse(TikTokSessionManager.hasActiveSessionCookies("ttwid=abc123; msToken=xyz"))
        assertFalse(TikTokSessionManager.hasActiveSessionCookies("sessionid=; sid_tt=deleted"))
        assertTrue(TikTokSessionManager.hasActiveSessionCookies("ttwid=abc123; sessionid=real_session_token"))
        assertTrue(TikTokSessionManager.hasActiveSessionCookies("sid_tt=token_value; uid_tt=12345"))
    }

    @Test
    fun `tiktok session persists across login and studio urls and clears cleanly on logout`() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie("https://example.com", "other_site=keep_me; Path=/")
        cookieManager.setCookie(
            BrowserConstants.TIKTOK_LOGIN_URL,
            "sessionid=persisted_token_123; Domain=.tiktok.com; Path=/; Secure"
        )
        TikTokSessionManager.flushCookies(cookieManager)

        assertTrue(TikTokSessionManager.isLoggedIn(cookieManager))
        val studioCookies = cookieManager.getCookie(BrowserConstants.DEFAULT_HOMEPAGE_URL)
        assertTrue(TikTokSessionManager.hasActiveSessionCookies(studioCookies))

        TikTokSessionManager.clearTikTokSession(cookieManager)
        assertFalse(TikTokSessionManager.isLoggedIn(cookieManager))
        val otherSiteCookies = cookieManager.getCookie("https://example.com")
        assertTrue(otherSiteCookies?.contains("other_site=keep_me") == true)
    }

    @Test
    fun `multi profile picker and management Arabic strings match requirements`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("من يستخدم Tok Studio؟", context.getString(R.string.profile_picker_title))
        assertEquals("إضافة ملف شخصي جديد", context.getString(R.string.profile_add_new))
        assertEquals("إدارة الملفات الشخصية", context.getString(R.string.profile_manage_button))
        assertEquals("تبديل الملف الشخصي", context.getString(R.string.profile_switcher_title))
    }

    @Test
    fun `multiple profiles persist independently across restarts without mixing settings or tabs`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BrowserPreferencesRepository(context)
        repo.ensureDefaultProfileMigrated()

        val profile1 = repo.createProfile("حساب أول", ProfileColor.CYAN.id, ProfileIcon.VIDEOCAM.id)
        val profile2 = repo.createProfile("حساب ثاني", ProfileColor.PINK.id, ProfileIcon.STORE.id)
        val profile3 = repo.createProfile("حساب ثالث", ProfileColor.EMERALD.id, ProfileIcon.STAR.id)

        assertNotEquals(profile1.webViewProfileName, profile2.webViewProfileName)
        assertNotEquals(profile2.webViewProfileName, profile3.webViewProfileName)
        assertEquals(BrowserConstants.DEFAULT_WEBVIEW_PROFILE_NAME, "Default")

        // Customize Profile 1 independently
        repo.updateProfileSettings(profile1.id) {
            it.copy(
                websiteZoomPercent = 160,
                desktopModeEnabled = true,
                homepageUrl = "https://www.tiktok.com/tiktokstudio"
            )
        }
        repo.saveProfileTabs(
            profileId = profile1.id,
            tabs = listOf(SavedTabState("tab1", "https://www.tiktok.com/tiktokstudio", "Studio 1")),
            activeTabId = "tab1"
        )

        // Customize Profile 2 independently
        repo.updateProfileSettings(profile2.id) {
            it.copy(
                websiteZoomPercent = 80,
                desktopModeEnabled = false,
                homepageUrl = "https://example.com/creator2"
            )
        }
        repo.saveProfileTabs(
            profileId = profile2.id,
            tabs = listOf(SavedTabState("tab2", "https://example.com/creator2", "Creator 2")),
            activeTabId = "tab2"
        )

        // Rename & recolor Profile 2
        repo.updateProfileAppearance(profile2.id, "حساب محدث", ProfileColor.AMBER.id, ProfileIcon.WORK.id)

        // Delete Profile 3 and verify Profile 1, Profile 2, and Default remain intact
        repo.deleteProfile(profile3.id)

        // Simulate reopening the app with a fresh repository read
        val reopenedRepo = BrowserPreferencesRepository(context)
        val reopenedState = reopenedRepo.appStateFlow.first()

        assertNull(reopenedState.profiles.firstOrNull { it.id == profile3.id })
        val restored1 = reopenedState.profiles.first { it.id == profile1.id }
        val restored2 = reopenedState.profiles.first { it.id == profile2.id }

        assertEquals(160, restored1.settings.websiteZoomPercent)
        assertTrue(restored1.settings.desktopModeEnabled)
        assertEquals("https://www.tiktok.com/tiktokstudio", restored1.settings.homepageUrl)
        assertEquals("tab1", restored1.activeTabId)
        assertEquals(listOf(SavedTabState("tab1", "https://www.tiktok.com/tiktokstudio", "Studio 1")), restored1.tabs)

        assertEquals("حساب محدث", restored2.name)
        assertEquals(ProfileColor.AMBER.id, restored2.colorId)
        assertEquals(ProfileIcon.WORK.id, restored2.iconId)
        assertEquals(80, restored2.settings.websiteZoomPercent)
        assertFalse(restored2.settings.desktopModeEnabled)
        assertEquals("https://example.com/creator2", restored2.settings.homepageUrl)
        assertEquals("tab2", restored2.activeTabId)
        assertEquals(listOf(SavedTabState("tab2", "https://example.com/creator2", "Creator 2")), restored2.tabs)

        // Verify round-trip JSON serialization preserves all profile fields
        val encoded = ProfileSerialization.encodeProfiles(reopenedState.profiles)
        val decoded = ProfileSerialization.decodeProfiles(encoded)
        assertEquals(reopenedState.profiles, decoded)
    }

    @Test
    fun `file chooser intent builder creates video upload intent`() {
        val intent = WebViewConfig.buildFileChooserIntent(null)
        assertEquals(android.content.Intent.ACTION_GET_CONTENT, intent.action)
        assertEquals("*/*", intent.type)
    }
}
