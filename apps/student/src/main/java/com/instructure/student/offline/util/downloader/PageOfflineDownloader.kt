package com.instructure.student.offline.util.downloader

import androidx.annotation.ColorRes
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.instructure.canvasapi2.managers.OAuthManager
import com.instructure.canvasapi2.managers.PageManager
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.canvasapi2.models.Page
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.canvasapi2.utils.FileUtils
import com.instructure.canvasapi2.utils.weave.*
import com.instructure.pandautils.utils.HtmlContentFormatter
import com.instructure.pandautils.views.CanvasWebView
import com.instructure.pandautils.views.HtmlFormatColors
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

    override fun startPreparation() {
        processDebug("start prepare Page content")

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
            // Add RTL support
            if (ContextKeeper.appContext.resources.getBoolean(R.bool.is_rtl)) {
                page.body = "<body dir=\"rtl\">${page.body}</body>"
            }

            // Some pages need to know the course ID, so we set it on window.ENV.COURSE.id (See MBL-14324)
            val body =
                """<script>window.ENV = { COURSE: { id: "${mCanvasContext.id}" } };</script>""" +
                        page.body.orEmpty()

            // Load the html with the helper function to handle iframe cases
            mLoadHtmlJob = weave {
                val formatter = HtmlContentFormatter(
                    ContextKeeper.appContext, FirebaseCrashlytics.getInstance(), OAuthManager
                )

                val html = formatter.formatHtmlWithIframes(body)

                var formatted = CanvasWebView.applyWorkAroundForDoubleSlashesAsUrlSource(html)
                formatted = CanvasWebView.addProtocolToLinks(formatted)
                formatted = checkForMathTags(formatted)
                val htmlWrapperFileName =
                    if (ApiPrefs.showElementaryView) "html_wrapper_k5.html" else "html_wrapper.html"
                val htmlWrapper =
                    FileUtils.getAssetsFile(ContextKeeper.appContext, htmlWrapperFileName)

                val htmlFormatColors = HtmlFormatColors()

                val result = htmlWrapper
                    .replace("{\$CONTENT$}", formatted)
                    .replace("{\$TITLE$}", page.title ?: "")
                    .replace(
                        "{\$BACKGROUND$}", colorResToHexString(htmlFormatColors.backgroundColorRes)
                    )
                    .replace("{\$COLOR$}", colorResToHexString(htmlFormatColors.textColor))
                    .replace("{\$LINK_COLOR$}", colorResToHexString(htmlFormatColors.linkColor))
                    .replace(
                        "{\$VISITED_LINK_COLOR\$}",
                        colorResToHexString(htmlFormatColors.visitedLinkColor)
                    )

                if (isDestroyed.get()) return@weave

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
            }

        } else if (page.body == null || page.body?.endsWith("") == true) {
            processError(
                OfflineDownloadException(message = ContextKeeper.appContext.getString(R.string.noPageFound))
            )
        }
    }

    private fun checkForMathTags(content: String): String {
        // If this html that we're about to load has a math tag and isn't just an image we want to parse it with MathJax.
        // This is the version that web currently uses (the 2.7.1 is the version number) and this is the check that they do to
        // decide if they'll run the MathJax script on the webview
        if ((content.contains("<math") || content.contains(Regex("\\\$\\\$.+\\\$\\\$|\\\\\\(.+\\\\\\)"))) && !content.contains(
                "<img class='equation_image'"
            )
        ) {
            return """<script type="text/javascript"
                src="https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.1/MathJax.js?config=TeX-AMS-MML_HTMLorMML">
        </script>$content"""
        }
        return content
    }

    private fun colorResToHexString(@ColorRes colorRes: Int): String {
        return "#" + Integer.toHexString(ContextKeeper.appContext.getColor(colorRes)).substring(2)
    }
}