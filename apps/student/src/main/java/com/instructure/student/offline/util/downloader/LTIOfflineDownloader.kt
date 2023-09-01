package com.instructure.student.offline.util.downloader

import android.net.Uri
import com.google.gson.Gson
import com.instructure.canvasapi2.managers.OAuthManager
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.weave.StatusCallbackError
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.weave
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineUnsupportedException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.OfflineLogs
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class LTIOfflineDownloader(private var mUrl: String, keyItem: KeyOfflineItem) :
    BaseIframeDownloader(keyItem) {

    private var sessionAuthJob: WeaveJob? = null

    private var isFirstLaunch = true
    private var mReloadCount = 0

    private val mHtmlListener = object : HtmlListener {
        override fun onHtmlLoaded(html: String) {
            if (isDestroyed.get()) return

            if (isFirstLaunch) {
                isFirstLaunch = false

                val document = Jsoup.parse(html)
                val text = document.text()

                try {
                    val ltiItem = Gson().fromJson(text, LTIItem::class.java)
                    if (ltiItem.url.isNullOrEmpty()) {
                        processError(
                            OfflineUnsupportedException(message = "No support when URL is empty")
                        )

                    } else {
                        processLTIItem(ltiItem.url)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()

                    processError(e)
                }

            } else {
                resourceSet.forEach {
                    if (it.contains("/lti/course-player/")) {
                        handler.post {
                            getWebView()?.evaluateJavascript(
                                "(function(){" +
                                        "return document.querySelectorAll(\"div[class*='NavigationItem']\").length / 2 " +
                                        "})()"
                            ) { value ->
                                try {
                                    processHtml(html, isOnlineOnly = value.toInt() > 1)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    processHtml(html, isOnlineOnly = false)
                                }
                            }
                        }
                        return

                    } else if (it.contains("/leap/view/lti/provider/")) {
                        processHtml(html, isOnlineOnly = true)
                        return

                    } else if (it.contains("/oyster/player/") || it.contains("lti/media-player/")) {
                        if (!html.contains("oyster-wrapper")) {
                            OfflineLogs.e(TAG, "Issue with OYSTER found, reloading...")

                            reloadLti()
                            return
                        }
                    }
                }

                processHtml(html, isOnlineOnly = false)
            }
        }
    }

    init {
        initWebView()
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

            loadLti()
        }
    }

    override fun destroy() {
        sessionAuthJob?.cancel()

        super.destroy()
    }

    private fun loadLti() {
        isFirstLaunch = true
        handler.post {
            getWebView(mHtmlListener)?.loadUrl(mUrl, getReferer())
        }
    }

    private fun reloadLti() {
        if (mReloadCount < 3) {
            mReloadCount++
            loadLti()

        } else {
            processError(
                OfflineDownloadException(message = "Something went wrong")
            )
        }
    }

    private fun processLTIItem(url: String) {
        handler.post {
            getWebView(mHtmlListener)?.loadUrl(url)
        }
    }

    private fun processHtml(html: String, isOnlineOnly: Boolean) {
        if (isOnlineOnly) {
            processError(
                OfflineUnsupportedException(message = "No support for Course Player")
            )

        } else {
            if (html.contains("Third-Party cookies Disabled") ||
                html.contains("MissingKeyMissing Key-Pair-Id")
            ) {
                OfflineLogs.e(
                    TAG,
                    "Issue with LTI found, reloading... " + html.contains("MissingKeyMissing Key-Pair-Id")
                )

                reloadLti()

            } else {
                setInitialDocument(Jsoup.parse("<html>$html</html>"))
            }
        }
    }

    private fun getReferer(): Map<String, String> = mutableMapOf(Pair("Referer", ApiPrefs.domain))

    data class IframeElement(val link: String, val element: Element)

    data class LTIItem(val url: String?)

    companion object {

        private const val TAG = "LTIOfflineDownloader"
    }
}