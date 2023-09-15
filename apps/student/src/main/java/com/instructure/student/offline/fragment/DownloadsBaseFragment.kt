package com.instructure.student.offline.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.instructure.canvasapi2.utils.APIHelper
import com.instructure.pandautils.utils.DP
import com.instructure.student.R
import com.instructure.student.offline.view.CustomPlayerView

open class DownloadsBaseFragment : Fragment() {

    lateinit var mContext: Context

    private val mOfflineHandler = Handler(Looper.getMainLooper())
    private val mVideoViewList = mutableMapOf<String, View>()

    private var mCurrentVideoIdToPlay = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mContext = view.context
    }

    @SuppressLint("JavascriptInterface")
    protected fun addVideoPlayerToWebView(webView: WebView) {
        webView.addJavascriptInterface(object : Any() {
            @Suppress("unused")
            @JavascriptInterface
            fun isOnline(): Boolean {
                return APIHelper.hasNetworkConnection()
            }

            @SuppressLint("InflateParams")
            @Suppress("UNUSED_PARAMETER", "unused")
            @JavascriptInterface
            fun onProcessVideoPlayer(
                url: String, subtitleUrl: String, id: String, x: Int, y: Int
            ) {
                mOfflineHandler.post {
                    var videoLayout = mVideoViewList[id]

                    if (videoLayout == null) {
                        videoLayout = LayoutInflater.from(mContext)
                            .inflate(R.layout.layout_offline_video, null)

                        val videoView =
                            videoLayout.findViewById<CustomPlayerView>(R.id.customPlayerView)
                        videoView.linkActivity(activity)
                        videoLayout.id = "video_layout_$id".hashCode()
                        videoView.id = "video_view_$id".hashCode()

                        mVideoViewList[id] = videoLayout

                        webView.addView(
                            videoLayout,
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        )

                        videoView.setListener(object : CustomPlayerView.Listener {
                            override fun onSizeChanged(newHeight: Int) {
                                videoView.post {
                                    webView.evaluateJavascript(
                                        """
                                            function addVideoPlayerToWebView() {
                                                var elements = document.getElementsByClassName("offline-video-player");
                                                
                                                var id = "$id";
                                                var size = "${newHeight}px";
                                                console.log("id  " + id + "; size  " + size + "; length " + elements.length);
                                                        
                                                for (var i = 0; i < elements.length; i++) {
                                                    if (elements[i].getAttribute("id") === id) {
                                                        elements[i].setAttribute("style", "height:" + size + "!important");
                                                    }
                                                }
                                                
                                                findAllVideoPlayers();
                                            }
                                            
                                            addVideoPlayerToWebView();
                                        """.trimIndent(), null
                                    )
                                }
                            }

                            override fun onVideoEnded() {
                                this@DownloadsBaseFragment.onVideoEnded(id)
                            }

                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                if (isPlaying) pauseAllOtherVideosExceptTheCurrentOne(id)
                                onVideoPlaying(id, isPlaying)
                            }
                        })

                        videoView.setSource(url, subtitleUrl)

                        if (mCurrentVideoIdToPlay == id) {
                            videoView.play()
                            mCurrentVideoIdToPlay = ""
                        }
                    }

                    videoLayout?.setPadding(0, mContext.DP(y).toInt(), 0, 0)
                }
            }

            @Suppress("UNUSED_PARAMETER", "unused")
            @JavascriptInterface
            fun onContentHeightChanged(height: Int) {
                mOfflineHandler.post {
                    webView.layoutParams?.let { layoutParams ->
                        layoutParams.height = mContext.DP(height).toInt()
                        webView.layoutParams = layoutParams
                    }
                }
            }

        }, "offline")
    }

    protected fun addResizeObserverToWebView(webView: WebView) {
        webView.evaluateJavascript(
            """
                window.resizeObserver = new ResizeObserver(entries => {
                    if (document.body.children.length > 0) {
                        setTimeout(function() {
                            var height = document.body.children[0].offsetHeight;
                            var scrollHeight = document.body.children[0].scrollHeight;
                            
                            console.log("height  " + height);
                            //if (scrollHeight > height) height = scrollHeight;
                            javascript:window.offline.onContentHeightChanged(height);
                        }, 500);
                    }
                });                    
                    
                var isResizeObserverAdded = false;
                window.mutationObserver = new MutationObserver(mutationRecords => {
                    if (isResizeObserverAdded) return;
                    
                    if (document.body != undefined) {
                        isResizeObserverAdded = true;
                        window.resizeObserver.observe(document.body);
                    }
                });
                
                if (document.body != undefined) {
                    isResizeObserverAdded = true;    
                    window.resizeObserver.observe(document.body);
                        
                } else {
                    window.mutationObserver.observe(document, { childList: true, subtree: true });
                }
            """.trimIndent(), null
        )
    }

    open fun onVideoEnded(videoId: String) {

    }

    open fun onVideoPlaying(videoId: String, isPlaying: Boolean) {

    }

    open fun onBackPressed(): Boolean {
        return false
    }

    private fun pauseAllOtherVideosExceptTheCurrentOne(videoId: String) {
        mVideoViewList.entries.forEach { entry ->
            val childView = (entry.value as ViewGroup).children.firstOrNull()
            val playerView = childView as? CustomPlayerView
            if (entry.key != videoId && playerView?.isVideoPlay() == true) {
                playerView.pause()
            }
        }
    }
}