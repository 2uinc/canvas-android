package com.instructure.student.offline.util.downloader

import android.view.View
import android.webkit.WebView
import com.instructure.canvasapi2.managers.PageManager
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.canvasapi2.models.Page
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApiResponse
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.pandautils.utils.loadHtmlWithIframes
import com.instructure.pandautils.views.CanvasWebView
import com.instructure.student.R
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.OfflineHtmlVideoChecker
import kotlinx.coroutines.Job
import org.jsoup.Jsoup

class PageOfflineDownloader(
    private val mCanvasContext: CanvasContext, private val mUrl: String, keyItem: KeyOfflineItem
) : BaseIframeDownloader(keyItem) {

    private var mFetchDataJob: WeaveJob? = null
    private var mLoadHtmlJob: Job? = null

    init {
        initWebView()
    }

    override fun createWebView(): WebView {
        val webView = CanvasWebView(ContextKeeper.appContext)
        addWebViewClient(webView)
        return webView
    }

    override fun startPreparation() {
        mFetchDataJob = tryWeave {
            val response = awaitApiResponse {
                PageManager.getPageDetails(mCanvasContext, mUrl, true, it)
            }

            response.body()?.let { loadPage(it) }

            if (response.body() == null) {
                processError(
                    OfflineDownloadException(message = "Failed to retrieve Page - body is Null")
                )
            }
        } catch {
            it.printStackTrace()

            processError(OfflineDownloadException(it, "Failed to retrieve Page"))
        }
    }

    override fun destroy() {
        mFetchDataJob?.cancel()
        mLoadHtmlJob?.cancel()

        super.destroy()
    }

    private fun loadPage(page: Page) {
        if (page.body != null && page.body != "null" && page.body != "") {
            val webView = getWebView() as? CanvasWebView

            // Add RTL support
            if (webView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                page.body = "<body dir=\"rtl\">${page.body}</body>"
            }

            // Some pages need to know the course ID, so we set it on window.ENV.COURSE.id (See MBL-14324)
            val body =
                """<script>window.ENV = { COURSE: { id: "${mCanvasContext.id}" } };</script>""" +
                        page.body.orEmpty()

            // Load the html with the helper function to handle iframe cases
            mLoadHtmlJob = webView?.loadHtmlWithIframes(ContextKeeper.appContext, body, {
                if (isDestroyed.get()) return@loadHtmlWithIframes

                val result = webView.formatHtml(it, page.title)
                val document = Jsoup.parse(result)

                getAllVideosAndDownload(document, object :
                    OfflineHtmlVideoChecker.OnVideoProcessListener() {
                    override fun onVideoLoaded(videoLinks: List<OfflineHtmlVideoChecker.VideoLink>) {
                        if (isDestroyed.get()) return

                        setInitialDocument(document)
                    }

                    override fun onError(e: Exception) {
                        processError(e)
                    }
                }, isNeedReplaceIframes = true)
            })

        } else if (page.body == null || page.body?.endsWith("") == true) {
            processError(
                OfflineDownloadException(message = ContextKeeper.appContext.getString(R.string.noPageFound))
            )
        }
    }
}