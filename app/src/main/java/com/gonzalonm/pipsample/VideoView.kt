package com.gonzalonm.pipsample

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import java.io.IOException

class VideoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : RelativeLayout(context, attrs, defStyleAttr) {

    companion object {

        private val TAG = "MovieView"

        /** The amount of time until we fade out the controls.  */
        private val TIMEOUT_CONTROLS = 3000L // ms

    }

    /**
     * Monitors all events related to [VideoView].
     */
    interface VideoListener {

        /**
         * Called when the video is started or resumed.
         */
        fun onVideoStarted() {}

        /**
         * Called when the video is paused or finished.
         */
        fun onVideoStopped() {}

        /**
         * Called when this view should be minimized.
         */
        fun onVideoMinimized() {}
    }

    /** Shows the video playback.  */
    private val mSurfaceView: SurfaceView

    /** This plays the video. This will be null when no video is set.  */
    internal var mMediaPlayer: MediaPlayer? = null

    // Controls
    private val mToggle: ImageButton
    private val mShade: View
    private val mMinimize: ImageButton

    var mVideoResourceId: Int = 0
        set(value) {
            if (value == field) return
            field = value
            val surface = mSurfaceView.holder.surface
            if (surface != null && surface.isValid) {
                closeVideo()
                openVideo(surface)
            }
        }

