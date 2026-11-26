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
import com.example.browser.WebErrorState
import com.example.browser.WebViewConfig
import com.example.browser.WebViewHostCallbacks
import com.example.browser.createConfiguredWebView
import com.example.browser.launchExternalUriSafely
import com.example.data.BrowserConstants
import com.example.data.BrowserPreferencesRepository
import com.example.data.BrowserSettings
import com.example.data.UrlUtils
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val settings: BrowserSettings = BrowserSettings(),
    val isInitialized: Boolean = false,
    val tabs: List<BrowserTabUi> = emptyList(),
    val activeTabId: String? = null,
    val isSettingsOpen: Boolean = false,
    val isTabsSheetOpen: Boolean = false,
    val fullscreenView: View? = null
) {
    val activeTab: BrowserTabUi?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()
}

data class PendingFileChooser(
    val intent: Intent
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BrowserPreferencesRepository(application.applicationContext)
    private val webViewContextWrapper = MutableContextWrapper(application.applicationContext)
    private val webViews = mutableMapOf<String, WebView>()
    private var hasActivityContext = false
    private var initialTabCreated = false

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
            val initialSettings = repository.settingsFlow.first()
            _uiState.update { it.copy(settings = initialSettings, isInitialized = true) }
            maybeOpenInitialTab()

            repository.settingsFlow.collect { updated ->
                val previous = _uiState.value.settings
                _uiState.update { it.copy(settings = updated) }
                val modeOrJsChanged = previous.desktopModeEnabled != updated.desktopModeEnabled ||
                    previous.javaScriptEnabled != updated.javaScriptEnabled
                if (modeOrJsChanged) {
                    applySettingsToAllTabs(
                        settings = updated,
                        reloadActive = true
                    )
                } else if (previous.websiteZoomPercent != updated.websiteZoomPercent) {
                    applyZoomToAllTabs(updated)
                }
            }
        }
    }

    fun attachActivityContext(context: Context) {
        webViewContextWrapper.baseContext = context
        hasActivityContext = true
        maybeOpenInitialTab()
    }

    private fun maybeOpenInitialTab() {
        if (!hasActivityContext || initialTabCreated || !_uiState.value.isInitialized) return
        initialTabCreated = true
        val homepage = _uiState.value.settings.homepageUrl
        openNewTab(hostContext = webViewContextWrapper, initialUrl = homepage, switchToTab = true)
    }

    fun getWebViewForTab(tabId: String?): WebView? {
        if (tabId == null) return null
        return webViews[tabId]
    }

    private val hostCallbacks = object : WebViewHostCallbacks {
        override fun currentSettings(): BrowserSettings = _uiState.value.settings

        override fun onPageStarted(tabId: String, url: String) {
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url,
                    isLoading = true,
                    progress = 10,
                    errorState = null
                )
            }
        }

        override fun onPageFinished(
            tabId: String,
            url: String,
            title: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url.ifBlank { tab.url },
                    title = title.ifBlank { tab.title },
                    isLoading = false,
                    progress = 100,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }
        }

        override fun onProgressChanged(tabId: String, progress: Int) {
            updateTab(tabId) { tab ->
                val webView = webViews[tabId]
                tab.copy(
                    progress = progress,
                    isLoading = progress in 1..99,
                    canGoBack = webView?.canGoBack() ?: tab.canGoBack,
                    canGoForward = webView?.canGoForward() ?: tab.canGoForward
                )
            }
        }

        override fun onTitleReceived(tabId: String, title: String) {
            updateTab(tabId) { tab -> tab.copy(title = title) }
        }

        override fun onMainFrameError(tabId: String, error: WebErrorState) {
            updateTab(tabId) { tab ->
                tab.copy(
                    isLoading = false,
                    progress = 0,
                    errorState = error
                )
            }
        }

        override fun onShowFileChooser(
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

        override fun onCreateChildWindow(resultMsg: Message?): Boolean {
            if (!hasActivityContext) return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val parentId = _uiState.value.activeTabId
            val childTab = createTabInternal(
                hostContext = webViewContextWrapper,
                initialUrl = "",
                isChildTab = true,
                parentTabId = parentId
            )
            transport.webView = webViews[childTab.id]
            resultMsg.sendToTarget()
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs + childTab,
                    activeTabId = childTab.id
                )
            }
            return true
        }

        override fun onCloseWindow(tabId: String) {
            closeTab(tabId)
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
            url: String,
            userAgent: String,
            contentDisposition: String,
            mimeType: String
        ) {
            val app = getApplication<Application>()
            val result = WebViewConfig.enqueueDownload(
                context = app,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType
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

        override fun onRendererProcessGone(tabId: String, didCrash: Boolean) {
            val oldView = webViews.remove(tabId)
            if (oldView != null) {
                (oldView.parent as? ViewGroup)?.removeView(oldView)
                oldView.destroy()
            }
            val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            val recoveredView = createConfiguredWebView(
                context = webViewContextWrapper,
                tabId = tabId,
                callbacks = this,
                forceSoftwareLayer = didCrash
            )
            webViews[tabId] = recoveredView
            if (currentTab.url.isNotBlank()) {
                recoveredView.loadUrl(currentTab.url)
            }
        }
    }

    private fun createTabInternal(
        hostContext: Context,
        initialUrl: String,
        isChildTab: Boolean = false,
        parentTabId: String? = null
    ): BrowserTabUi {
        val tabId = UUID.randomUUID().toString()
        val webView = createConfiguredWebView(
            context = hostContext,
            tabId = tabId,
            callbacks = hostCallbacks
        )
        webViews[tabId] = webView
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

    fun openNewTab(
        hostContext: Context? = webViewContextWrapper,
        initialUrl: String = _uiState.value.settings.homepageUrl,
        switchToTab: Boolean = true
    ) {
        val ctx = hostContext ?: webViewContextWrapper
        val newTab = createTabInternal(hostContext = ctx, initialUrl = initialUrl)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = if (switchToTab) newTab.id else state.activeTabId,
                isTabsSheetOpen = false
            )
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { it.copy(activeTabId = tabId, isTabsSheetOpen = false) }
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        val closingTab = currentTabs.firstOrNull { it.id == tabId } ?: return
        val remainingTabs = currentTabs.filterNot { it.id == tabId }

        webViews.remove(tabId)?.apply {
            stopLoading()
            destroy()
        }

        if (remainingTabs.isEmpty()) {
            if (hasActivityContext) {
                val freshTab = createTabInternal(
                    hostContext = webViewContextWrapper,
                    initialUrl = _uiState.value.settings.homepageUrl
                )
                _uiState.update {
                    it.copy(
                        tabs = listOf(freshTab),
                        activeTabId = freshTab.id
                    )
                }
            } else {
                _uiState.update { it.copy(tabs = emptyList(), activeTabId = null) }
            }
            return
        }

        val nextActiveId = if (_uiState.value.activeTabId == tabId) {
            val parentId = closingTab.parentTabId
            if (parentId != null && remainingTabs.any { it.id == parentId }) {
                parentId
            } else {
                remainingTabs.last().id
            }
        } else {
            _uiState.value.activeTabId
        }

        _uiState.update {
            it.copy(
                tabs = remainingTabs,
                activeTabId = nextActiveId
            )
        }
    }

    fun loadUrlInActiveTab(rawInput: String) {
        val targetUrl = UrlUtils.normalizeAddressBarInput(rawInput)
        val activeTab = _uiState.value.activeTab ?: return
        val webView = webViews[activeTab.id] ?: return
        updateTab(activeTab.id) { it.copy(errorState = null, isLoading = true, url = targetUrl) }
        webView.loadUrl(targetUrl)
    }

    fun goBack(): Boolean {
        val activeTab = _uiState.value.activeTab ?: return false
        val webView = webViews[activeTab.id]
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        if (activeTab.isChildTab && _uiState.value.tabs.size > 1) {
            closeTab(activeTab.id)
            return true
        }
        return false
    }

    fun goForward() {
        val activeTab = _uiState.value.activeTab ?: return
        val webView = webViews[activeTab.id] ?: return
        if (webView.canGoForward()) {
            webView.goForward()
        }
    }

    fun reloadOrStop() {
        val activeTab = _uiState.value.activeTab ?: return
        val webView = webViews[activeTab.id] ?: return
        if (activeTab.isLoading) {
            webView.stopLoading()
            updateTab(activeTab.id) { it.copy(isLoading = false, progress = 0) }
        } else {
            updateTab(activeTab.id) { it.copy(errorState = null) }
            webView.reload()
        }
    }

    fun retryActiveTab() {
        val activeTab = _uiState.value.activeTab ?: return
        val webView = webViews[activeTab.id] ?: return
        val failingUrl = activeTab.errorState?.failingUrl.orEmpty()
        updateTab(activeTab.id) { it.copy(errorState = null, isLoading = true) }
        if (failingUrl.isNotBlank()) {
            webView.loadUrl(failingUrl)
        } else {
            webView.reload()
        }
    }

    fun openHomepage() {
        val homepage = _uiState.value.settings.homepageUrl
        val activeTab = _uiState.value.activeTab
        if (activeTab == null) {
            openNewTab(initialUrl = homepage, switchToTab = true)
            return
        }
        val webView = webViews[activeTab.id] ?: return
        updateTab(activeTab.id) { it.copy(errorState = null, isLoading = true, url = homepage) }
        webView.loadUrl(homepage)
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true, isTabsSheetOpen = false) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun openTabsSheet() {
        _uiState.update { it.copy(isTabsSheetOpen = true) }
    }

    fun closeTabsSheet() {
        _uiState.update { it.copy(isTabsSheetOpen = false) }
    }

    fun saveHomepage(rawUrl: String): Boolean {
        val normalized = UrlUtils.normalizeHomepageUrl(rawUrl)
        val app = getApplication<Application>()
        if (normalized == null) {
            _snackbarMessages.tryEmit(app.getString(R.string.settings_homepage_invalid))
            return false
        }
        viewModelScope.launch {
            repository.saveHomepageUrl(normalized)
            _snackbarMessages.tryEmit(app.getString(R.string.settings_homepage_saved))
        }
        return true
    }

    fun resetHomepageToDefault(): String {
        val defaultUrl = BrowserConstants.DEFAULT_HOMEPAGE_URL
        viewModelScope.launch {
            repository.saveHomepageUrl(defaultUrl)
            val app = getApplication<Application>()
            _snackbarMessages.tryEmit(app.getString(R.string.settings_homepage_saved))
        }
        return defaultUrl
    }

    fun setDesktopMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDesktopMode(enabled)
        }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setJavaScriptEnabled(enabled)
        }
    }

    fun setWebsiteZoomPercent(rawPercent: Float) {
        val snapped = BrowserConstants.snapZoomPercent(rawPercent)
        val current = _uiState.value.settings
        if (snapped == current.websiteZoomPercent) return
        val updated = current.copy(websiteZoomPercent = snapped)
        _uiState.update { it.copy(settings = updated) }
        applyZoomToAllTabs(updated)
        viewModelScope.launch {
            repository.setWebsiteZoomPercent(snapped)
        }
    }

    fun resetWebsiteZoom() {
        setWebsiteZoomPercent(BrowserConstants.DEFAULT_ZOOM_PERCENT.toFloat())
    }

    fun clearBrowsingData() {
        WebViewConfig.clearAllBrowsingData(webViews.values.toList())
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { it.copy(canGoBack = false, canGoForward = false) }
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

    private fun applySettingsToAllTabs(settings: BrowserSettings, reloadActive: Boolean) {
        val activeId = _uiState.value.activeTabId
        webViews.forEach { (id, webView) ->
            WebViewConfig.applySettings(webView, settings)
            if (reloadActive && id == activeId) {
                webView.reload()
            } else {
                WebViewConfig.applyWebsiteZoom(webView, settings)
            }
        }
    }

    private fun applyZoomToAllTabs(settings: BrowserSettings) {
        webViews.values.forEach { webView ->
            WebViewConfig.applyWebsiteZoom(webView, settings)
        }
    }

    private fun updateTab(tabId: String, transform: (BrowserTabUi) -> BrowserTabUi) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) transform(tab) else tab
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        webViews.values.forEach { webView ->
            webView.stopLoading()
            webView.destroy()
        }
        webViews.clear()
    }
}
