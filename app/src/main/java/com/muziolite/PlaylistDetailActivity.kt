package com.muziolite

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.muziolite.adapter.SongAdapter
import com.muziolite.databinding.ActivityPlaylistDetailBinding
import com.muziolite.model.Song
import com.muziolite.service.MusicService
import com.muziolite.util.FavouritesManager
import com.muziolite.util.MusicScanner
import com.muziolite.util.PlaylistManager

class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var adapter: SongAdapter
    private var playlistId   = ""
    private var playlistName = ""
    private var playlistSongs: List<Song> = emptyList()
    private val isFavourites get() = playlistId == "__favourites"

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val ctrl get() = try {
        controllerFuture?.let { if (it.isDone) it.get() else null }
    } catch (e: Exception) { null }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val id = item?.mediaId?.toLongOrNull() ?: -1L
            adapter.setActiveSong(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId   = intent.getStringExtra("playlist_id")   ?: run { finish(); return }
        playlistName = intent.getStringExtra("playlist_name") ?: playlistId

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = playlistName
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SongAdapter(
            onSongClick      = { _, idx -> playSong(idx) },
            onSongLongClick  = { song ->
                if (isFavourites) confirmUnfavourite(song)
                else confirmRemove(song)
            },
            onFavouriteClick = { song -> toggleFavourite(song) }
        )
        binding.rvPlaylistSongs.layoutManager = LinearLayoutManager(this)
        binding.rvPlaylistSongs.adapter = adapter

        binding.btnPlayAll.setOnClickListener {
            if (playlistSongs.isNotEmpty()) playSong(0, shuffle = false)
        }
        binding.btnShuffleAll.setOnClickListener {
            if (playlistSongs.isNotEmpty()) playSong(0, shuffle = true)
        }

        loadPlaylist()
    }

    override fun onStart() {
        super.onStart()
        try {
            val token = SessionToken(this, ComponentName(this, MusicService::class.java))
            controllerFuture = MediaController.Builder(this, token).buildAsync()
            controllerFuture?.addListener({
                try {
                    ctrl?.addListener(playerListener)
                    val id = ctrl?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                    adapter.setActiveSong(id)
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

    private fun loadPlaylist() {
        val allSongs = MusicScanner.scanSongs(this)

        playlistSongs = if (isFavourites) {
            val favIds = FavouritesManager.getAll(this)
            allSongs.filter { favIds.contains(it.id) }
        } else {
            val pl = PlaylistManager.getById(this, playlistId) ?: run { finish(); return }
            val songMap = allSongs.associateBy { it.id }
            pl.songIds.mapNotNull { songMap[it] }
        }

        adapter.submitList(playlistSongs)
        adapter.setFavourites(FavouritesManager.getAll(this))
        binding.tvSubtitle.text = "${playlistSongs.size} songs"
        binding.tvEmpty.visibility = if (playlistSongs.isEmpty()) View.VISIBLE else View.GONE
        binding.btnPlayAll.isEnabled    = playlistSongs.isNotEmpty()
        binding.btnShuffleAll.isEnabled = playlistSongs.isNotEmpty()
    }

    private fun playSong(startIndex: Int, shuffle: Boolean = false) {
        val c = ctrl ?: return
        try {
            val items = playlistSongs.map { s ->
                MediaItem.Builder()
                    .setMediaId(s.id.toString())
                    .setUri(Uri.parse(s.path))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                            .setArtworkUri(Uri.parse("content://media/external/audio/albumart/${s.albumId}"))
                            .build()
                    ).build()
            }
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, startIndex, 0L)
            c.prepare(); c.play()
            adapter.setActiveSong(playlistSongs[startIndex].id)
            startActivity(Intent(this, PlayerActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not play track", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFavourite(song: Song) {
        val nowFav = FavouritesManager.toggle(this, song.id)
        adapter.setFavourites(FavouritesManager.getAll(this))
        if (isFavourites && !nowFav) loadPlaylist() // remove from this list
        Toast.makeText(this,
            if (nowFav) "Added to Favourites" else "Removed from Favourites",
            Toast.LENGTH_SHORT).show()
    }

    private fun confirmRemove(song: Song) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove from playlist")
            .setMessage("Remove \"${song.title}\"?")
            .setPositiveButton("Remove") { _, _ ->
                PlaylistManager.removeSong(this, playlistId, song.id)
                loadPlaylist()
                Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmUnfavourite(song: Song) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove from Favourites")
            .setMessage("Remove \"${song.title}\" from Favourites?")
            .setPositiveButton("Remove") { _, _ ->
                FavouritesManager.toggle(this, song.id)
                loadPlaylist()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
