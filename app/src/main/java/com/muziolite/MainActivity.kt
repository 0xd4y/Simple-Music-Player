package com.muziolite

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.muziolite.databinding.ActivityMainBinding
import com.muziolite.service.MusicService
import com.muziolite.ui.MainPagerAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val ctrl get() = try {
        controllerFuture?.let { if (it.isDone) it.get() else null }
    } catch (e: Exception) { null }

    private lateinit var pagerAdapter: MainPagerAdapter

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { updateMiniPlayer() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updateMiniPlayer() }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pagerAdapter.getSongsFragment().reloadSongs()
        } else {
            Toast.makeText(this, "Storage permission is required to load music.", Toast.LENGTH_LONG).show()
        }
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Playlists" else "Songs"
        }.attach()

        setupMiniPlayer()
        requestStoragePermission()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val token = SessionToken(this, ComponentName(this, MusicService::class.java))
            controllerFuture = MediaController.Builder(this, token).buildAsync()
            controllerFuture?.addListener({
                try {
                    ctrl?.addListener(playerListener)
                    updateMiniPlayer()
                } catch (e: Exception) { e.printStackTrace() }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onStop() {
        try {
            ctrl?.removeListener(playerListener)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) { e.printStackTrace() }
        controllerFuture = null
        super.onStop()
    }

    fun updateSongCount(count: Int) {
        binding.tvSongCount.text = "$count songs"
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            if (ctrl?.currentMediaItem != null)
                startActivity(Intent(this, PlayerActivity::class.java))
        }
        binding.btnMiniPlayPause.setOnClickListener {
            val c = ctrl ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }
        binding.btnMiniNext.setOnClickListener {
            ctrl?.seekToNextMediaItem()
        }
    }

    private fun requestStoragePermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(perm)
        }
        // If permission already granted, SongsFragment.onViewCreated loads songs itself
    }

    private fun updateMiniPlayer() {
        val c = ctrl
        val item = c?.currentMediaItem
        if (item == null) {
            binding.miniPlayer.visibility = View.GONE
            return
        }
        binding.miniPlayer.visibility = View.VISIBLE
        binding.tvMiniTitle.text  = item.mediaMetadata.title?.toString()  ?: "Unknown"
        binding.tvMiniArtist.text = item.mediaMetadata.artist?.toString() ?: "Unknown"
        Glide.with(this)
            .load(item.mediaMetadata.artworkUri)
            .placeholder(R.drawable.ic_music_note_bg)
            .error(R.drawable.ic_music_note_bg)
            .centerCrop()
            .into(binding.ivMiniArt)
        binding.btnMiniPlayPause.setImageResource(
            if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
}
