package com.gonzalonm.pipsample

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v7.app.AppCompatActivity
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.ScrollView

class MainActivity : AppCompatActivity(), VideoView.VideoListener {

    companion object {

        /** Intent action for media controls from Picture-in-Picture mode.  */
        private val ACTION_MEDIA_CONTROL = "media_control"

        /** Intent extra for media controls from Picture-in-Picture mode.  */
        private val EXTRA_CONTROL_TYPE = "control_type"

        /** The request code for play action PendingIntent.  */
        private val REQUEST_PLAY = 1

        /** The request code for pause action PendingIntent.  */
        private val REQUEST_PAUSE = 2

        /** The intent extra value for play action.  */
        private val CONTROL_TYPE_PLAY = 1

        /** The intent extra value for pause action.  */
        private val CONTROL_TYPE_PAUSE = 2

    }

    /** This shows the video.  */
    private lateinit var mVideoView: VideoView

    /** The bottom half of the screen; hidden on landscape  */
    private lateinit var mScrollView: ScrollView

    /** The arguments to be used for Picture-in-Picture mode.  */
    private val mPictureInPictureParamsBuilder = PictureInPictureParams.Builder()

    /** A [BroadcastReceiver] to receive action item events from Picture-in-Picture mode.  */
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.let { intent ->
                if (intent.action != ACTION_MEDIA_CONTROL) {
                    return
                }

                // This is where we are called back from Picture-in-Picture action items.
                val controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)
                when (controlType) {
                    CONTROL_TYPE_PLAY -> mVideoView.play()
                    CONTROL_TYPE_PAUSE -> mVideoView.pause()
                }
            }
        }
    }

    private val labelPlay: String by lazy { getString(R.string.play) }
    private val labelPause: String by lazy { getString(R.string.pause) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View references
        mVideoView = findViewById(R.id.movie)
        mScrollView = findViewById(R.id.scroll)

        // Set up the video; it automatically starts.
        mVideoView.mVideoListener = this
        findViewById<Button>(R.id.pip).setOnClickListener { minimize() }
    }

    override fun onStop() {
        // On entering Picture-in-Picture mode, onPause is called, but not onStop.
        // For this reason, this is the place where we should pause the video playback.
        mVideoView.pause()
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        // Show the video controls so the video can be easily resumed.
        if (!isInPictureInPictureMode) {
            mVideoView.showControls()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustFullScreen(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            adjustFullScreen(resources.configuration)
        }
    }

    override fun onVideoStarted() {
        // We are playing the video now. In PiP mode, we want to show an action item to pause
        // the video.
        updatePictureInPictureActions(R.drawable.ic_pause_24dp, labelPause,
                CONTROL_TYPE_PAUSE, REQUEST_PAUSE)
    }

    override fun onVideoStopped() {
        // The video stopped or reached its end. In PiP mode, we want to show an action item
        // to play the video.
        updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, labelPlay,
                CONTROL_TYPE_PLAY, REQUEST_PLAY)
    }

    override fun onVideoMinimized() {
        // The MovieView wants us to minimize it. We enter Picture-in-Picture mode now.
        minimize()
    }

    internal fun updatePictureInPictureActions(@DrawableRes iconId: Int, title: String,
                                               controlType: Int, requestCode: Int) {
        val actions = ArrayList<RemoteAction>()

        // This is the PendingIntent that is invoked when a user clicks on the action item.
        // You need to use distinct request codes for play and pause, or the PendingIntent won't
        // be properly updated.
        val intent = PendingIntent.getBroadcast(this@MainActivity,
                requestCode, Intent(ACTION_MEDIA_CONTROL)
                .putExtra(EXTRA_CONTROL_TYPE, controlType), 0)
        val icon = Icon.createWithResource(this@MainActivity, iconId)
        actions.add(RemoteAction(icon, title, title, intent))

        mPictureInPictureParamsBuilder.setActions(actions)

        // This is how you can update action items (or aspect ratio) for Picture-in-Picture mode.
        // Note this call can happen even when the app is not in PiP mode. In that case, the
        // arguments will be used for at the next call of #enterPictureInPictureMode.
        setPictureInPictureParams(mPictureInPictureParamsBuilder.build())
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Starts receiving events from action items in PiP mode.
            registerReceiver(mReceiver, IntentFilter(ACTION_MEDIA_CONTROL))
        } else {
            // We are out of PiP mode. We can stop receiving events from it.
            unregisterReceiver(mReceiver)
            // Show the video controls if the video is not playing
            if (!mVideoView.isPlaying) {
                mVideoView.showControls()
            }
        }
    }

    /**
     * Enters Picture-in-Picture mode.
     */
    internal fun minimize() {
        // Hide the controls in picture-in-picture mode.
        mVideoView.hideControls()
        // Calculate the aspect ratio of the PiP screen.
        mPictureInPictureParamsBuilder.setAspectRatio(Rational(mVideoView.width, mVideoView.height))
        enterPictureInPictureMode(mPictureInPictureParamsBuilder.build())
    }

    /**
     * Adjusts immersive full-screen flags depending on the screen orientation.
     * @param config The current [Configuration].
     */
    private fun adjustFullScreen(config: Configuration) {
        val decorView = window.decorView
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            mScrollView.visibility = View.GONE
            mVideoView.mAdjustViewBounds = false
        } else {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            mScrollView.visibility = View.VISIBLE
            mVideoView.mAdjustViewBounds = true
        }
    }

}
