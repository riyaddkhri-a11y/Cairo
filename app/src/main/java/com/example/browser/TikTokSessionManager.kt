package com.example.browser

import android.net.Uri
import android.webkit.CookieManager

object TikTokSessionManager {

    private val SESSION_COOKIE_NAMES = setOf(
        "sessionid",
        "sessionid_ss",
        "sid_tt",
        "uid_tt",
        "uid_tt_ss",
        "sid_guard"
    )

    private val TIKTOK_COOKIE_URLS = listOf(
        "https://www.tiktok.com",
        "https://www.tiktok.com/tiktokstudio",
        "https://www.tiktok.com/login",
        "https://tiktok.com",
        "https://m.tiktok.com",
        "https://www.tiktokv.com",
        "https://tiktokv.com"
    )

    fun parseCookieMap(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        cookieHeader.split(";").forEach { rawPair ->
            val trimmed = rawPair.trim()
            if (trimmed.isEmpty()) return@forEach
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex <= 0) return@forEach
            val name = trimmed.substring(0, eqIndex).trim()
            val value = trimmed.substring(eqIndex + 1).trim()
            if (name.isNotEmpty()) {
                result[name] = value
            }
        }
        return result
    }

    fun hasActiveSessionCookies(cookieHeader: String?): Boolean {
        val cookies = parseCookieMap(cookieHeader)
        return SESSION_COOKIE_NAMES.any { key ->
            val value = cookies[key]
            !value.isNullOrBlank() &&
                !value.equals("deleted", ignoreCase = true) &&
                value != "\"\""
        }
    }

    fun isLoggedIn(cookieManager: CookieManager = CookieManager.getInstance()): Boolean {
        return runCatching {
            cookieManager.setAcceptCookie(true)
            TIKTOK_COOKIE_URLS.any { url ->
                hasActiveSessionCookies(cookieManager.getCookie(url))
            }
        }.getOrDefault(false)
    }

    fun flushCookies(cookieManager: CookieManager = CookieManager.getInstance()) {
        runCatching {
            cookieManager.flush()
        }
    }

    fun clearTikTokSession(cookieManager: CookieManager = CookieManager.getInstance()) {
        runCatching {
            cookieManager.setAcceptCookie(true)
            val discoveredNames = linkedSetOf<String>().apply {
                addAll(SESSION_COOKIE_NAMES)
            }
            TIKTOK_COOKIE_URLS.forEach { url ->
                discoveredNames.addAll(parseCookieMap(cookieManager.getCookie(url)).keys)
            }

            val expirySuffix = "Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            TIKTOK_COOKIE_URLS.forEach { url ->
                val host = Uri.parse(url).host.orEmpty()
                val rootHost = if (host.endsWith("tiktokv.com", ignoreCase = true)) {
                    "tiktokv.com"
                } else {
                    "tiktok.com"
                }
                val rootDomain = ".$rootHost"
                val domains: List<String?> = listOf(
                    null,
                    rootDomain,
                    rootHost,
                    host.takeIf { it.isNotEmpty() }
                ).distinct()

                discoveredNames.forEach { name ->
                    domains.forEach { domain ->
                        val domainPart = if (domain != null) "; Domain=$domain" else ""
                        cookieManager.setCookie(url, "$name=deleted; Path=/$domainPart")
                        cookieManager.setCookie(url, "$name=deleted; Path=/$domainPart; Secure")
                        cookieManager.setCookie(url, "$name=deleted; $expirySuffix$domainPart")
                        cookieManager.setCookie(url, "$name=deleted; $expirySuffix$domainPart; Secure")
                    }
                }
            }
            cookieManager.flush()
        }
    }

    fun isTikTokUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "tiktok.com" ||
            host.endsWith(".tiktok.com") ||
            host == "tiktokv.com" ||
            host.endsWith(".tiktokv.com")
    }
}
