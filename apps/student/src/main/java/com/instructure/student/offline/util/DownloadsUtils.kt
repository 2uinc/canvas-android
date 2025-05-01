package com.instructure.student.offline.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object DownloadsUtils {

    @SuppressLint("SetJavaScriptEnabled")
    fun getWebView(context: Context): WebView {
        val webView = WebView(context)
        val webSettings: WebSettings = webView.settings
        webSettings.builtInZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.javaScriptEnabled = true
        webSettings.allowFileAccess = true
        webSettings.useWideViewPort = true
        webSettings.setSupportZoom(true)
        webSettings.displayZoomControls = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowContentAccess = true
        webSettings.domStorageEnabled = true

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(Color.TRANSPARENT)

        return webView
    }
}