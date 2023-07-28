package com.instructure.student.offline.util.downloader

import android.net.Uri
import android.webkit.WebView
import com.google.gson.Gson
import com.instructure.canvasapi2.managers.OAuthManager
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.canvasapi2.utils.weave.StatusCallbackError
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.weave
import com.instructure.pandautils.views.CanvasWebView
import com.twou.offline.item.KeyOfflineItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class LTIOfflineDownloader(private var mUrl: String, keyItem: KeyOfflineItem) :
    BaseIframeDownloader(keyItem) {

    private var sessionAuthJob: WeaveJob? = null

    private var isFirstLaunch = true

    private val mHtmlListener = object : HtmlListener {
        override fun onHtmlLoaded(html: String) {
            if (isDestroyed.get()) return

            if (isFirstLaunch) {
                isFirstLaunch = false

                val document = Jsoup.parse(html)
                val text = document.text()

                try {
                    val ltiItem = Gson().fromJson(text, LTIItem::class.java)
                    processLTIItem(ltiItem)
                } catch (e: Exception) {
                    e.printStackTrace()

                    processError(e)
                }

            } else {
                setInitialDocument(Jsoup.parse("<html>$html</html>"))
            }
        }
    }

    init {
        initWebView()
    }

    override fun createWebView(): WebView {
        val webView = CanvasWebView(ContextKeeper.appContext)
        addWebViewClient(webView)
        return webView
    }

    override fun startPreparation() {
        sessionAuthJob = weave {
            if (ApiPrefs.domain in mUrl) {
                try {
                    mUrl = awaitApi {
                        OAuthManager.getAuthenticatedSession(mUrl, it)
                    }.sessionUrl
                } catch (e: StatusCallbackError) {
                    e.printStackTrace()

                    processError(e)
                    return@weave
                }
            }

            mUrl = Uri.parse(mUrl).buildUpon()
                .appendQueryParameter("embedded", "1")
                .appendQueryParameter("display", "borderless")
                .build().toString()

            getWebView(mHtmlListener)?.loadUrl(mUrl, getReferer())
        }
    }

    override fun destroy() {
        sessionAuthJob?.cancel()

        super.destroy()
    }

    private fun processLTIItem(ltiItem: LTIItem) {
        handler.post {
            getWebView(mHtmlListener)?.loadUrl(ltiItem.url)
        }
    }

    private fun getReferer(): Map<String, String> = mutableMapOf(Pair("Referer", ApiPrefs.domain))

    data class IframeElement(val link: String, val element: Element)

    data class LTIItem(val url: String)
}