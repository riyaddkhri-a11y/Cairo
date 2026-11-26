package com.example.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.R
import com.example.data.BrowserConstants
import com.example.data.BrowserSettings

data class WebErrorState(
    val title: String,
    val description: String,
    val failingUrl: String,
    val isSslError: Boolean = false
)

object WebViewConfig {

    fun buildViewportAndZoomScript(browserSettings: BrowserSettings): String {
        val zoom = BrowserConstants.snapZoomPercent(browserSettings.websiteZoomPercent.toFloat())
        val zoomFactor = zoom / 100.0
        val widthPercent = if (zoom < BrowserConstants.DEFAULT_ZOOM_PERCENT) {
            "${10000.0 / zoom}%"
        } else {
            ""
        }
        val desktopMinWidth = if (browserSettings.desktopModeEnabled) {
            if (zoom < BrowserConstants.DEFAULT_ZOOM_PERCENT) {
                "${(1280 * 100) / zoom}px"
            } else {
                "1280px"
            }
        } else {
            ""
        }
        return """
            (function() {
                try {
                    var isDesktop = ${browserSettings.desktopModeEnabled};
                    if (isDesktop) {
                        var vp = document.querySelector('meta[name="viewport"]');
                        if (!vp) {
                            vp = document.createElement('meta');
                            vp.name = 'viewport';
                            if (document.head) {
                                document.head.appendChild(vp);
                            }
                        }
                        if (vp) {
                            vp.setAttribute('content', 'width=1280, user-scalable=yes');
                        }
                    }
                    var root = document.documentElement;
                    if (root) {
                        if ($zoom === 100) {
                            root.style.removeProperty('zoom');
                            root.style.removeProperty('width');
                        } else {
                            root.style.zoom = '$zoomFactor';
                            if ('$widthPercent'.length > 0) {
                                root.style.width = '$widthPercent';
                            } else {
                                root.style.removeProperty('width');
                            }
                        }
                        if ('$desktopMinWidth'.length > 0) {
                            root.style.minWidth = '$desktopMinWidth';
                        } else if (!isDesktop) {
                            root.style.removeProperty('min-width');
                        }
                    }
                } catch (e) {}
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(
        webView: WebView,
        browserSettings: BrowserSettings,
        cookieManager: CookieManager = CookieManager.getInstance()
    ) {
        val settings = webView.settings
        settings.javaScriptEnabled = browserSettings.javaScriptEnabled
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (browserSettings.desktopModeEnabled) {
            settings.userAgentString = BrowserConstants.DESKTOP_USER_AGENT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            webView.setInitialScale(0)
        } else {
            settings.userAgentString = BrowserConstants.MOBILE_USER_AGENT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            webView.setInitialScale(
                if (!browserSettings.javaScriptEnabled) browserSettings.websiteZoomPercent else 0
            )
        }

        webView.isHorizontalScrollBarEnabled = true
        webView.isVerticalScrollBarEnabled = true
        webView.isScrollbarFadingEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.overScrollMode = View.OVER_SCROLL_ALWAYS

        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    fun applyWebsiteZoom(webView: WebView, browserSettings: BrowserSettings) {
        if (browserSettings.javaScriptEnabled) {
            webView.evaluateJavascript(buildViewportAndZoomScript(browserSettings), null)
        } else if (!browserSettings.desktopModeEnabled) {
            webView.setInitialScale(browserSettings.websiteZoomPercent)
        }
    }

    fun injectDesktopViewportIfEnabled(webView: WebView, browserSettings: BrowserSettings) {
        if (browserSettings.javaScriptEnabled &&
            (browserSettings.desktopModeEnabled ||
                browserSettings.websiteZoomPercent != BrowserConstants.DEFAULT_ZOOM_PERCENT)
        ) {
            webView.evaluateJavascript(buildViewportAndZoomScript(browserSettings), null)
        }
    }

    fun clearAllBrowsingData(
        webViews: List<WebView>,
        cookieManager: CookieManager = CookieManager.getInstance(),
        webStorage: WebStorage = WebStorage.getInstance()
    ) {
        webViews.forEach { webView ->
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
        }
        webStorage.deleteAllData()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    fun buildFileChooserIntent(params: WebChromeClient.FileChooserParams?): Intent {
        val rawAcceptTypes = params?.acceptTypes.orEmpty()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val resolvedMimeTypes = rawAcceptTypes.mapNotNull { token ->
            when {
                token.contains("/") -> token
                token.startsWith(".") -> {
                    val ext = token.removePrefix(".").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                }
                else -> null
            }
        }.distinct()

        val primaryType = when {
            resolvedMimeTypes.size == 1 -> resolvedMimeTypes.first()
            resolvedMimeTypes.isNotEmpty() && resolvedMimeTypes.all { it.startsWith("video/") } -> "video/*"
            resolvedMimeTypes.isNotEmpty() && resolvedMimeTypes.all { it.startsWith("image/") } -> "image/*"
            else -> "*/*"
        }

        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = primaryType
            if (resolvedMimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, resolvedMimeTypes.toTypedArray())
            }
            if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
    }

    fun enqueueDownload(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        cookieManager: CookieManager = CookieManager.getInstance()
    ): Result<String> {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(
                IllegalArgumentException(context.getString(R.string.download_unsupported))
            )
        }

        return runCatching {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(uri).apply {
                setMimeType(mimeType)
                val cookies = cookieManager.getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription(context.getString(R.string.app_name))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            fileName
        }
    }
}

interface WebViewHostCallbacks {
    fun currentSettings(profileId: String): BrowserSettings
    fun onPageStarted(profileId: String, tabId: String, url: String)
    fun onPageFinished(
        profileId: String,
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    )
    fun onProgressChanged(profileId: String, tabId: String, progress: Int)
    fun onTitleReceived(profileId: String, tabId: String, title: String)
    fun onMainFrameError(profileId: String, tabId: String, error: WebErrorState)
    fun onShowFileChooser(
        profileId: String,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean
    fun onCreateChildWindow(profileId: String, resultMsg: Message?): Boolean
    fun onCloseWindow(profileId: String, tabId: String)
    fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?)
    fun onHideCustomView()
    fun onDownloadRequested(
        profileId: String,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    )
    fun onOpenExternalUri(uri: Uri)
    fun onRendererProcessGone(profileId: String, tabId: String, didCrash: Boolean)
    fun onCookiesMaybeUpdated(profileId: String, tabId: String, url: String?)
}

fun createConfiguredWebView(
    context: Context,
    profileId: String,
    webViewProfileName: String,
    tabId: String,
    callbacks: WebViewHostCallbacks,
    forceSoftwareLayer: Boolean = false
): WebView {
    return WebView(context).apply {
        WebViewProfileEngine.bindWebViewToProfile(this, webViewProfileName)
        val profileCookieManager = WebViewProfileEngine.cookieManagerForProfile(webViewProfileName)

        if (forceSoftwareLayer) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        WebViewConfig.applySettings(
            webView = this,
            browserSettings = callbacks.currentSettings(profileId),
            cookieManager = profileCookieManager
        )

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            callbacks.onDownloadRequested(
                profileId = profileId,
                url = url,
                userAgent = userAgent ?: callbacks.currentSettings(profileId).let {
                    if (it.desktopModeEnabled) BrowserConstants.DESKTOP_USER_AGENT else BrowserConstants.MOBILE_USER_AGENT
                },
                contentDisposition = contentDisposition.orEmpty(),
                mimeType = mimeType.orEmpty()
            )
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val scheme = targetUri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                if (scheme == "intent") {
                    try {
                        val intent = Intent.parseUri(targetUri.toString(), Intent.URI_INTENT_SCHEME)
                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                        if (!fallbackUrl.isNullOrBlank()) {
                            view?.loadUrl(fallbackUrl)
                        }
                    } catch (_: Exception) {
                        // Ignore malformed intent URI so WebView stays intact
                    }
                    return true
                }
                if (scheme == "mailto" || scheme == "tel" || scheme == "sms") {
                    callbacks.onOpenExternalUri(targetUri)
                    return true
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    callbacks.onPageStarted(profileId, tabId, url)
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                if (view != null) {
                    WebViewConfig.injectDesktopViewportIfEnabled(
                        view,
                        callbacks.currentSettings(profileId)
                    )
                }
                TikTokSessionManager.flushCookies(profileCookieManager)
                callbacks.onCookiesMaybeUpdated(profileId, tabId, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null) {
                    WebViewConfig.injectDesktopViewportIfEnabled(
                        view,
                        callbacks.currentSettings(profileId)
                    )
                    TikTokSessionManager.flushCookies(profileCookieManager)
                    callbacks.onCookiesMaybeUpdated(profileId, tabId, url)
                    callbacks.onPageFinished(
                        profileId = profileId,
                        tabId = tabId,
                        url = url.orEmpty(),
                        title = view.title.orEmpty(),
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward()
                    )
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (TikTokSessionManager.isTikTokUrl(url)) {
                    TikTokSessionManager.flushCookies(profileCookieManager)
                    callbacks.onCookiesMaybeUpdated(profileId, tabId, url)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val failingUrl = request.url?.toString().orEmpty()
                    val code = error?.errorCode ?: ERROR_UNKNOWN
                    val friendlyDesc = when (code) {
                        ERROR_HOST_LOOKUP -> context.getString(R.string.error_host_lookup)
                        ERROR_CONNECT -> context.getString(R.string.error_connect)
                        ERROR_TIMEOUT -> context.getString(R.string.error_timeout)
                        else -> context.getString(R.string.error_network_generic, code)
                    }
                    callbacks.onMainFrameError(
                        profileId = profileId,
                        tabId = tabId,
                        error = WebErrorState(
                            title = context.getString(R.string.error_title),
                            description = friendlyDesc,
                            failingUrl = failingUrl,
                            isSslError = false
                        )
                    )
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
                callbacks.onMainFrameError(
                    profileId = profileId,
                    tabId = tabId,
                    error = WebErrorState(
                        title = context.getString(R.string.error_ssl_title),
                        description = context.getString(R.string.error_ssl_message),
                        failingUrl = error?.url.orEmpty(),
                        isSslError = true
                    )
                )
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail?.didCrash() ?: true
                } else {
                    true
                }
                callbacks.onRendererProcessGone(profileId, tabId, didCrash)
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChanged(profileId, tabId, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    callbacks.onTitleReceived(profileId, tabId, title)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) return false
                return callbacks.onShowFileChooser(profileId, filePathCallback, fileChooserParams)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return callbacks.onCreateChildWindow(profileId, resultMsg)
            }

            override fun onCloseWindow(window: WebView?) {
                callbacks.onCloseWindow(profileId, tabId)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                callbacks.onShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                callbacks.onHideCustomView()
            }
        }
    }
}

fun launchExternalUriSafely(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No handler installed
    }
}
