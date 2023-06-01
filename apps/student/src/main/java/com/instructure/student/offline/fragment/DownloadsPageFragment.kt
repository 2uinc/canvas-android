package com.instructure.student.offline.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.instructure.student.databinding.FragmentDownloadsPageBinding
import com.instructure.student.offline.util.DownloadsUtils
import com.twou.offline.Offline
import com.twou.offline.util.OfflineDownloaderUtils

class DownloadsPageFragment : DownloadsBaseFragment() {

    private lateinit var binding: FragmentDownloadsPageBinding

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
        binding = FragmentDownloadsPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initWebViewForOffline()
    }

    private fun initWebViewForOffline() {
        mOfflineRepository.getOfflineModule(mKey)?.let {
            val url = OfflineDownloaderUtils.getStartPagePath(mKey)
            mWebView = DownloadsUtils.getWebView(mContext)

            mWebView?.let { webView ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!isAdded) return
                        mWebView?.let { webView ->
                            addResizeObserverToWebView(webView)
                        }
                        binding.progressBar.visibility = View.GONE
                    }
                }

                addVideoPlayerToWebView(webView)
            }
            binding.webViewLayout.addView(mWebView)

            mWebView?.loadUrl(url)
        }
    }

    companion object {

        const val TAG = "DownloadsPageFragment"

        private const val ARG_KEY = "ARG_KEY"

        @JvmStatic
        fun newArgs(key: String): Bundle {
            return Bundle().apply { putString(ARG_KEY, key) }
        }
    }
}