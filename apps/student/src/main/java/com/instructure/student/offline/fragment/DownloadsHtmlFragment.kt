package com.instructure.student.offline.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.ColorRes
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.pandautils.views.HtmlFormatColors
import com.instructure.student.databinding.FragmentDownloadsHtmlBinding
import com.instructure.student.offline.util.DownloadsUtils
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils
import com.twou.offline.Offline
import com.twou.offline.util.OfflineDownloaderUtils
import java.io.File

class DownloadsHtmlFragment : DownloadsBaseFragment() {

    private lateinit var binding: FragmentDownloadsHtmlBinding

    private var mKey = ""
    private var mWebView: WebView? = null

    private val mOfflineRepository = Offline.getOfflineRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        mKey = arguments?.getString(ARG_KEY) ?: ""
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentDownloadsHtmlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initWebViewForOffline()
    }

    override fun onBackPressed(): Boolean {
        if (mWebView?.canGoBack() == true) {
            mWebView?.goBack()
            return true
        }

        return super.onBackPressed()
    }

    private fun initWebViewForOffline() {
        mOfflineRepository.getOfflineModule(mKey)?.let {
            mWebView = DownloadsUtils.getWebView(mContext)

            mWebView?.let { webView ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!isAdded) return
                        binding.progressBar.visibility = View.GONE
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val requestUrl = request?.url?.toString() ?: ""
                        return if (requestUrl.startsWith("file://")) {
                            val file = OfflineUtils.getFile(requestUrl)
                            val uri = OfflineUtils.getFileUri(mContext, file)
                            OfflineUtils.openFile(mContext, uri)
                            true

                        } else {
                            if (requestUrl.startsWith("http") ||
                                requestUrl.startsWith("mailto:")
                            ) {
                                OfflineUtils.openFile(mContext, Uri.parse(requestUrl))
                                true

                            } else {
                                super.shouldOverrideUrlLoading(view, request)
                            }
                        }
                    }
                }

                addVideoPlayerToWebView(webView)
            }
            binding.webViewLayout.addView(mWebView)

            val indexPath = OfflineDownloaderUtils.getStartPagePath(mKey)
            if (OfflineUtils.getKeyType(mKey) == OfflineConst.TYPE_PAGE) {
                val htmlFormatColors = HtmlFormatColors()

                var content = File(indexPath).readText()
                content = content
                    .replace(
                        "{\$BACKGROUND$}", colorResToHexString(htmlFormatColors.backgroundColorRes)
                    )
                    .replace("{\$COLOR$}", colorResToHexString(htmlFormatColors.textColor))
                    .replace("{\$LINK_COLOR$}", colorResToHexString(htmlFormatColors.linkColor))
                    .replace(
                        "{\$VISITED_LINK_COLOR\$}",
                        colorResToHexString(htmlFormatColors.visitedLinkColor)
                    )
                mWebView?.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)

            } else {
                mWebView?.loadUrl(indexPath)
            }
        }
    }

    private fun colorResToHexString(@ColorRes colorRes: Int): String {
        return "#" + Integer.toHexString(ContextKeeper.appContext.getColor(colorRes)).substring(2)
    }

    companion object {

        const val TAG = "DownloadsHtmlFragment"

        private const val ARG_KEY = "ARG_KEY"

        @JvmStatic
        fun newArgs(key: String): Bundle {
            return Bundle().apply { putString(ARG_KEY, key) }
        }
    }
}