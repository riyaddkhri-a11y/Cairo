package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.browser.TikTokSessionManager
import com.example.browser.WebErrorState
import com.example.browser.WebViewConfig
import com.example.browser.WebViewHostCallbacks
import com.example.browser.WebViewProfileEngine
import com.example.browser.createConfiguredWebView
import com.example.browser.launchExternalUriSafely
import com.example.data.BrowserConstants
import com.example.data.BrowserPreferencesRepository
import com.example.data.BrowserProfile
import com.example.data.BrowserSettings
import com.example.data.SavedTabState
import com.example.data.UrlUtils
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserTabUi(
    val id: String,
    val url: String,
    val title: String,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val errorState: WebErrorState? = null,
    val isChildTab: Boolean = false,
    val parentTabId: String? = null
)

data class BrowserUiState(
    val profiles: List<BrowserProfile> = emptyList(),
    val activeProfileId: String? = null,
    val lastActiveProfileId: String? = null,
    val hasCompletedOnboarding: Boolean = false,
    val isInitialized: Boolean = false,
    val isProfilePickerOpen: Boolean = true,
    val isManageProfilesOpen: Boolean = false,
    val isProfileSwitcherOpen: Boolean = false,
    val isCreateProfileDialogOpen: Boolean = false,
    val editingProfileId: String? = null,
    val tabsByProfile: Map<String, List<BrowserTabUi>> = emptyMap(),
    val activeTabIdByProfile: Map<String, String?> = emptyMap(),
    val tikTokLoginStatusByProfile: Map<String, Boolean> = emptyMap(),
    val isMultiProfileIsolated: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isTabsSheetOpen: Boolean = false,
    val fullscreenView: View? = null
) {
    val activeProfile: BrowserProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }

    val settings: BrowserSettings
        get() = activeProfile?.settings?.copy(hasCompletedOnboarding = hasCompletedOnboarding)
            ?: BrowserSettings(hasCompletedOnboarding = hasCompletedOnboarding)

    val tabs: List<BrowserTabUi>
        get() = activeProfileId?.let { tabsByProfile[it] }.orEmpty()

    val activeTab: BrowserTabUi?
        get() {
            val profileId = activeProfileId ?: return null
            val profileTabs = tabsByProfile[profileId].orEmpty()
            val targetTabId = activeTabIdByProfile[profileId]
            return profileTabs.firstOrNull { it.id == targetTabId } ?: profileTabs.firstOrNull()
        }

    val isTikTokLoggedIn: Boolean
        get() = activeProfileId?.let { tikTokLoginStatusByProfile[it] } ?: false

    val editingProfile: BrowserProfile?
        get() = editingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
}

