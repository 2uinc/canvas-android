package com.instructure.student.offline.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.instructure.student.databinding.FragmentDownloadsHtmlBinding
import com.instructure.student.offline.util.DownloadsUtils
import com.instructure.student.offline.util.OfflineUtils
import com.twou.offline.Offline
import com.twou.offline.util.OfflineDownloaderUtils

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
            val url = OfflineDownloaderUtils.getStartPagePath(mKey)
            mWebView = DownloadsUtils.getWebView(mContext)

            mWebView?.let { webView ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!isAdded) return
                        /*mWebView?.let { webView ->
                            addResizeObserverToWebView(webView)
                        }*/
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

            mWebView?.loadUrl(url)
        }
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