package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Patterns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "desktop_browser_settings"
)

data class BrowserSettings(
    val homepageUrl: String = BrowserConstants.DEFAULT_HOMEPAGE_URL,
    val desktopModeEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val websiteZoomPercent: Int = BrowserConstants.DEFAULT_ZOOM_PERCENT,
    val hasCompletedOnboarding: Boolean = false
)

object BrowserConstants {
    const val DEFAULT_HOMEPAGE_URL = "https://www.tiktok.com/tiktokstudio"
    const val TIKTOK_LOGIN_URL = "https://www.tiktok.com/login"

    const val MIN_ZOOM_PERCENT = 50
    const val MAX_ZOOM_PERCENT = 200
    const val DEFAULT_ZOOM_PERCENT = 100
    const val ZOOM_STEP_PERCENT = 10

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun snapZoomPercent(rawPercent: Float): Int {
        val stepped = (rawPercent / ZOOM_STEP_PERCENT).roundToInt() * ZOOM_STEP_PERCENT
        return stepped.coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)
    }
}

object UrlUtils {
    fun normalizeHomepageUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) return null

        val candidate = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = Uri.parse(candidate)
        val host = uri.host ?: return null
        if (!host.contains('.') && host != "localhost") return null
        return if (Patterns.WEB_URL.matcher(candidate).matches() || host == "localhost") {
            candidate
        } else {
            null
        }
    }

    fun normalizeAddressBarInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return BrowserConstants.DEFAULT_HOMEPAGE_URL

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        val looksLikeDomain = !trimmed.contains(" ") &&
            (trimmed.contains(".") || trimmed.startsWith("localhost", ignoreCase = true))
        if (looksLikeDomain) {
            val candidate = "https://$trimmed"
            if (Patterns.WEB_URL.matcher(candidate).matches() ||
                trimmed.startsWith("localhost", ignoreCase = true)
            ) {
                return candidate
            }
        }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return "https://www.google.com/search?q=$encoded"
    }

    fun isSecureUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true)
}

class BrowserPreferencesRepository(private val context: Context) {

    private object Keys {
        val HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode_enabled")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val WEBSITE_ZOOM_PERCENT = intPreferencesKey("website_zoom_percent")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<BrowserSettings> = context.browserDataStore.data.map { prefs ->
        val rawZoom = prefs[Keys.WEBSITE_ZOOM_PERCENT] ?: BrowserConstants.DEFAULT_ZOOM_PERCENT
        BrowserSettings(
            homepageUrl = prefs[Keys.HOMEPAGE_URL] ?: BrowserConstants.DEFAULT_HOMEPAGE_URL,
            desktopModeEnabled = prefs[Keys.DESKTOP_MODE] ?: true,
            javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            websiteZoomPercent = BrowserConstants.snapZoomPercent(rawZoom.toFloat()),
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun saveHomepageUrl(url: String) {
        context.browserDataStore.edit { prefs ->
            prefs[Keys.HOMEPAGE_URL] = url
        }
    }

    suspend fun setDesktopMode(enabled: Boolean) {
        context.browserDataStore.edit { prefs ->
            prefs[Keys.DESKTOP_MODE] = enabled
        }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.browserDataStore.edit { prefs ->
            prefs[Keys.JAVASCRIPT_ENABLED] = enabled
        }
    }

    suspend fun setWebsiteZoomPercent(percent: Int) {
        val normalized = BrowserConstants.snapZoomPercent(percent.toFloat())
        context.browserDataStore.edit { prefs ->
            prefs[Keys.WEBSITE_ZOOM_PERCENT] = normalized
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.browserDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }
}
