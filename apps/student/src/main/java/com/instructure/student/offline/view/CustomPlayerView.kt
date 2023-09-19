package com.instructure.student.offline.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.common.collect.ImmutableList
import com.instructure.pandautils.utils.DP
import com.instructure.pandautils.utils.PX
import com.instructure.student.R
import com.instructure.student.databinding.ViewCustomPlayerBinding
import com.instructure.student.offline.util.OfflineStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class CustomPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), Player.Listener {

    private var settingsWindow: PopupWindow
    private var settingsView: RecyclerView
    private var playbackSpeedAdapter: PlaybackSpeedAdapter
    private var controlsWrapper: View
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val mEnterFullscreenButton: AppCompatImageButton
    private val mExitFullscreenButton: AppCompatImageButton

    private var mCastPlayer: CastPlayer? = null

    private var mCurrentVolume = 0f
    private var mActivity: Activity? = null

    private var mListener: Listener? = null
    var isInFullscreenMode = false

    private var mAudioLayout: FrameLayout? = null
    private var mSource = ""
    private var mSubtitleUrl = ""

    private var mCastContext: CastContext? = null
    private var mCastStateListener: CastStateListener? = null
    private var isPiPMode = false
    private var selectedSpeedPosition = 0

    var isFromTts = false
    private val binding =
        ViewCustomPlayerBinding.inflate(LayoutInflater.from(context), this, true)

    private var mSubtitleView: SubtitleView? = null
    private var mSubtitleLayout: FrameLayout? = null
    private var mSubtitleOnButton: AppCompatImageButton? = null
    private var mSubtitleOffButton: AppCompatImageButton? = null

    init {
        exoPlayer.addListener(this)

        mAudioLayout = binding.playerView.findViewById(R.id.audioLayout)

        val muteButton = binding.playerView.findViewById<AppCompatImageButton>(R.id.muteButton)
        val unMuteButton = binding.playerView.findViewById<AppCompatImageButton>(R.id.unMuteButton)

        muteButton.setOnClickListener {
            binding.playerView.player?.volume = mCurrentVolume
            muteButton.visibility = View.GONE
            unMuteButton.visibility = View.VISIBLE
        }

        unMuteButton.setOnClickListener {
            mCurrentVolume = binding.playerView.player?.volume ?: 0f
            binding.playerView.player?.volume = 0f
            muteButton.visibility = View.VISIBLE
            unMuteButton.visibility = View.GONE
        }

        updateVideoSize()
        controlsWrapper = binding.playerView.findViewById(R.id.controlsWrapper)

        mEnterFullscreenButton = binding.playerView.findViewById(R.id.enterFullscreenButton)
        mEnterFullscreenButton.setOnClickListener { enterFullscreen() }

        mExitFullscreenButton = binding.playerView.findViewById(R.id.exitFullscreenButton)
        mExitFullscreenButton.setOnClickListener { exitFullscreen() }
        binding.playerView.isClickable = false

        settingsView =
            RecyclerView(ContextThemeWrapper(context, R.style.ScrollbarRecyclerView)).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.opacity70))
            }
        playbackSpeedAdapter = PlaybackSpeedAdapter(
            resources.getStringArray(R.array.controls_playback_speeds),
            PLAYBACK_SPEEDS
        )
        settingsView.adapter = playbackSpeedAdapter
        settingsView.layoutManager = LinearLayoutManager(getContext())
        settingsWindow =
            PopupWindow(settingsView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true)
        settingsWindow.setOnDismissListener {
            if (isInFullscreenMode) hideBars()
        }
        binding.playerView.findViewById<AppCompatImageButton>(R.id.speedButton)?.apply {
            setOnClickListener {
                displaySettingsWindow()
                settingsView.layoutManager?.scrollToPosition(selectedSpeedPosition)
            }
        }
        OfflineStorageHelper.speedState.onEach { speed ->
            val oldPosition = selectedSpeedPosition
            exoPlayer.setPlaybackSpeed(speed)
            playbackSpeedAdapter.updateSelectedIndex(speed)
            playbackSpeedAdapter.notifyItemChanged(oldPosition)
            playbackSpeedAdapter.notifyItemChanged(PLAYBACK_SPEEDS.indexOfFirst { it == speed })
        }.launchIn(CoroutineScope(Dispatchers.Main))

        mSubtitleView = binding.playerView.subtitleView
        mSubtitleLayout = binding.playerView.findViewById(R.id.subtitleLayout)
        mSubtitleOnButton = binding.playerView.findViewById(R.id.subtitleOnButton)
        mSubtitleOffButton = binding.playerView.findViewById(R.id.subtitleOffButton)

        binding.playerView.setControllerVisibilityListener {
            updateSubtitleViewMargin()
        }

        updateSubtitleViewMargin()
        mSubtitleView?.visibility = View.GONE

        mSubtitleOnButton?.setOnClickListener {
            mSubtitleView?.visibility = View.GONE
            updateSubtitleButtons()
        }

        mSubtitleOffButton?.setOnClickListener {
            mSubtitleView?.visibility = View.VISIBLE
            updateSubtitleButtons()
        }

        binding.playerView.findViewById<View>(R.id.exo_play)?.setOnClickListener {
            binding.playerView.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            )

            mListener?.onIsPlayingChanged(true)
        }

        binding.playerView.findViewById<View>(R.id.exo_pause)?.setOnClickListener {
            binding.playerView.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            )

            mListener?.onIsPlayingChanged(false)
        }

        binding.playerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.playerView.isControllerVisible) {
                    binding.playerView.hideController()

                } else {
                    binding.playerView.showController()
                }
            }
            isInFullscreenMode
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val castButton = binding.playerView.findViewById<MediaRouteButton>(R.id.castButton)

        try {
            val castContext = CastContext.getSharedInstance(context)
            mCastContext = castContext
            mCastPlayer = CastPlayer(castContext)
            mCastPlayer?.setSessionAvailabilityListener(CastAvailabilityListener())

            CastButtonFactory.setUpMediaRouteButton(context.applicationContext, castButton)

            if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) {
                castButton.visibility = VISIBLE
            }
            mCastStateListener = CastStateListener { state ->
                if (state == CastState.NO_DEVICES_AVAILABLE) {
                    castButton?.visibility = GONE

                } else {
                    castButton?.visibility = VISIBLE
                }
            }

            mCastStateListener?.let { castContext.addCastStateListener(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            castButton.visibility = GONE
        }
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        mActivity = null
        mCastStateListener?.let { mCastContext?.removeCastStateListener(it) }

        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        updateVideoSize()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            isFromTts = false
            mListener?.onVideoEnded()
        }
    }

    fun linkActivity(activity: Activity?) {
        mActivity = activity
    }

    fun setListener(l: Listener) {
        mListener = l

        updateVideoSize()
    }

    private fun releasePlayer() {
        exoPlayer.stop()
        exoPlayer.release()
        mCastPlayer?.release()
    }

    fun setSource(source: String, subtitleUrl: String = "") {
        mSource = source
        mSubtitleUrl = subtitleUrl

        binding.playerView.player = exoPlayer.apply {
            playWhenReady = false
            setMediaSource(buildMediaSource())
            prepare()
        }

        mSubtitleLayout?.visibility =
            if (mSubtitleUrl.isNotBlank()) View.VISIBLE else View.GONE
        updateSubtitleButtons()
    }

    private fun buildMediaSource(): MediaSource {
        val mediaItemBuilder = MediaItem.Builder().setUri(mSource)

        if (mSubtitleUrl.isNotBlank()) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(mSubtitleUrl))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            mediaItemBuilder.setSubtitleConfigurations(ImmutableList.of(subtitleConfig))
        }

        return DefaultMediaSourceFactory(DefaultDataSource.Factory(context)).createMediaSource(
            mediaItemBuilder.build()
        )
    }

    fun isVideoPlay(): Boolean {
        return binding.playerView.player?.isPlaying ?: false
    }

    fun getVideoVisibleRect(rect: Rect?) {
        binding.playerView.getGlobalVisibleRect(rect)
    }

    fun hideController() {
        binding.playerView.hideController()
    }

    fun play(isFromTts: Boolean = false) {
        this.isFromTts = isFromTts

        binding.playerView.player?.let { player ->
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(player.currentMediaItemIndex, C.TIME_UNSET)
            }

            player.playWhenReady = true
        }
    }

    fun pause() {
        isFromTts = false
        binding.playerView.player?.playWhenReady = false
    }

    fun setControllerAutoShow(isAutoShow: Boolean) {
        binding.playerView.controllerAutoShow = isAutoShow
    }

    fun isCastPlayer(): Boolean {
        return binding.playerView.player is CastPlayer
    }

    fun setPiPMode(isPiPMode: Boolean) {
        this.isPiPMode = isPiPMode
    }

    fun enterFullscreen() {
        if (isInFullscreenMode) return

        hideBars()

        removeView(binding.playerView)

        (mActivity?.window?.decorView as FrameLayout).addView(
            binding.playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        mExitFullscreenButton.visibility = View.VISIBLE
        mEnterFullscreenButton.visibility = View.GONE

        isInFullscreenMode = true

        updateVideoSize()
    }

    fun exitFullscreen() {
        if (!isInFullscreenMode) return

        (mActivity?.window?.decorView as FrameLayout).removeView(binding.playerView)
        mActivity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(
                window,
                window.decorView
            ).show(WindowInsetsCompat.Type.systemBars())
        }

        addView(
            binding.playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        mExitFullscreenButton.visibility = View.GONE
        mEnterFullscreenButton.visibility = View.VISIBLE

        isInFullscreenMode = false

        updateVideoSize()
    }

    private fun hideBars() {
        mActivity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun updateSubtitleButtons() {
        if (mSubtitleView?.visibility == View.VISIBLE) {
            mSubtitleOnButton?.visibility = View.VISIBLE
            mSubtitleOffButton?.visibility = View.GONE

        } else {
            mSubtitleOnButton?.visibility = View.GONE
            mSubtitleOffButton?.visibility = View.VISIBLE
        }
    }

    private fun updateVideoSize() {
        if (isPiPMode && isInFullscreenMode) return

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        val videoHeight = if (screenWidth < screenHeight) 9 * screenWidth / 16 else screenHeight

        layoutParams?.apply {
            height = videoHeight
            layoutParams = this
        }

        if (!isInFullscreenMode) mListener?.onSizeChanged(context.PX(videoHeight))
    }

    private fun displaySettingsWindow() {
        updateSettingsWindowSize()
        settingsWindow.dismiss()
        val xOff = width - settingsWindow.width - 20
        val yOff = -settingsWindow.height - 20
        val speedButton = binding.playerView.findViewById<AppCompatImageButton>(R.id.speedButton)
        settingsWindow.showAsDropDown(speedButton, xOff, yOff)
    }

    private fun updateSettingsWindowSize() {
        settingsView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val maxWidth = controlsWrapper.measuredWidth - 20
        val itemWidth = settingsView.measuredWidth
        val width = itemWidth.coerceAtMost(maxWidth)
        settingsWindow.width = width
        val maxHeight = controlsWrapper.measuredHeight - 20
        val totalHeight = settingsView.measuredHeight
        val height = maxHeight.coerceAtMost(totalHeight)
        settingsWindow.height = height
    }

    private fun castVideo() {
        val currentProgress = exoPlayer.currentPosition
        exoPlayer.stop()
        binding.playerView.player = mCastPlayer

        val metadata = MediaMetadata.Builder()
            .setTitle("Video")
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaMetadata(metadata)
            .setMimeType(MimeTypes.BASE_TYPE_VIDEO)
            .setUri(mSource)
            .build()

        mCastPlayer?.setMediaItem(mediaItem, currentProgress)

        mAudioLayout?.visibility = View.GONE
    }

    private fun stopCasting() {
        val currentProgress = mCastPlayer?.currentPosition ?: 0
        mCastPlayer?.stop()
        binding.playerView.player = exoPlayer
        exoPlayer.apply {
            setMediaSource(buildMediaSource())
            seekTo(currentProgress)
        }
        mAudioLayout?.visibility = View.VISIBLE
    }

    private fun setPlaybackSpeed(position: Int) {
        OfflineStorageHelper.playerSpeed = PLAYBACK_SPEEDS[position]
    }

    private fun updateSubtitleViewMargin() {
        val bottomMargin = if (binding.playerView.isControllerVisible) 44f else 8f

        (mSubtitleView?.layoutParams as? LayoutParams)?.let { lp ->
            lp.bottomMargin = context.DP(bottomMargin).toInt()
            binding.playerView.subtitleView?.layoutParams = lp
        }
    }

    inner class PlaybackSpeedAdapter(
        private val playbackSpeedTexts: Array<String>,
        private val playbackSpeeds: FloatArray
    ) : RecyclerView.Adapter<SubSettingViewHolder>() {
        fun updateSelectedIndex(playbackSpeed: Float) {
            var closestMatchIndex = 0
            var closestMatchDifference = Float.MAX_VALUE
            for (i in playbackSpeeds.indices) {
                val difference = abs(playbackSpeed - playbackSpeeds[i])
                if (difference < closestMatchDifference) {
                    closestMatchIndex = i
                    closestMatchDifference = difference
                }
            }
            selectedSpeedPosition = closestMatchIndex
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubSettingViewHolder {
            val v = LayoutInflater.from(context)
                .inflate(
                    R.layout.item_playback_speed, parent, false
                )
            return SubSettingViewHolder(v)
        }

        override fun onBindViewHolder(holder: SubSettingViewHolder, _position: Int) {
            val position = holder.absoluteAdapterPosition
            if (position < playbackSpeedTexts.size) {
                holder.textView.text = playbackSpeedTexts[position]
            }
            holder.checkView.visibility =
                if (position == selectedSpeedPosition) View.VISIBLE else View.INVISIBLE
            holder.itemView.setOnClickListener {
                if (position != selectedSpeedPosition) {
                    setPlaybackSpeed(position)
                }
                settingsWindow.dismiss()
            }
        }

        override fun getItemCount(): Int {
            return playbackSpeedTexts.size
        }
    }

    inner class SubSettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView
        val checkView: View

        init {
            if (Util.SDK_INT < 26) {
                itemView.isFocusable = true
            }
            textView = itemView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_text)
            checkView = itemView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_check)
        }
    }

    private inner class CastAvailabilityListener : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            castVideo()
        }

        override fun onCastSessionUnavailable() {
            stopCasting()
        }
    }

    interface Listener {
        fun onSizeChanged(newHeight: Int)
        fun onVideoEnded()
        fun onIsPlayingChanged(isPlaying: Boolean)
    }

    companion object {
        private val PLAYBACK_SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
