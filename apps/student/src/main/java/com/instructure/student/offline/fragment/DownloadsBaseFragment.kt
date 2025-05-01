package com.instructure.student.offline.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.instructure.canvasapi2.utils.APIHelper
import com.instructure.pandautils.utils.DP
import com.instructure.student.offline.view.CustomPlayerView

open class DownloadsBaseFragment : Fragment() {

    lateinit var mContext: Context

    private val mOfflineHandler = Handler(Looper.getMainLooper())
    private val mVideoViewList = mutableMapOf<String, CustomPlayerView>()

    private var mCurrentVideoIdToPlay = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mContext = view.context
    }

    override fun onStop() {
        super.onStop()

        pauseVideo()
    }

    fun pauseVideo() {
        mCurrentVideoIdToPlay = ""
        getCurrentVideoPlayer()?.pause()
    }

    @SuppressLint("JavascriptInterface")
    protected fun addVideoPlayerToWebView(webView: WebView) {
        webView.addJavascriptInterface(object : Any() {
            @Suppress("unused")
            @JavascriptInterface
            fun isOnline(): Boolean {
                return APIHelper.hasNetworkConnection()
            }

            @SuppressLint("InflateParams", "ClickableViewAccessibility")
            @Suppress("UNUSED_PARAMETER", "unused")
            @JavascriptInterface
            fun onProcessVideoPlayer(
                url: String, subtitleUrl: String, id: String, x: Int, y: Int
            ) {
                mOfflineHandler.post {
                    var videoView = mVideoViewList[id]

                    if (videoView == null) {
                        videoView = CustomPlayerView(mContext)
                        videoView.linkActivity(activity)
                        videoView.id = "video_view_$id".hashCode()

                        mVideoViewList[id] = videoView

                        webView.addView(
                            videoView,
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

                    videoView.y = mContext.DP(y)
                }
            }

            @Suppress("unused")
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
                            /*var allHeight = 0;
                            var children = document.body.children;
                            for (var i = 0; i < children.length; i++) {
                                allHeight += children[i].offsetHeight;
                                console.log("allHeight  " + allHeight);
                            }*/
                            var height = document.body.getBoundingClientRect().height;
                            console.log("height  " + height);
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
        getFullscreenVideoPlayer()?.let {
            it.exitFullscreen()
            return true
        }

        return false
    }

    private fun pauseAllOtherVideosExceptTheCurrentOne(videoId: String) {
        mVideoViewList.entries.forEach { entry ->
            val playerView = entry.value as? CustomPlayerView
            if (entry.key != videoId && playerView?.isVideoPlay() == true) {
                playerView.pause()
            }
        }
    }

    private fun getCurrentVideoPlayer(): CustomPlayerView? {
        mVideoViewList.values.forEach { view ->
            val playerView = view as? CustomPlayerView
            if (playerView?.isVideoPlay() == true) return playerView
        }
        return null
    }

    private fun getFullscreenVideoPlayer(): CustomPlayerView? {
        mVideoViewList.values.forEach { view ->
            val playerView = view as? CustomPlayerView
            if (playerView?.isInFullscreenMode == true) return playerView
        }
        return null
    }
}