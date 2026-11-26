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
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

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
    const val MAX_ZOOM_PERCENT = 400
    const val DEFAULT_ZOOM_PERCENT = 100
    const val ZOOM_STEP_PERCENT = 10

    const val DEFAULT_PROFILE_ID = "default"
    const val DEFAULT_WEBVIEW_PROFILE_NAME = "Default"
    const val DEFAULT_PROFILE_DISPLAY_NAME = "الملف الرئيسي"

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun snapZoomPercent(rawPercent: Float): Int {
        val stepped = (rawPercent / ZOOM_STEP_PERCENT).roundToInt() * ZOOM_STEP_PERCENT
        return stepped.coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)
    }

    fun webViewProfileNameForId(profileId: String): String {
        return if (profileId == DEFAULT_PROFILE_ID) {
            DEFAULT_WEBVIEW_PROFILE_NAME
        } else {
            "tok_profile_${profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
        }
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

object ProfileSerialization {

    fun encodeProfiles(profiles: List<BrowserProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("webViewProfileName", profile.webViewProfileName)
                put("colorId", profile.colorId)
                put("iconId", profile.iconId)
                put("activeTabId", profile.activeTabId ?: JSONObject.NULL)
                put("createdAtMillis", profile.createdAtMillis)
                put(
                    "settings",
                    JSONObject().apply {
                        put("homepageUrl", profile.settings.homepageUrl)
                        put("desktopModeEnabled", profile.settings.desktopModeEnabled)
                        put("javaScriptEnabled", profile.settings.javaScriptEnabled)
                        put("websiteZoomPercent", profile.settings.websiteZoomPercent)
                    }
                )
                val tabsArray = JSONArray()
                profile.tabs.forEach { tab ->
                    tabsArray.put(
                        JSONObject().apply {
                            put("id", tab.id)
                            put("url", tab.url)
                            put("title", tab.title)
                            put("isChildTab", tab.isChildTab)
                            put("parentTabId", tab.parentTabId ?: JSONObject.NULL)
                        }
                    )
                }
                put("tabs", tabsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeProfiles(rawJson: String?): List<BrowserProfile> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() }
                        ?: BrowserConstants.DEFAULT_PROFILE_DISPLAY_NAME
                    val webViewProfileName = obj.optString("webViewProfileName")
                        .takeIf { it.isNotBlank() }
                        ?: BrowserConstants.webViewProfileNameForId(id)
                    val colorId = obj.optString("colorId", ProfileColor.CYAN.id)
                    val iconId = obj.optString("iconId", ProfileIcon.PERSON.id)
                    val activeTabId = obj.optString("activeTabId").takeIf {
                        it.isNotBlank() && it != "null"
                    }
                    val createdAtMillis = obj.optLong("createdAtMillis", 0L)

                    val settingsObj = obj.optJSONObject("settings")
                    val rawZoom = settingsObj?.optInt(
                        "websiteZoomPercent",
                        BrowserConstants.DEFAULT_ZOOM_PERCENT
                    ) ?: BrowserConstants.DEFAULT_ZOOM_PERCENT
                    val settings = BrowserSettings(
                        homepageUrl = settingsObj?.optString(
                            "homepageUrl",
                            BrowserConstants.DEFAULT_HOMEPAGE_URL
                        )?.takeIf { it.isNotBlank() } ?: BrowserConstants.DEFAULT_HOMEPAGE_URL,
                        desktopModeEnabled = settingsObj?.optBoolean("desktopModeEnabled", true)
                            ?: true,
                        javaScriptEnabled = settingsObj?.optBoolean("javaScriptEnabled", true)
                            ?: true,
                        websiteZoomPercent = BrowserConstants.snapZoomPercent(rawZoom.toFloat())
                    )

                    val tabsArray = obj.optJSONArray("tabs")
                    val tabs = buildList {
                        if (tabsArray != null) {
                            for (t in 0 until tabsArray.length()) {
                                val tabObj = tabsArray.optJSONObject(t) ?: continue
                                val tabId = tabObj.optString("id").takeIf { it.isNotBlank() }
                                    ?: continue
                                val tabUrl = tabObj.optString("url")
                                val tabTitle = tabObj.optString("title")
                                val isChildTab = tabObj.optBoolean("isChildTab", false)
                                val parentTabId = tabObj.optString("parentTabId").takeIf {
                                    it.isNotBlank() && it != "null"
                                }
                                add(
                                    SavedTabState(
                                        id = tabId,
                                        url = tabUrl,
                                        title = tabTitle,
                                        isChildTab = isChildTab,
                                        parentTabId = parentTabId
                                    )
                                )
                            }
                        }
                    }

                    add(
                        BrowserProfile(
                            id = id,
                            name = name,
                            webViewProfileName = webViewProfileName,
                            colorId = colorId,
                            iconId = iconId,
                            settings = settings,
                            tabs = tabs,
                            activeTabId = activeTabId,
                            createdAtMillis = createdAtMillis
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

class BrowserPreferencesRepository(private val context: Context) {

    private object Keys {
        val HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode_enabled")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val WEBSITE_ZOOM_PERCENT = intPreferencesKey("website_zoom_percent")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val HAS_MIGRATED_PROFILES = booleanPreferencesKey("has_migrated_profiles")
    }

    val settingsFlow: Flow<BrowserSettings> = context.browserDataStore.data.map { prefs ->
        legacySettingsFromPrefs(prefs)
    }

    val appStateFlow: Flow<AppPreferencesState> = context.browserDataStore.data.map { prefs ->
        val onboardingDone = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        val hasMigrated = prefs[Keys.HAS_MIGRATED_PROFILES] ?: false
        val storedRaw = prefs[Keys.PROFILES_JSON]
        val profiles = if (!hasMigrated && storedRaw == null) {
            listOf(buildDefaultMigratedProfile(legacySettingsFromPrefs(prefs)))
        } else {
            ProfileSerialization.decodeProfiles(storedRaw)
        }
        AppPreferencesState(
            profiles = profiles,
            lastActiveProfileId = prefs[Keys.ACTIVE_PROFILE_ID],
            hasCompletedOnboarding = onboardingDone
        )
    }

    suspend fun ensureDefaultProfileMigrated() {
        context.browserDataStore.edit { prefs ->
            val hasMigrated = prefs[Keys.HAS_MIGRATED_PROFILES] ?: false
            if (!hasMigrated && prefs[Keys.PROFILES_JSON] == null) {
                val migrated = buildDefaultMigratedProfile(legacySettingsFromPrefs(prefs))
                prefs[Keys.PROFILES_JSON] = ProfileSerialization.encodeProfiles(listOf(migrated))
                prefs[Keys.HAS_MIGRATED_PROFILES] = true
                if (prefs[Keys.ACTIVE_PROFILE_ID] == null) {
                    prefs[Keys.ACTIVE_PROFILE_ID] = migrated.id
                }
            } else if (!hasMigrated) {
                prefs[Keys.HAS_MIGRATED_PROFILES] = true
            }
        }
    }

    suspend fun createProfile(
        name: String,
        colorId: String,
        iconId: String
    ): BrowserProfile {
        val cleanName = name.trim().ifBlank { BrowserConstants.DEFAULT_PROFILE_DISPLAY_NAME }
        val newId = UUID.randomUUID().toString().replace("-", "").take(12)
        val newProfile = BrowserProfile(
            id = newId,
            name = cleanName,
            webViewProfileName = BrowserConstants.webViewProfileNameForId(newId),
            colorId = colorId,
            iconId = iconId,
            settings = BrowserSettings(
                homepageUrl = BrowserConstants.DEFAULT_HOMEPAGE_URL,
                desktopModeEnabled = true,
                javaScriptEnabled = true,
                websiteZoomPercent = BrowserConstants.DEFAULT_ZOOM_PERCENT
            ),
            tabs = emptyList(),
            activeTabId = null,
            createdAtMillis = System.currentTimeMillis()
        )
        context.browserDataStore.edit { prefs ->
            val current = readOrMigrateProfiles(prefs)
            val updated = current + newProfile
            prefs[Keys.PROFILES_JSON] = ProfileSerialization.encodeProfiles(updated)
            prefs[Keys.HAS_MIGRATED_PROFILES] = true
            prefs[Keys.ACTIVE_PROFILE_ID] = newProfile.id
        }
        return newProfile
    }

    suspend fun updateProfileAppearance(
        profileId: String,
        name: String,
        colorId: String,
        iconId: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        context.browserDataStore.edit { prefs ->
            val current = readOrMigrateProfiles(prefs)
            val updated = current.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(
                        name = cleanName,
                        colorId = colorId,
                        iconId = iconId
                    )
                } else {
                    profile
                }
            }
            prefs[Keys.PROFILES_JSON] = ProfileSerialization.encodeProfiles(updated)
            prefs[Keys.HAS_MIGRATED_PROFILES] = true
        }
    }

    suspend fun updateProfileSettings(
        profileId: String,
        transform: (BrowserSettings) -> BrowserSettings
    ) {
        context.browserDataStore.edit { prefs ->
            val current = readOrMigrateProfiles(prefs)
            val updated = current.map { profile ->
                if (profile.id == profileId) {
                    val nextSettings = transform(profile.settings).let { s ->
                        s.copy(
                            websiteZoomPercent = BrowserConstants.snapZoomPercent(
                                s.websiteZoomPercent.toFloat()
                            )
                        )
                    }
                    profile.copy(settings = nextSettings)
                } else {
                    profile
                }
            }
            prefs[Keys.PROFILES_JSON] = ProfileSerialization.encodeProfiles(updated)
            prefs[Keys.HAS_MIGRATED_PROFILES] = true
        }
    }

    suspend fun saveProfileTabs(
        profileId: String,
        tabs: List<SavedTabState>,
        activeTabId: String?
    ) {
        context.browserDataStore.edit { prefs ->
            val current = readOrMigrateProfiles(prefs)
            val updated = current.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(tabs = tabs, activeTabId = activeTabId)
                } else {
                    profile
                }
            }
            prefs[Keys.PROFILES_JSON] = ProfileSerialization.encodeProfiles(updated)
            prefs[Keys.HAS_MIGRATED_PROFILES] = true
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.browserDataStore.edit { prefs ->
            val current = readOrMigrateProfiles(prefs)
            val updated = current.filterNot { it.id == profileId }
            prefs[Keys.PROFILES_JSON] = ProfileSerialization.encodeProfiles(updated)
            prefs[Keys.HAS_MIGRATED_PROFILES] = true
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                val nextId = updated.firstOrNull()?.id
                if (nextId != null) {
                    prefs[Keys.ACTIVE_PROFILE_ID] = nextId
                } else {
                    prefs.remove(Keys.ACTIVE_PROFILE_ID)
                }
            }
        }
    }

    suspend fun setLastActiveProfileId(profileId: String?) {
        context.browserDataStore.edit { prefs ->
            if (profileId != null) {
                prefs[Keys.ACTIVE_PROFILE_ID] = profileId
            } else {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
            }
        }
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

    private fun readOrMigrateProfiles(prefs: Preferences): List<BrowserProfile> {
        val hasMigrated = prefs[Keys.HAS_MIGRATED_PROFILES] ?: false
        val storedRaw = prefs[Keys.PROFILES_JSON]
        return if (!hasMigrated && storedRaw == null) {
            listOf(buildDefaultMigratedProfile(legacySettingsFromPrefs(prefs)))
        } else {
            ProfileSerialization.decodeProfiles(storedRaw)
        }
    }

    private fun legacySettingsFromPrefs(prefs: Preferences): BrowserSettings {
        val rawZoom = prefs[Keys.WEBSITE_ZOOM_PERCENT] ?: BrowserConstants.DEFAULT_ZOOM_PERCENT
        return BrowserSettings(
            homepageUrl = prefs[Keys.HOMEPAGE_URL] ?: BrowserConstants.DEFAULT_HOMEPAGE_URL,
            desktopModeEnabled = prefs[Keys.DESKTOP_MODE] ?: true,
            javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            websiteZoomPercent = BrowserConstants.snapZoomPercent(rawZoom.toFloat()),
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    private fun buildDefaultMigratedProfile(legacy: BrowserSettings): BrowserProfile {
        return BrowserProfile(
            id = BrowserConstants.DEFAULT_PROFILE_ID,
            name = BrowserConstants.DEFAULT_PROFILE_DISPLAY_NAME,
            webViewProfileName = BrowserConstants.DEFAULT_WEBVIEW_PROFILE_NAME,
            colorId = ProfileColor.CYAN.id,
            iconId = ProfileIcon.PERSON.id,
            settings = legacy.copy(hasCompletedOnboarding = false),
            tabs = emptyList(),
            activeTabId = null,
            createdAtMillis = 0L
        )
    }
}