    var mAdjustViewBounds: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) background = null
                else setBackgroundColor(Color.BLACK)
                requestLayout()
            }
        }

    /** Handles timeout for media controls.  */
    private var mTimeoutHandler: TimeoutHandler? = null

    /** The listener for all the events we publish.  */
    var mVideoListener: VideoListener? = null

    val isPlaying: Boolean
        get() = mMediaPlayer?.isPlaying ?: false

    private var mSavedCurrentPosition: Int = 0

    init {
        setBackgroundColor(Color.BLACK)

        View.inflate(context, R.layout.view_video, this)
        mSurfaceView = findViewById(R.id.surface)
        mShade = findViewById(R.id.shade)
        mToggle = findViewById(R.id.toggle)
        mMinimize = findViewById(R.id.minimize)

        applyAttrs(attrs, defStyleAttr)
        bindViewEvents()
    }

    fun play() {
        if (mMediaPlayer == null) {
            return
        }
        mMediaPlayer!!.start()
        adjustToggleState()
        keepScreenOn = true
        mVideoListener?.onVideoStarted()
    }

    fun pause() {
        if (mMediaPlayer == null) {
            adjustToggleState()
            return
        }
        mMediaPlayer!!.pause()
        adjustToggleState()
        keepScreenOn = false
        mVideoListener?.onVideoStopped()
    }

    /**
     * Shows all the controls.
     */
    fun showControls() {
        TransitionManager.beginDelayedTransition(this)
        mShade.visibility = View.VISIBLE
        mToggle.visibility = View.VISIBLE
        mMinimize.visibility = View.VISIBLE
    }

    /**
     * Hides all the controls.
     */
    fun hideControls() {
        TransitionManager.beginDelayedTransition(this)
        mShade.visibility = View.INVISIBLE
        mToggle.visibility = View.INVISIBLE
        mMinimize.visibility = View.INVISIBLE
    }

    private fun bindViewEvents() {
        val listener = View.OnClickListener { view ->
            when (view.id) {
                R.id.surface -> toggleControls()
                R.id.toggle -> toggle()
                R.id.minimize -> mVideoListener?.onVideoMinimized()
            }
        }

        // Start or reset the timeout to hide controls
        mMediaPlayer?.let { player ->
            if (mTimeoutHandler == null) {
                mTimeoutHandler = TimeoutHandler(this)
            }
            mTimeoutHandler?.let { handler ->
                handler.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS)
                if (player.isPlaying) {
                    handler.sendEmptyMessageDelayed(TimeoutHandler.MESSAGE_HIDE_CONTROLS,
                            TIMEOUT_CONTROLS)
                }
            }
        }
        mSurfaceView.setOnClickListener(listener)
        mToggle.setOnClickListener(listener)
        mMinimize.setOnClickListener(listener)

        // Prepare video playback
        mSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                openVideo(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int,
                                        width: Int, height: Int) {
                // Do nothing
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mMediaPlayer?.let { mSavedCurrentPosition = it.currentPosition }
                closeVideo()
            }
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mMediaPlayer?.let { player ->
            val videoWidth = player.videoWidth
            val videoHeight = player.videoHeight
            if (videoWidth != 0 && videoHeight != 0) {
                val aspectRatio = videoHeight.toFloat() / videoWidth
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
                val height = View.MeasureSpec.getSize(heightMeasureSpec)
                val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
                if (mAdjustViewBounds) {
                    if (widthMode == View.MeasureSpec.EXACTLY
                            && heightMode != View.MeasureSpec.EXACTLY) {
                        super.onMeasure(widthMeasureSpec,
                                View.MeasureSpec.makeMeasureSpec((width * aspectRatio).toInt(),
                                        View.MeasureSpec.EXACTLY))
                    } else if (widthMode != View.MeasureSpec.EXACTLY
                            && heightMode == View.MeasureSpec.EXACTLY) {
                        super.onMeasure(View.MeasureSpec.makeMeasureSpec((height / aspectRatio).toInt(),
                                View.MeasureSpec.EXACTLY), heightMeasureSpec)
                    } else {
                        super.onMeasure(widthMeasureSpec,
                                View.MeasureSpec.makeMeasureSpec((width * aspectRatio).toInt(),
                                        View.MeasureSpec.EXACTLY))
                    }
                } else {
                    val viewRatio = height.toFloat() / width
                    if (aspectRatio > viewRatio) {
                        val padding = ((width - height / aspectRatio) / 2).toInt()
                        setPadding(padding, 0, padding, 0)
                    } else {
                        val padding = ((height - width * aspectRatio) / 2).toInt()
                        setPadding(0, padding, 0, padding)
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        mTimeoutHandler?.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS)
        mTimeoutHandler = null
        super.onDetachedFromWindow()
    }

    internal fun openVideo(surface: Surface) {
        if (mVideoResourceId == 0) {
            return
        }
        mMediaPlayer = MediaPlayer()
        mMediaPlayer?.let { player ->
            player.setSurface(surface)
            try {
                resources.openRawResourceFd(mVideoResourceId).use { fd ->
                    player.setDataSource(fd)
                    player.setOnPreparedListener { mediaPlayer ->
                        // Adjust the aspect ratio of this view
                        requestLayout()
                        if (mSavedCurrentPosition > 0) {
                            mediaPlayer.seekTo(mSavedCurrentPosition)
                            mSavedCurrentPosition = 0
                        } else {
                            // Start automatically
                            play()
                        }
                    }
                    player.setOnCompletionListener {
                        adjustToggleState()
                        keepScreenOn = false
                        mVideoListener?.onVideoStopped()
                    }
                    player.prepare()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to open video", e)
            }
        }
    }

    internal fun closeVideo() {
        mMediaPlayer?.release()
        mMediaPlayer = null
    }

    internal fun toggle() {
        mMediaPlayer?.let { if (it.isPlaying) pause() else play() }
    }

    internal fun toggleControls() {
        if (mShade.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }

    internal fun adjustToggleState() {
        mMediaPlayer?.let {
            if (it.isPlaying) {
                mToggle.contentDescription = resources.getString(R.string.pause)
                mToggle.setImageResource(R.drawable.ic_pause_64dp)
            } else {
                mToggle.contentDescription = resources.getString(R.string.play)
                mToggle.setImageResource(R.drawable.ic_play_arrow_64dp)
            }
        }
    }

    private fun applyAttrs(attrs: AttributeSet?, defStyleAttr: Int) {
        // Attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.VideoView,
                defStyleAttr, R.style.PictureInPicture_VideoView)
        mVideoResourceId = a.getResourceId(R.styleable.VideoView_android_src, 0)
        mAdjustViewBounds = a.getBoolean(R.styleable.VideoView_android_adjustViewBounds, false)
        a.recycle()
    }
}