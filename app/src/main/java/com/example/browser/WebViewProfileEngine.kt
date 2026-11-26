package com.example.browser

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.BrowserConstants

object WebViewProfileEngine {

    fun isMultiProfileSupported(): Boolean {
        return runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)
    }

    fun getOrCreateProfile(webViewProfileName: String): Profile? {
        if (!isMultiProfileSupported()) return null
        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(webViewProfileName)
        }.getOrNull()
    }

    fun bindWebViewToProfile(webView: WebView, webViewProfileName: String): Boolean {
        if (!isMultiProfileSupported()) return false
        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(webViewProfileName)
            WebViewCompat.setProfile(webView, webViewProfileName)
            true
        }.getOrDefault(false)
    }

    fun cookieManagerForProfile(webViewProfileName: String): CookieManager {
        return getOrCreateProfile(webViewProfileName)?.cookieManager
            ?: CookieManager.getInstance()
    }

    fun webStorageForProfile(webViewProfileName: String): WebStorage {
        return getOrCreateProfile(webViewProfileName)?.webStorage
            ?: WebStorage.getInstance()
    }

    fun flushCookiesForProfile(webViewProfileName: String) {
        runCatching {
            cookieManagerForProfile(webViewProfileName).flush()
        }
    }

    fun flushCookiesForProfiles(webViewProfileNames: Collection<String>) {
        if (webViewProfileNames.isEmpty()) {
            TikTokSessionManager.flushCookies()
            return
        }
        webViewProfileNames.distinct().forEach { name ->
            flushCookiesForProfile(name)
        }
    }

    fun deleteProfileStorage(webViewProfileName: String) {
        runCatching {
            val cookieManager = cookieManagerForProfile(webViewProfileName)
            val webStorage = webStorageForProfile(webViewProfileName)
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            webStorage.deleteAllData()
            if (isMultiProfileSupported() &&
                webViewProfileName != BrowserConstants.DEFAULT_WEBVIEW_PROFILE_NAME &&
                webViewProfileName != Profile.DEFAULT_PROFILE_NAME
            ) {
                ProfileStore.getInstance().deleteProfile(webViewProfileName)
            }
        }
    }
}
