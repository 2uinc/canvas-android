package com.instructure.student.offline.util.downloader

import android.net.Uri
import com.google.gson.Gson
import com.instructure.canvasapi2.managers.OAuthManager
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.canvasapi2.utils.weave.StatusCallbackError
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.weave
import com.instructure.student.offline.findParameterValue
import com.instructure.student.offline.item.*
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineUnsupportedException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.OfflineConst
import com.twou.offline.util.OfflineLogs
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URL
import java.util.concurrent.TimeUnit


class LTIOfflineDownloader(private var mUrl: String, keyItem: KeyOfflineItem) :
    BaseIframeDownloader(keyItem) {

    private var sessionAuthJob: WeaveJob? = null

    private var isGetAuthUrl = false
    private var mReloadCount = 0

    private val mHtmlListener = object : HtmlListener {
        override fun onHtmlLoaded(html: String) {
            if (isDestroyed.get()) return

            if (isGetAuthUrl) {
                isGetAuthUrl = false

                val document = Jsoup.parse(html)
                val text = document.text()

                try {
                    val ltiItem = Gson().fromJson(text, LTIItem::class.java)
                    processLTIItem(ltiItem)
                } catch (e: Exception) {
                    e.printStackTrace()

                    processError(e)
                }
                return
            }

            if (!OfflineConst.IS_PREPARED) {
                resourceSet.forEach { resource ->
                    if (resource.contains("/lti/course-player/")) {
                        handler.post {
                            if (html.contains("<video")) {
                                processHtml(html, isOnlineOnly = false)
                                return@post
                            }

                            if (html.contains("Error loading segment")) {
                                processError(
                                    OfflineDownloadException(message = "Error loading segment")
                                )
                                return@post
                            }

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
                    } else if (resource.contains("/leap/view/lti/provider/")) {
                        processHtml(html, isOnlineOnly = true)
                        return

                    } else if (resource.contains("/oyster/player/") || resource.contains("lti/media-player/")) {
                        if (!html.contains("oyster-wrapper")) {
                            OfflineLogs.e(TAG, "Issue with OYSTER found, reloading...")

                            reloadLti()
                            return
                        }
                    }
                }

            } else if (html.contains("Invalid LTI request")) {
                processError(
                    OfflineDownloadException(message = "Error loading LTI")
                )
            }

            processHtml(html, isOnlineOnly = false)
        }
    }

    init {
        initWebView()
    }

    override fun isNeedDelayWebView(): Boolean {
        return !isGetAuthUrl
    }

    override fun startPreparation() {
        processDebug("start prepare LTI content")

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
        launch {
            try {
                val text = downloadFileContent(mUrl, mutableMapOf(Pair("Referer", ApiPrefs.domain)))

                if (text.contains("unauthenticated")) {
                    isGetAuthUrl = true
                    handler.post { getWebView(mHtmlListener)?.loadUrl(mUrl) }

                } else if (text.contains("unauthorized")) {
                    processError(
                        OfflineUnsupportedException(message = "No support for Unauthorized content")
                    )

                } else {
                    val ltiItem = Gson().fromJson(text, LTIItem::class.java)
                    processLTIItem(ltiItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()

                processError(
                    OfflineDownloadException(message = "Something went wrong")
                )
            }
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

    private fun processLTIItem(ltiItem: LTIItem) {
        if (ltiItem.url.isNullOrEmpty()) {
            processError(OfflineDownloadException(message = "URL is empty"))
            return
        }

        if (!OfflineConst.IS_PREPARED) {
            handler.post { getWebView(mHtmlListener)?.loadUrl(ltiItem.url) }
            return
        }

        val content = downloadFileContent(ltiItem.url)
        var action = ""
        val params = mutableListOf<Pair<String, String>>()

        Jsoup.parse(content).getElementsByTag("form").firstOrNull()
            ?.let { formElement ->
                action = formElement.attr("action")

                formElement.getElementsByTag("input")?.forEach { element ->
                    val name = element.attr("name")
                    val value = element.attr("value")

                    params.add(Pair(name, value))
                }
            }

        if (action.isNotEmpty() && params.isNotEmpty()) {
            var redirectUrl = getFormRedirect(action, params)

            if (redirectUrl.isEmpty()) {
                processError(
                    OfflineDownloadException(message = "Error getting Redirect URL")
                )

            } else {
                if (redirectUrl.startsWith("/")) {
                    val uri = Uri.parse(action)
                    val host = uri.scheme + "://" + uri.host
                    redirectUrl = host + redirectUrl
                }

                if (redirectUrl.contains("media-player?auth_token=")) {
                    processOysterContent(redirectUrl)

                } else if (redirectUrl.contains("leap")) {
                    processError(
                        OfflineUnsupportedException(message = "No support for Leap")
                    )

                } else if (redirectUrl.contains("inscribe.education")) {
                    processError(
                        OfflineUnsupportedException(message = "No support for inscribe education")
                    )

                } else if (redirectUrl.contains("course-player?auth_token=")) {
                    processCoursePlayerContent(redirectUrl)

                } else {
                    handler.post { getWebView(mHtmlListener)?.loadUrl(redirectUrl) }
                }
            }

        } else {
            handler.post { getWebView(mHtmlListener)?.loadUrl(mUrl) }
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

    private fun processOysterContent(redirectUrl: String) {
        val url = URL(redirectUrl)
        val token = url.findParameterValue("auth_token") ?: ""
        val uuid = url.findParameterValue("segmentUuid")

        val host = url.protocol + "://" + url.host

        val oysterContent = downloadFileContent(
            "${host}/content/v3/segments/${uuid}?readOnly=true",
            mapOf("Authorization" to token)
        )
        val oysterItem =
            Gson().fromJson(oysterContent, DownloadsContentPlayerItem::class.java)
        val oysterElement = oysterItem.segment.elements.firstOrNull()

        val videoContent = downloadFileContent(
            "${host}/content/files-api/files/bundler/cit-oyster?videoUUID=${oysterElement?.videoUuid}",
            mapOf("Authorization" to token)
        )
        val videoItem = Gson().fromJson(videoContent, DownloadsOysterVideoItem::class.java)
        val videoUrl = videoItem.sources.minByOrNull { it.size }?.url ?: ""

        val html = ContextKeeper.appContext.assets.open("offline_oyster.html").reader().readText()
            .replace("#PLAYER#", OfflineConst.OFFLINE_VIDEO_SCRIPT)
            .replace("#VIDEO#", videoUrl)
            .replace("#SUBTITLE#", videoItem.trackURL)
            .replace("#TRANSCRIPT#", videoItem.transcription)

        setInitialDocument(Jsoup.parse("<html>$html</html>"))
    }

    private fun processCoursePlayerContent(redirectUrl: String) {
        val url = URL(redirectUrl)
        val token = url.findParameterValue("auth_token") ?: ""

        val host = url.protocol + "://" + url.host

        val content = downloadFileContent(
            "$host/content/v3${url.ref}", mapOf("Authorization" to token)
        )

        val coursePlayerItem = Gson().fromJson(content, DownloadsContentPlayerItem::class.java)
        if (coursePlayerItem.segment.elements.size > 1) {
            processError(
                OfflineUnsupportedException(message = "No support for Course Player")
            )

        } else {
            val element = coursePlayerItem.segment.elements.firstOrNull()
            if (!element?.videoUuid.isNullOrEmpty()) {
                try {
                    processCoursePlayerVideoElement(element!!, host, token)
                } catch (e: Exception) {
                    e.printStackTrace()

                    handler.post { getWebView(mHtmlListener)?.loadUrl(redirectUrl) }
                }

            } else {
                processError(
                    OfflineUnsupportedException(message = "No support for Course Player without videoUUID for type " + element?.typeId)
                )
            }
        }
    }

    private fun processCoursePlayerVideoElement(
        element: DownloadsElementItem, host: String, token: String
    ) {
        val videoUuid = element.videoUuid
        val videoContent = downloadFileContent(
            "$host/content/files-api/metadata?uuid=$videoUuid", mapOf("Authorization" to token)
        )
        val videoItem =
            Gson().fromJson(videoContent, Array<DownloadsVideoItem>::class.java).toList()
                .firstOrNull()
        val videoLabel = videoItem?.sources?.minByOrNull { it.size }?.label ?: ""

        val videoUrl =
            "$host/content/files-api/files/$videoUuid?label=$videoLabel&auth-token=$token"
        var subtitleUrl = ""
        var transcriptText = ""

        val projectId = videoItem?.tracks?.firstOrNull()?.projectId ?: ""
        val externalId = videoItem?.tracks?.firstOrNull()?.externalId ?: ""

        if (projectId.isNotEmpty() && externalId.isNotEmpty()) {
            val transcriptContent = downloadFileContent(
                "https://static.3playmedia.com/p/projects/$projectId/files/$externalId/transcript.tpm?format=tpm"
            )

            try {
                val transcriptItem =
                    Gson().fromJson(transcriptContent, DownloadsTranscriptItem::class.java)
                transcriptText = transcriptItem.transcript
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        videoItem?.tracks?.firstOrNull()?.uuid?.let { uuid ->
            if (uuid.isNotEmpty()) {
                subtitleUrl =
                    "$host/content/files-api/files/$videoUuid/tracks/$uuid?auth-token=$token"
            }
        }

        val html =
            ContextKeeper.appContext.assets.open("offline_video.html").reader().readText()
                .replace("#PLAYER#", OfflineConst.OFFLINE_VIDEO_SCRIPT)
                .replace("#VIDEO#", videoUrl)
                .replace("#SUBTITLE#", subtitleUrl)
                .replace("#TRANSCRIPT#", transcriptText)

        setInitialDocument(Jsoup.parse("<html>$html</html>"))
    }

    private fun getFormRedirect(action: String, params: List<Pair<String, String>>): String {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        val formBodyBuilder = FormBody.Builder()
        params.forEach {
            formBodyBuilder.add(it.first, it.second)
        }

        val request = Request.Builder()
            .url(action)
            .post(formBodyBuilder.build())
            .build()

        val call = client.newCall(request)

        val response = call.execute()
        val location = response.headers.find { it.first.equals("location", true) }?.second ?: ""
        response.body?.close()
        return location
    }

    data class IframeElement(val link: String, val element: Element)

    data class LTIItem(val url: String?)

    companion object {

        private const val TAG = "LTIOfflineDownloader"
    }
}