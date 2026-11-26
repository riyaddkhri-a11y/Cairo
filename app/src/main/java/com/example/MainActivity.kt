package com.example

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BrowserConstants
import com.example.ui.BrowserUiState
import com.example.ui.BrowserViewModel
import com.example.ui.components.BrowserBottomBar
import com.example.ui.components.BrowserToolbar
import com.example.ui.components.NetworkErrorOverlay
import com.example.ui.components.TabSwitcherSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.DesktopBrowserTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        viewModel.attachActivityContext(this)

        setContent {
            DesktopBrowserTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DesktopBrowserApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
    }
}

@Composable
fun DesktopBrowserApp(viewModel: BrowserViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.onFileChooserResult(null)
            return@rememberLauncherForActivityResult
        }
        val uris = extractUrisFromIntent(result.data)
        viewModel.onFileChooserResult(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fileChooserEvents.collect { request ->
            try {
                fileChooserLauncher.launch(
                    Intent.createChooser(
                        request.intent,
                        context.getString(R.string.file_picker_title)
                    )
                )
            } catch (_: ActivityNotFoundException) {
                viewModel.notifyFilePickerUnavailable()
            }
        }
    }

    val activeTab = uiState.activeTab
    val canHandleWebBack = activeTab != null &&
        (activeTab.canGoBack || (activeTab.isChildTab && uiState.tabs.size > 1))

    val showWelcomeScreen = uiState.isInitialized && !uiState.settings.hasCompletedOnboarding

    BackHandler(enabled = uiState.fullscreenView != null) {
        viewModel.exitFullscreen()
    }

    BackHandler(
        enabled = !showWelcomeScreen &&
            uiState.fullscreenView == null &&
            !uiState.isSettingsOpen &&
            canHandleWebBack
    ) {
        viewModel.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            // We handle statusBars/navigationBars inside BrowserToolbar and BrowserBottomBar
            // so the WebView fills edge-to-edge cleanly between chrome bars.
            @Suppress("UNUSED_VARIABLE")
            val unused = innerPadding

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideLayout = maxWidth >= 600.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    BrowserToolbar(
                        currentUrl = activeTab?.url ?: uiState.settings.homepageUrl,
                        progress = activeTab?.progress ?: 0,
                        isLoading = activeTab?.isLoading ?: false,
                        desktopModeEnabled = uiState.settings.desktopModeEnabled,
                        canGoBack = activeTab?.canGoBack ?: false,
                        canGoForward = activeTab?.canGoForward ?: false,
                        tabCount = uiState.tabs.size.coerceAtLeast(1),
                        isWideLayout = isWideLayout,
                        onNavigateUrl = viewModel::loadUrlInActiveTab,
                        onBack = { viewModel.goBack() },
                        onForward = viewModel::goForward,
                        onRefreshOrStop = viewModel::reloadOrStop,
                        onHome = viewModel::openHomepage,
                        onToggleDesktopMode = {
                            viewModel.setDesktopMode(!uiState.settings.desktopModeEnabled)
                        },
                        onOpenTabs = viewModel::openTabsSheet,
                        onOpenSettings = viewModel::openSettings
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        WebViewHostContainer(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        val errorState = activeTab?.errorState
                        if (errorState != null) {
                            NetworkErrorOverlay(
                                error = errorState,
                                onRetry = viewModel::retryActiveTab,
                                onOpenHome = viewModel::openHomepage,
                                onOpenSettings = viewModel::openSettings
                            )
                        }

                        if (!uiState.isInitialized) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (!isWideLayout) {
                        BrowserBottomBar(
                            canGoBack = activeTab?.canGoBack ?: false,
                            canGoForward = activeTab?.canGoForward ?: false,
                            tabCount = uiState.tabs.size.coerceAtLeast(1),
                            onBack = { viewModel.goBack() },
                            onForward = viewModel::goForward,
                            onHome = viewModel::openHomepage,
                            onOpenTabs = viewModel::openTabsSheet,
                            onOpenSettings = viewModel::openSettings
                        )
                    }
                }
            }
        }

        // Settings Screen Overlay (keeps WebView mounted & live underneath)
        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = slideInVertically(initialOffsetY = { it / 8 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 8 }) + fadeOut()
        ) {
            SettingsScreen(
                settings = uiState.settings,
                isTikTokLoggedIn = uiState.isTikTokLoggedIn,
                onNavigateBack = viewModel::closeSettings,
                onOpenTikTokLogin = viewModel::openTikTokLogin,
                onLogoutTikTok = viewModel::logoutTikTok,
                onSaveHomepage = viewModel::saveHomepage,
                onResetHomepage = viewModel::resetHomepageToDefault,
                onToggleDesktopMode = viewModel::setDesktopMode,
                onToggleJavaScript = viewModel::setJavaScriptEnabled,
                onWebsiteZoomChange = viewModel::setWebsiteZoomPercent,
                onResetWebsiteZoom = viewModel::resetWebsiteZoom,
                onClearBrowsingData = viewModel::clearBrowsingData
            )
        }

        if (uiState.isTabsSheetOpen) {
            TabSwitcherSheet(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTab?.id,
                onSelectTab = viewModel::selectTab,
                onCloseTab = viewModel::closeTab,
                onNewTab = {
                    viewModel.openNewTab(
                        initialUrl = uiState.settings.homepageUrl.ifBlank {
                            BrowserConstants.DEFAULT_HOMEPAGE_URL
                        },
                        switchToTab = true
                    )
                },
                onDismiss = viewModel::closeTabsSheet
            )
        }

        val fullscreenView = uiState.fullscreenView
        if (fullscreenView != null) {
            FullscreenVideoHost(
                customView = fullscreenView,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showWelcomeScreen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { -it / 12 }
        ) {
            WelcomeScreen(
                onStartClick = viewModel::completeOnboarding
            )
        }
    }
}

@Composable
private fun WebViewHostContainer(
    uiState: BrowserUiState,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val activeWebView = viewModel.getWebViewForTab(uiState.activeTab?.id)

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { container ->
            if (activeWebView == null) {
                container.removeAllViews()
            } else if (container.getChildAt(0) !== activeWebView) {
                (activeWebView.parent as? ViewGroup)?.removeView(activeWebView)
                container.removeAllViews()
                container.addView(
                    activeWebView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun FullscreenVideoHost(
    customView: View,
    modifier: Modifier = Modifier
) {
    val hostView = LocalView.current
    val activity = LocalContext.current as? Activity

    DisposableEffect(customView) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                (customView.parent as? ViewGroup)?.removeView(customView)
                addView(
                    customView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        modifier = modifier.background(Color.Black)
    )
}

private fun extractUrisFromIntent(data: Intent?): Array<Uri>? {
    if (data == null) return null
    val clipData = data.clipData
    if (clipData != null && clipData.itemCount > 0) {
        val uris = buildList {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { add(it) }
            }
        }
        if (uris.isNotEmpty()) return uris.toTypedArray()
    }
    val singleUri = data.data
    return if (singleUri != null) arrayOf(singleUri) else null
}