data class PendingFileChooser(
    val intent: Intent
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BrowserPreferencesRepository(application.applicationContext)
    private val webViewContextWrapper = MutableContextWrapper(application.applicationContext)
    private val profileWebViews = mutableMapOf<String, MutableMap<String, WebView>>()
    private var hasActivityContext = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _fileChooserEvents = MutableSharedFlow<PendingFileChooser>(extraBufferCapacity = 1)
    val fileChooserEvents: SharedFlow<PendingFileChooser> = _fileChooserEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultProfileMigrated()
            val multiProfileSupported = WebViewProfileEngine.isMultiProfileSupported()

            repository.appStateFlow.collect { appState ->
                val previousState = _uiState.value
                val previousProfilesById = previousState.profiles.associateBy { it.id }

                val mergedTabsByProfile = previousState.tabsByProfile.toMutableMap()
                val mergedActiveTabIds = previousState.activeTabIdByProfile.toMutableMap()
                val loginStatuses = previousState.tikTokLoginStatusByProfile.toMutableMap()

                appState.profiles.forEach { profile ->
                    if (!mergedTabsByProfile.containsKey(profile.id) && profile.tabs.isNotEmpty()) {
                        mergedTabsByProfile[profile.id] = profile.tabs.map { saved ->
                            BrowserTabUi(
                                id = saved.id,
                                url = saved.url,
                                title = saved.title,
                                isChildTab = saved.isChildTab,
                                parentTabId = saved.parentTabId
                            )
                        }
                        mergedActiveTabIds[profile.id] = profile.activeTabId
                            ?: profile.tabs.firstOrNull()?.id
                    }
                    if (!loginStatuses.containsKey(profile.id)) {
                        val cm = WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
                        loginStatuses[profile.id] = TikTokSessionManager.isLoggedIn(cm)
                    }
                }

                // Clean up stale entries for deleted profiles
                val validIds = appState.profiles.map { it.id }.toSet()
                mergedTabsByProfile.keys.retainAll(validIds)
                mergedActiveTabIds.keys.retainAll(validIds)
                loginStatuses.keys.retainAll(validIds)

                val resolvedActiveProfileId = previousState.activeProfileId?.takeIf { it in validIds }
                val shouldKeepPickerOpen = if (resolvedActiveProfileId == null) {
                    true
                } else {
                    previousState.isProfilePickerOpen
                }

                _uiState.update {
                    it.copy(
                        profiles = appState.profiles,
                        activeProfileId = resolvedActiveProfileId,
                        lastActiveProfileId = appState.lastActiveProfileId,
                        hasCompletedOnboarding = appState.hasCompletedOnboarding,
                        isInitialized = true,
                        isProfilePickerOpen = shouldKeepPickerOpen,
                        tabsByProfile = mergedTabsByProfile,
                        activeTabIdByProfile = mergedActiveTabIds,
                        tikTokLoginStatusByProfile = loginStatuses,
                        isMultiProfileIsolated = multiProfileSupported
                    )
                }

                appState.profiles.forEach { updatedProfile ->
                    val prevSettings = previousProfilesById[updatedProfile.id]?.settings
                    if (prevSettings != null && prevSettings != updatedProfile.settings) {
                        val modeOrJsChanged =
                            prevSettings.desktopModeEnabled != updatedProfile.settings.desktopModeEnabled ||
                                prevSettings.javaScriptEnabled != updatedProfile.settings.javaScriptEnabled
                        if (modeOrJsChanged) {
                            applySettingsToProfileTabs(
                                profile = updatedProfile,
                                reloadActive = updatedProfile.id == resolvedActiveProfileId
                            )
                        } else if (prevSettings.websiteZoomPercent != updatedProfile.settings.websiteZoomPercent) {
                            applyZoomToProfileTabs(updatedProfile)
                        }
                    }
                }

                if (resolvedActiveProfileId != null && hasActivityContext) {
                    appState.profiles.firstOrNull { it.id == resolvedActiveProfileId }?.let {
                        ensureProfileWebViewsInitialized(it)
                    }
                }
            }
        }
    }

    fun attachActivityContext(context: Context) {
        webViewContextWrapper.baseContext = context
        hasActivityContext = true
        _uiState.value.activeProfile?.let { activeProfile ->
            ensureProfileWebViewsInitialized(activeProfile)
        }
    }

    fun getWebViewForTab(profileId: String?, tabId: String?): WebView? {
        if (profileId == null || tabId == null) return null
        return profileWebViews[profileId]?.get(tabId)
    }

    private fun ensureProfileWebViewsInitialized(profile: BrowserProfile) {
        if (!hasActivityContext) return
        val profileMap = profileWebViews.getOrPut(profile.id) { mutableMapOf() }
        val existingTabs = _uiState.value.tabsByProfile[profile.id].orEmpty()

        if (existingTabs.isEmpty()) {
            val initialTab = createTabInternal(
                profile = profile,
                hostContext = webViewContextWrapper,
                initialUrl = profile.settings.homepageUrl
            )
            _uiState.update { state ->
                state.copy(
                    tabsByProfile = state.tabsByProfile + (profile.id to listOf(initialTab)),
                    activeTabIdByProfile = state.activeTabIdByProfile + (profile.id to initialTab.id)
                )
            }
            persistTabsForProfile(profile.id)
            return
        }

        existingTabs.forEach { tab ->
            if (!profileMap.containsKey(tab.id)) {
                val webView = createConfiguredWebView(
                    context = webViewContextWrapper,
                    profileId = profile.id,
                    webViewProfileName = profile.webViewProfileName,
                    tabId = tab.id,
                    callbacks = hostCallbacks
                )
                profileMap[tab.id] = webView
                if (tab.url.isNotBlank()) {
                    webView.loadUrl(tab.url)
                }
            }
        }
    }

    private val hostCallbacks = object : WebViewHostCallbacks {
        override fun currentSettings(profileId: String): BrowserSettings {
            val profile = _uiState.value.profiles.firstOrNull { it.id == profileId }
            return profile?.settings?.copy(
                hasCompletedOnboarding = _uiState.value.hasCompletedOnboarding
            ) ?: _uiState.value.settings
        }

        override fun onPageStarted(profileId: String, tabId: String, url: String) {
            updateTab(profileId, tabId) { tab ->
                tab.copy(
                    url = url,
                    isLoading = true,
                    progress = 10,
                    errorState = null
                )
            }
            persistTabsForProfile(profileId)
        }

        override fun onPageFinished(
            profileId: String,
            tabId: String,
            url: String,
            title: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            updateTab(profileId, tabId) { tab ->
                tab.copy(
                    url = url.ifBlank { tab.url },
                    title = title.ifBlank { tab.title },
                    isLoading = false,
                    progress = 100,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }
            persistTabsForProfile(profileId)
        }

        override fun onProgressChanged(profileId: String, tabId: String, progress: Int) {
            updateTab(profileId, tabId) { tab ->
                val webView = profileWebViews[profileId]?.get(tabId)
                tab.copy(
                    progress = progress,
                    isLoading = progress in 1..99,
                    canGoBack = webView?.canGoBack() ?: tab.canGoBack,
                    canGoForward = webView?.canGoForward() ?: tab.canGoForward
                )
            }
        }

        override fun onTitleReceived(profileId: String, tabId: String, title: String) {
            updateTab(profileId, tabId) { tab -> tab.copy(title = title) }
            persistTabsForProfile(profileId)
        }

        override fun onMainFrameError(profileId: String, tabId: String, error: WebErrorState) {
            updateTab(profileId, tabId) { tab ->
                tab.copy(
                    isLoading = false,
                    progress = 0,
                    errorState = error
                )
            }
        }

        override fun onShowFileChooser(
            profileId: String,
            callback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams?
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback
            val chooserIntent = WebViewConfig.buildFileChooserIntent(params)
            val emitted = _fileChooserEvents.tryEmit(PendingFileChooser(chooserIntent))
            if (!emitted) {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                return false
            }
            return true
        }

        override fun onCreateChildWindow(profileId: String, resultMsg: Message?): Boolean {
            if (!hasActivityContext) return false
            val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val parentId = _uiState.value.activeTabIdByProfile[profileId]
            val childTab = createTabInternal(
                profile = profile,
                hostContext = webViewContextWrapper,
                initialUrl = "",
                isChildTab = true,
                parentTabId = parentId
            )
            transport.webView = profileWebViews[profileId]?.get(childTab.id)
            resultMsg.sendToTarget()
            _uiState.update { state ->
                val currentTabs = state.tabsByProfile[profileId].orEmpty()
                state.copy(
                    tabsByProfile = state.tabsByProfile + (profileId to (currentTabs + childTab)),
                    activeTabIdByProfile = state.activeTabIdByProfile + (profileId to childTab.id)
                )
            }
            persistTabsForProfile(profileId)
            return true
        }

        override fun onCloseWindow(profileId: String, tabId: String) {
            closeTab(tabId, profileId)
        }

        override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
            if (view == null) {
                callback?.onCustomViewHidden()
                return
            }
            customViewCallback?.onCustomViewHidden()
            customViewCallback = callback
            _uiState.update { it.copy(fullscreenView = view) }
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }

        override fun onDownloadRequested(
            profileId: String,
            url: String,
            userAgent: String,
            contentDisposition: String,
            mimeType: String
        ) {
            val app = getApplication<Application>()
            val profile = _uiState.value.profiles.firstOrNull { it.id == profileId }
            val cookieManager = if (profile != null) {
                WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
            } else {
                android.webkit.CookieManager.getInstance()
            }
            val result = WebViewConfig.enqueueDownload(
                context = app,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                cookieManager = cookieManager
            )
            result.onSuccess { fileName ->
                _snackbarMessages.tryEmit(app.getString(R.string.download_started, fileName))
            }.onFailure { err ->
                _snackbarMessages.tryEmit(
                    app.getString(
                        R.string.download_failed,
                        err.localizedMessage ?: app.getString(R.string.download_unsupported)
                    )
                )
            }
        }

        override fun onOpenExternalUri(uri: Uri) {
            launchExternalUriSafely(webViewContextWrapper, uri)
        }

        override fun onRendererProcessGone(profileId: String, tabId: String, didCrash: Boolean) {
            val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
            val profileMap = profileWebViews[profileId] ?: return
            val oldView = profileMap.remove(tabId)
            if (oldView != null) {
                (oldView.parent as? ViewGroup)?.removeView(oldView)
                oldView.destroy()
            }
            val currentTab = _uiState.value.tabsByProfile[profileId]
                ?.firstOrNull { it.id == tabId } ?: return
            val recoveredView = createConfiguredWebView(
                context = webViewContextWrapper,
                profileId = profile.id,
                webViewProfileName = profile.webViewProfileName,
                tabId = tabId,
                callbacks = this,
                forceSoftwareLayer = didCrash
            )
            profileMap[tabId] = recoveredView
            if (currentTab.url.isNotBlank()) {
                recoveredView.loadUrl(currentTab.url)
            }
        }

        override fun onCookiesMaybeUpdated(profileId: String, tabId: String, url: String?) {
            refreshTikTokLoginStatus(profileId)
        }
    }

    private fun createTabInternal(
        profile: BrowserProfile,
        hostContext: Context,
        initialUrl: String,
        isChildTab: Boolean = false,
        parentTabId: String? = null
    ): BrowserTabUi {
        val tabId = UUID.randomUUID().toString()
        val webView = createConfiguredWebView(
            context = hostContext,
            profileId = profile.id,
            webViewProfileName = profile.webViewProfileName,
            tabId = tabId,
            callbacks = hostCallbacks
        )
        profileWebViews.getOrPut(profile.id) { mutableMapOf() }[tabId] = webView
        if (initialUrl.isNotBlank()) {
            webView.loadUrl(initialUrl)
        }
        return BrowserTabUi(
            id = tabId,
            url = initialUrl,
            title = hostContext.getString(R.string.tabs_untitled),
            isLoading = initialUrl.isNotBlank(),
            isChildTab = isChildTab,
            parentTabId = parentTabId
        )
    }

    // --- Profile Management Actions ---

    fun selectProfile(profileId: String) {
        val previousProfile = _uiState.value.activeProfile
        if (previousProfile != null && previousProfile.id != profileId) {
            WebViewProfileEngine.flushCookiesForProfile(previousProfile.webViewProfileName)
            persistTabsForProfile(previousProfile.id)
        }

        val targetProfile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        _uiState.update {
            it.copy(
                activeProfileId = profileId,
                isProfilePickerOpen = false,
                isManageProfilesOpen = false,
                isProfileSwitcherOpen = false,
                isSettingsOpen = false,
                isTabsSheetOpen = false
            )
        }
        refreshTikTokLoginStatus(profileId)
        ensureProfileWebViewsInitialized(targetProfile)
        viewModelScope.launch {
            repository.setLastActiveProfileId(profileId)
        }
    }

    fun openProfilePicker() {
        flushAllProfileCookies()
        refreshAllTikTokLoginStatuses()
        _uiState.update {
            it.copy(
                isProfilePickerOpen = true,
                isProfileSwitcherOpen = false,
                isManageProfilesOpen = false,
                isSettingsOpen = false,
                isTabsSheetOpen = false
            )
        }
    }

    fun openProfileSwitcher() {
        refreshAllTikTokLoginStatuses()
        _uiState.update { it.copy(isProfileSwitcherOpen = true) }
    }

    fun closeProfileSwitcher() {
        _uiState.update { it.copy(isProfileSwitcherOpen = false) }
    }

    fun openManageProfiles() {
        refreshAllTikTokLoginStatuses()
        _uiState.update {
            it.copy(
                isManageProfilesOpen = true,
                isProfileSwitcherOpen = false
            )
        }
    }

    fun closeManageProfiles() {
        _uiState.update { it.copy(isManageProfilesOpen = false) }
    }

    fun openCreateProfileDialog() {
        _uiState.update {
            it.copy(
                isCreateProfileDialogOpen = true,
                editingProfileId = null,
                isProfileSwitcherOpen = false
            )
        }
    }

    fun openEditProfileDialog(profileId: String) {
        _uiState.update {
            it.copy(
                isCreateProfileDialogOpen = true,
                editingProfileId = profileId
            )
        }
    }

    fun closeCreateOrEditProfileDialog() {
        _uiState.update {
            it.copy(
                isCreateProfileDialogOpen = false,
                editingProfileId = null
            )
        }
    }

    fun saveProfileFromDialog(name: String, colorId: String, iconId: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val editingId = _uiState.value.editingProfileId

        viewModelScope.launch {
            if (editingId != null) {
                repository.updateProfileAppearance(
                    profileId = editingId,
                    name = cleanName,
                    colorId = colorId,
                    iconId = iconId
                )
                _uiState.update {
                    it.copy(
                        isCreateProfileDialogOpen = false,
                        editingProfileId = null
                    )
                }
            } else {
                val created = repository.createProfile(
                    name = cleanName,
                    colorId = colorId,
                    iconId = iconId
                )
                _uiState.update { state ->
                    state.copy(
                        profiles = if (state.profiles.any { it.id == created.id }) {
                            state.profiles
                        } else {
                            state.profiles + created
                        },
                        isCreateProfileDialogOpen = false,
                        editingProfileId = null,
                        isProfilePickerOpen = false,
                        isManageProfilesOpen = false,
                        isProfileSwitcherOpen = false,
                        isSettingsOpen = false,
                        activeProfileId = created.id,
                        tikTokLoginStatusByProfile = state.tikTokLoginStatusByProfile + (created.id to false)
                    )
                }
                ensureProfileWebViewsInitialized(created)
            }
        }
    }

    fun deleteProfile(profileId: String) {
        val targetProfile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        val wasActive = _uiState.value.activeProfileId == profileId

        profileWebViews.remove(profileId)?.values?.forEach { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        WebViewProfileEngine.deleteProfileStorage(targetProfile.webViewProfileName)

        viewModelScope.launch {
            repository.deleteProfile(profileId)
            _uiState.update { state ->
                state.copy(
                    activeProfileId = if (wasActive) null else state.activeProfileId,
                    isProfilePickerOpen = if (wasActive) true else state.isProfilePickerOpen,
                    isSettingsOpen = if (wasActive) false else state.isSettingsOpen,
                    isTabsSheetOpen = if (wasActive) false else state.isTabsSheetOpen,
                    tabsByProfile = state.tabsByProfile - profileId,
                    activeTabIdByProfile = state.activeTabIdByProfile - profileId,
                    tikTokLoginStatusByProfile = state.tikTokLoginStatusByProfile - profileId
                )
            }
            val app = getApplication<Application>()
            _snackbarMessages.tryEmit(app.getString(R.string.profile_deleted_message, targetProfile.name))
        }
    }

    // --- Tab & Navigation Actions (Scoped to Active Profile) ---

    fun openNewTab(
        hostContext: Context? = webViewContextWrapper,
        initialUrl: String = _uiState.value.settings.homepageUrl,
        switchToTab: Boolean = true
    ) {
        val profile = _uiState.value.activeProfile ?: return
        val ctx = hostContext ?: webViewContextWrapper
        val newTab = createTabInternal(profile = profile, hostContext = ctx, initialUrl = initialUrl)
        _uiState.update { state ->
            val currentTabs = state.tabsByProfile[profile.id].orEmpty()
            val nextActiveTab = if (switchToTab) newTab.id else state.activeTabIdByProfile[profile.id]
            state.copy(
                tabsByProfile = state.tabsByProfile + (profile.id to (currentTabs + newTab)),
                activeTabIdByProfile = state.activeTabIdByProfile + (profile.id to nextActiveTab),
                isTabsSheetOpen = false
            )
        }
        persistTabsForProfile(profile.id)
    }

    fun selectTab(tabId: String) {
        val profileId = _uiState.value.activeProfileId ?: return
        _uiState.update {
            it.copy(
                activeTabIdByProfile = it.activeTabIdByProfile + (profileId to tabId),
                isTabsSheetOpen = false
            )
        }
        persistTabsForProfile(profileId)
    }

    fun closeTab(tabId: String, targetProfileId: String? = _uiState.value.activeProfileId) {
        val profileId = targetProfileId ?: return
        val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        val currentTabs = _uiState.value.tabsByProfile[profileId].orEmpty()
        val closingTab = currentTabs.firstOrNull { it.id == tabId } ?: return
        val remainingTabs = currentTabs.filterNot { it.id == tabId }

        profileWebViews[profileId]?.remove(tabId)?.apply {
            (parent as? ViewGroup)?.removeView(this)
            stopLoading()
            destroy()
        }

        if (remainingTabs.isEmpty()) {
            if (hasActivityContext) {
                val freshTab = createTabInternal(
                    profile = profile,
                    hostContext = webViewContextWrapper,
                    initialUrl = profile.settings.homepageUrl
                )
                _uiState.update {
                    it.copy(
                        tabsByProfile = it.tabsByProfile + (profileId to listOf(freshTab)),
                        activeTabIdByProfile = it.activeTabIdByProfile + (profileId to freshTab.id)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        tabsByProfile = it.tabsByProfile + (profileId to emptyList()),
                        activeTabIdByProfile = it.activeTabIdByProfile + (profileId to null)
                    )
                }
            }
            persistTabsForProfile(profileId)
            return
        }

        val currentActiveId = _uiState.value.activeTabIdByProfile[profileId]
        val nextActiveId = if (currentActiveId == tabId) {
            val parentId = closingTab.parentTabId
            if (parentId != null && remainingTabs.any { it.id == parentId }) {
                parentId
            } else {
                remainingTabs.last().id
            }
        } else {
            currentActiveId
        }

        _uiState.update {
            it.copy(
                tabsByProfile = it.tabsByProfile + (profileId to remainingTabs),
                activeTabIdByProfile = it.activeTabIdByProfile + (profileId to nextActiveId)
            )
        }
        persistTabsForProfile(profileId)
    }

    fun loadUrlInActiveTab(rawInput: String) {
        val profileId = _uiState.value.activeProfileId ?: return
        val activeTab = _uiState.value.activeTab ?: return
        val targetUrl = UrlUtils.normalizeAddressBarInput(rawInput)
        val webView = profileWebViews[profileId]?.get(activeTab.id) ?: return
        updateTab(profileId, activeTab.id) {
            it.copy(errorState = null, isLoading = true, url = targetUrl)
        }
        webView.loadUrl(targetUrl)
        persistTabsForProfile(profileId)
    }

    fun goBack(): Boolean {
        val profileId = _uiState.value.activeProfileId ?: return false
        val activeTab = _uiState.value.activeTab ?: return false
        val webView = profileWebViews[profileId]?.get(activeTab.id)
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        if (activeTab.isChildTab && _uiState.value.tabs.size > 1) {
            closeTab(activeTab.id, profileId)
            return true
        }
        return false
    }

    fun goForward() {
        val profileId = _uiState.value.activeProfileId ?: return
        val activeTab = _uiState.value.activeTab ?: return
        val webView = profileWebViews[profileId]?.get(activeTab.id) ?: return
        if (webView.canGoForward()) {
            webView.goForward()
        }
    }

    fun reloadOrStop() {
        val profileId = _uiState.value.activeProfileId ?: return
        val activeTab = _uiState.value.activeTab ?: return
        val webView = profileWebViews[profileId]?.get(activeTab.id) ?: return
        if (activeTab.isLoading) {
            webView.stopLoading()
            updateTab(profileId, activeTab.id) { it.copy(isLoading = false, progress = 0) }
        } else {
            updateTab(profileId, activeTab.id) { it.copy(errorState = null) }
            webView.reload()
        }
    }

    fun retryActiveTab() {
        val profileId = _uiState.value.activeProfileId ?: return
        val activeTab = _uiState.value.activeTab ?: return
        val webView = profileWebViews[profileId]?.get(activeTab.id) ?: return
        val failingUrl = activeTab.errorState?.failingUrl.orEmpty()
        updateTab(profileId, activeTab.id) { it.copy(errorState = null, isLoading = true) }
        if (failingUrl.isNotBlank()) {
            webView.loadUrl(failingUrl)
        } else {
            webView.reload()
        }
    }

    fun openHomepage() {
        val profile = _uiState.value.activeProfile ?: return
        val homepage = profile.settings.homepageUrl
        val activeTab = _uiState.value.activeTab
        if (activeTab == null) {
            openNewTab(initialUrl = homepage, switchToTab = true)
            return
        }
        val webView = profileWebViews[profile.id]?.get(activeTab.id) ?: return
        updateTab(profile.id, activeTab.id) {
            it.copy(errorState = null, isLoading = true, url = homepage)
        }
        webView.loadUrl(homepage)
        persistTabsForProfile(profile.id)
    }

    // --- Settings Actions (Scoped to Active Profile) ---

    fun openSettings() {
        _uiState.value.activeProfileId?.let { refreshTikTokLoginStatus(it) }
        _uiState.update { it.copy(isSettingsOpen = true, isTabsSheetOpen = false) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun openTikTokLogin() {
        _uiState.update { it.copy(isSettingsOpen = false, isTabsSheetOpen = false) }
        val activeTab = _uiState.value.activeTab
        val profileId = _uiState.value.activeProfileId ?: return
        if (activeTab == null || profileWebViews[profileId]?.get(activeTab.id) == null) {
            openNewTab(initialUrl = BrowserConstants.TIKTOK_LOGIN_URL, switchToTab = true)
        } else {
            loadUrlInActiveTab(BrowserConstants.TIKTOK_LOGIN_URL)
        }
    }

    fun logoutTikTok() {
        val profile = _uiState.value.activeProfile ?: return
        val profileCookieManager = WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
        val profileWebStorage = WebViewProfileEngine.webStorageForProfile(profile.webViewProfileName)
        TikTokSessionManager.clearTikTokSession(profileCookieManager)
        runCatching {
            profileWebStorage.deleteOrigin("https://www.tiktok.com")
            profileWebStorage.deleteOrigin("https://tiktok.com")
        }
        _uiState.update { state ->
            state.copy(
                tikTokLoginStatusByProfile = state.tikTokLoginStatusByProfile + (profile.id to false)
            )
        }
        val activeTab = _uiState.value.activeTab
        if (activeTab != null && TikTokSessionManager.isTikTokUrl(activeTab.url)) {
            profileWebViews[profile.id]?.get(activeTab.id)?.reload()
        }
        val app = getApplication<Application>()
        _snackbarMessages.tryEmit(app.getString(R.string.settings_tiktok_logout_success))
    }

    fun refreshTikTokLoginStatus(profileId: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        val cm = WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
        val loggedIn = TikTokSessionManager.isLoggedIn(cm)
        if (_uiState.value.tikTokLoginStatusByProfile[profileId] != loggedIn) {
            _uiState.update { state ->
                state.copy(
                    tikTokLoginStatusByProfile = state.tikTokLoginStatusByProfile + (profileId to loggedIn)
                )
            }
        }
    }

    private fun refreshAllTikTokLoginStatuses() {
        val updated = _uiState.value.profiles.associate { profile ->
            val cm = WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
            profile.id to TikTokSessionManager.isLoggedIn(cm)
        }
        _uiState.update { it.copy(tikTokLoginStatusByProfile = updated) }
    }

    fun openTabsSheet() {
        _uiState.update { it.copy(isTabsSheetOpen = true) }
    }

    fun closeTabsSheet() {
        _uiState.update { it.copy(isTabsSheetOpen = false) }
    }

    fun saveHomepage(rawUrl: String): Boolean {
        val profileId = _uiState.value.activeProfileId ?: return false
        val normalized = UrlUtils.normalizeHomepageUrl(rawUrl)
        val app = getApplication<Application>()
        if (normalized == null) {
            _snackbarMessages.tryEmit(app.getString(R.string.settings_homepage_invalid))
            return false
        }
        viewModelScope.launch {
            repository.updateProfileSettings(profileId) { it.copy(homepageUrl = normalized) }
            _snackbarMessages.tryEmit(app.getString(R.string.settings_homepage_saved))
        }
        return true
    }

    fun resetHomepageToDefault(): String {
        val defaultUrl = BrowserConstants.DEFAULT_HOMEPAGE_URL
        val profileId = _uiState.value.activeProfileId ?: return defaultUrl
        viewModelScope.launch {
            repository.updateProfileSettings(profileId) { it.copy(homepageUrl = defaultUrl) }
            val app = getApplication<Application>()
            _snackbarMessages.tryEmit(app.getString(R.string.settings_homepage_saved))
        }
        return defaultUrl
    }

    fun setDesktopMode(enabled: Boolean) {
        val profileId = _uiState.value.activeProfileId ?: return
        viewModelScope.launch {
            repository.updateProfileSettings(profileId) { it.copy(desktopModeEnabled = enabled) }
        }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        val profileId = _uiState.value.activeProfileId ?: return
        viewModelScope.launch {
            repository.updateProfileSettings(profileId) { it.copy(javaScriptEnabled = enabled) }
        }
    }

    fun setWebsiteZoomPercent(rawPercent: Float) {
        val profile = _uiState.value.activeProfile ?: return
        val snapped = BrowserConstants.snapZoomPercent(rawPercent)
        if (snapped == profile.settings.websiteZoomPercent) return
        val updatedSettings = profile.settings.copy(websiteZoomPercent = snapped)
        val updatedProfile = profile.copy(settings = updatedSettings)
        _uiState.update { state ->
            state.copy(
                profiles = state.profiles.map {
                    if (it.id == profile.id) updatedProfile else it
                }
            )
        }
        applyZoomToProfileTabs(updatedProfile)
        viewModelScope.launch {
            repository.updateProfileSettings(profile.id) { it.copy(websiteZoomPercent = snapped) }
        }
    }

    fun resetWebsiteZoom() {
        setWebsiteZoomPercent(BrowserConstants.DEFAULT_ZOOM_PERCENT.toFloat())
    }

    fun completeOnboarding() {
        if (_uiState.value.hasCompletedOnboarding) return
        _uiState.update { it.copy(hasCompletedOnboarding = true) }
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
        }
    }

    fun clearBrowsingData() {
        val profile = _uiState.value.activeProfile ?: return
        val views = profileWebViews[profile.id]?.values?.toList().orEmpty()
        val cm = WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
        val ws = WebViewProfileEngine.webStorageForProfile(profile.webViewProfileName)
        WebViewConfig.clearAllBrowsingData(views, cm, ws)
        _uiState.update { state ->
            val clearedTabs = state.tabsByProfile[profile.id].orEmpty()
                .map { it.copy(canGoBack = false, canGoForward = false) }
            state.copy(
                tabsByProfile = state.tabsByProfile + (profile.id to clearedTabs),
                tikTokLoginStatusByProfile = state.tikTokLoginStatusByProfile + (profile.id to false)
            )
        }
        val app = getApplication<Application>()
        _snackbarMessages.tryEmit(app.getString(R.string.settings_data_cleared))
    }

    fun onFileChooserResult(uris: Array<Uri>?) {
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    fun notifyFilePickerUnavailable() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        val app = getApplication<Application>()
        _snackbarMessages.tryEmit(app.getString(R.string.file_picker_unavailable))
    }

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        _uiState.update { it.copy(fullscreenView = null) }
    }

    fun flushAllProfileCookies() {
        val names = _uiState.value.profiles.map { it.webViewProfileName }
        WebViewProfileEngine.flushCookiesForProfiles(names)
    }

    private fun applySettingsToProfileTabs(profile: BrowserProfile, reloadActive: Boolean) {
        val activeTabId = _uiState.value.activeTabIdByProfile[profile.id]
        val profileCm = WebViewProfileEngine.cookieManagerForProfile(profile.webViewProfileName)
        profileWebViews[profile.id]?.forEach { (tabId, webView) ->
            WebViewConfig.applySettings(webView, profile.settings, profileCm)
            if (reloadActive && tabId == activeTabId) {
                webView.reload()
            } else {
                WebViewConfig.applyWebsiteZoom(webView, profile.settings)
            }
        }
    }

    private fun applyZoomToProfileTabs(profile: BrowserProfile) {
        profileWebViews[profile.id]?.values?.forEach { webView ->
            WebViewConfig.applyWebsiteZoom(webView, profile.settings)
        }
    }

    private fun updateTab(
        profileId: String,
        tabId: String,
        transform: (BrowserTabUi) -> BrowserTabUi
    ) {
        _uiState.update { state ->
            val currentTabs = state.tabsByProfile[profileId] ?: return@update state
            val updatedTabs = currentTabs.map { tab ->
                if (tab.id == tabId) transform(tab) else tab
            }
            state.copy(tabsByProfile = state.tabsByProfile + (profileId to updatedTabs))
        }
    }

    private fun persistTabsForProfile(profileId: String) {
        val state = _uiState.value
        val currentTabs = state.tabsByProfile[profileId].orEmpty()
        val savedTabs = currentTabs.map { tab ->
            SavedTabState(
                id = tab.id,
                url = tab.url,
                title = tab.title,
                isChildTab = tab.isChildTab,
                parentTabId = tab.parentTabId
            )
        }
        val activeTabId = state.activeTabIdByProfile[profileId]
        viewModelScope.launch {
            repository.saveProfileTabs(profileId, savedTabs, activeTabId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        flushAllProfileCookies()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        profileWebViews.values.forEach { tabMap ->
            tabMap.values.forEach { webView ->
                webView.stopLoading()
                webView.destroy()
            }
        }
        profileWebViews.clear()
    }
}
