package com.instructure.student.offline.util.downloader

import android.net.Uri
import com.twou.offline.base.downloader.BaseHtmlOnePageDownloader
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.BaseOfflineUtils
import com.twou.offline.util.OfflineHtmlVideoChecker
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

abstract class BaseIframeDownloader(keyItem: KeyOfflineItem) : BaseHtmlOnePageDownloader(keyItem) {

    private var mInitHtmlDocument: Document? = null
    private var mCurrentIframePosition = 0

    private val mIframeLinks = mutableListOf<LTIOfflineDownloader.IframeElement>()

    protected fun setInitialDocument(document: Document) {
        mInitHtmlDocument = document

        launch { checkForIFrames() }
    }

    private fun checkForIFrames(isWithVideoCheck: Boolean = true) {
        mCurrentIframePosition = 0
        mIframeLinks.clear()

        mInitHtmlDocument?.getElementsByTag("iframe")?.forEach { element ->
            val iframeLink = element.attr("src")
            if (iframeLink.isNotBlank() && (iframeLink.startsWith("http://") ||
                        iframeLink.startsWith("https://"))
            ) {
                supportedIframes.find { iframeLink.contains(it) }?.let {
                    mIframeLinks.add(LTIOfflineDownloader.IframeElement(iframeLink, element))
                }
            }
        }

        if (mIframeLinks.isEmpty()) {
            mInitHtmlDocument?.let { document ->
                if (isWithVideoCheck) {
                    getAllVideosAndDownload(document, object :
                        OfflineHtmlVideoChecker.OnVideoProcessListener() {
                        override fun onVideoLoaded(videoLinks: List<OfflineHtmlVideoChecker.VideoLink>) {
                            mInitHtmlDocument?.let { document -> removeUnusedIframes(document) }
                        }

                        override fun onError(e: Exception) {
                            processError(e)
                        }
                    }, isNeedReplaceIframes = false)

                } else {
                    removeUnusedIframes(document)
                }
            }

        } else {
            loadIframe()
        }
    }

    private fun loadIframe() {
        val link = mIframeLinks[mCurrentIframePosition].link
        val html = downloadFileContent(link)

        val iFrameDocument = Jsoup.parse(html)
        mIframeLinks[mCurrentIframePosition].element.replaceWith(iFrameDocument)

        mCurrentIframePosition++

        getAllVideosAndDownload(
            iFrameDocument, object : OfflineHtmlVideoChecker.OnVideoProcessListener() {
                override fun onVideoLoaded(videoLinks: List<OfflineHtmlVideoChecker.VideoLink>) {
                    if (isDestroyed.get()) return

                    if (mCurrentIframePosition >= mIframeLinks.size) {
                        launch { checkForIFrames(isWithVideoCheck = false) }

                    } else {
                        launch { loadIframe() }
                    }
                }

                override fun onError(e: Exception) {
                    processError(e)
                }
            }, isNeedReplaceIframes = false
        )
    }

    private fun removeUnusedIframes(document: Document) {
        document.getElementsByTag("iframe")?.forEach { element ->
            element.replaceWith(Jsoup.parse(BaseOfflineUtils.getHtmlErrorOverlay(element)))
        }

        document.getElementsByTag(HtmlLink.A)?.forEach { element ->
            if (element.hasAttr(HtmlLink.HREF)) {
                var href = element.attr(HtmlLink.HREF)
                if (href.contains("/courses/") && href.contains("/files/")) {
                    href = Uri.parse(href.replace("/preview", ""))
                        .buildUpon()
                        .appendPath("download")
                        .build().toString()

                    element.attr(HtmlLink.HREF, href)
                }
            }
        }

        finishPreparation(document)
    }

    companion object {

        private val supportedIframes = listOf("frost.2u.com")
    }
}