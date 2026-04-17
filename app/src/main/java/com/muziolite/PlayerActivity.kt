package com.muziolite

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.muziolite.databinding.ActivityPlayerBinding
import com.muziolite.service.MusicService
import com.muziolite.util.PlaylistManager

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val ctrl get() = try {
        controllerFuture?.let { if (it.isDone) it.get() else null }
    } catch (e: Exception) { null }

    private var rotateAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { updateTrackUI() }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayButton(isPlaying)
            if (isPlaying) resumeDisc() else pauseDisc()
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            binding.btnShuffle.alpha = if (enabled) 1.0f else 0.35f
        }
        override fun onRepeatModeChanged(mode: Int) { updateRepeatIcon(mode) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDiscAnimation()
        setupControls()
        setupSeekBar()
    }

    override fun onStart() {
        super.onStart()
        try {
            val token = SessionToken(this, ComponentName(this, MusicService::class.java))
            controllerFuture = MediaController.Builder(this, token).buildAsync()
            controllerFuture?.addListener({
                try {
                    ctrl?.addListener(playerListener)
                    updateTrackUI()
                    val playing = ctrl?.isPlaying ?: false
                    updatePlayButton(playing)
                    if (playing) resumeDisc()
                    binding.btnShuffle.alpha = if (ctrl?.shuffleModeEnabled == true) 1.0f else 0.35f
                    updateRepeatIcon(ctrl?.repeatMode ?: Player.REPEAT_MODE_OFF)
                } catch (e: Exception) { e.printStackTrace() }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(progressRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(progressRunnable)
    }

    override fun onStop() {
        try {
            ctrl?.removeListener(playerListener)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) { e.printStackTrace() }
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        rotateAnimator?.cancel()
        super.onDestroy()
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            val c = ctrl ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }
        binding.btnNext.setOnClickListener { ctrl?.seekToNextMediaItem() }
        binding.btnPrevious.setOnClickListener {
            val c = ctrl ?: return@setOnClickListener
            if (c.currentPosition > 3000L) c.seekTo(0L)
            else c.seekToPreviousMediaItem()
        }
        binding.btnShuffle.setOnClickListener {
            val c = ctrl ?: return@setOnClickListener
            c.shuffleModeEnabled = !c.shuffleModeEnabled
        }
        binding.btnRepeat.setOnClickListener {
            val c = ctrl ?: return@setOnClickListener
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }

        binding.btnAddToPlaylist.setOnClickListener { showAddToPlaylistDialog() }
    }

    private fun showAddToPlaylistDialog() {
        val songId = ctrl?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val songTitle = ctrl?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "this song"

        val playlists = PlaylistManager.getAll(this)
        if (playlists.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Add to playlist")
                .setMessage("No playlists yet. Go to the Playlists tab to create one first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = playlists.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Add \"$songTitle\" to…")
            .setItems(names) { _, which ->
                val playlist = playlists[which]
                val added = PlaylistManager.addSong(this, playlist.id, songId)
                Toast.makeText(
                    this,
                    if (added) "Added to ${playlist.name}" else "Already in ${playlist.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatMs(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                isSeeking = false
                ctrl?.seekTo(sb?.progress?.toLong() ?: 0L)
            }
        })
    }

    private fun setupDiscAnimation() {
        rotateAnimator = ObjectAnimator.ofFloat(binding.ivAlbumArt, "rotation", 0f, 360f).apply {
            duration = 12000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun resumeDisc() {
        val anim = rotateAnimator ?: return
        if (anim.isPaused) anim.resume() else if (!anim.isRunning) anim.start()
    }

    private fun pauseDisc() { rotateAnimator?.pause() }

    private fun updateTrackUI() {
        val item = ctrl?.currentMediaItem ?: return
        binding.tvTitle.text  = item.mediaMetadata.title?.toString()  ?: "Unknown"
        binding.tvArtist.text = item.mediaMetadata.artist?.toString() ?: "Unknown Artist"
        Glide.with(this)
            .load(item.mediaMetadata.artworkUri)
            .placeholder(R.drawable.ic_music_note_bg)
            .error(R.drawable.ic_music_note_bg)
            .centerCrop()
            .into(binding.ivAlbumArt)
    }

    private fun updateProgress() {
        val c = ctrl ?: return
        val duration = c.duration
        val position = c.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0) return
        if (!isSeeking) {
            binding.seekBar.max = duration.toInt()
            binding.seekBar.progress = position.coerceIn(0, duration).toInt()
            binding.tvCurrentTime.text = formatMs(position.coerceAtLeast(0))
            binding.tvTotalTime.text   = formatMs(duration)
        }
    }

    private fun updatePlayButton(playing: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        )
    }

    private fun updateRepeatIcon(mode: Int) {
        binding.btnRepeat.setImageResource(
            if (mode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        binding.btnRepeat.alpha = if (mode == Player.REPEAT_MODE_OFF) 0.35f else 1.0f
    }

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "${s / 60}:${String.format("%02d", s % 60)}"
    }
}
