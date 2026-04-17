package com.muziolite.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.muziolite.PlayerActivity
import com.muziolite.R
import com.muziolite.adapter.SongAdapter
import com.muziolite.databinding.FragmentSongsBinding
import com.muziolite.model.Song
import com.muziolite.service.MusicService
import com.muziolite.util.FavouritesManager
import com.muziolite.util.MusicScanner
import com.muziolite.util.PlaylistManager
import com.muziolite.util.SearchHistoryManager

class SongsFragment : Fragment() {

    private var _b: FragmentSongsBinding? = null
    private val b get() = _b!!

    private lateinit var adapter: SongAdapter
    private var allSongs: List<Song> = emptyList()

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

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSongsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SongAdapter(
            onSongClick     = { _, idx -> playSong(idx) },
            onSongLongClick = { song -> enterSelectionMode(song) },
            onFavouriteClick = { song -> toggleFavourite(song) },
            onCheckedChange  = { _, _ -> updateSelectionCount() }
        )
        b.rvSongs.adapter = adapter

        setupSearch()
        setupSelectionBar()
        loadSongsFromDevice()
    }

    override fun onResume() {
        super.onResume()
        // Refresh favourite state in case user came back from somewhere
        refreshFavourites()
    }

    override fun onStart() {
        super.onStart()
        val ctx = context ?: return
        try {
            val token = SessionToken(ctx, ComponentName(ctx, MusicService::class.java))
            controllerFuture = MediaController.Builder(ctx, token).buildAsync()
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

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    fun reloadSongs() {
        if (_b == null) return
        loadSongsFromDevice()
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun setupSearch() {
        val et = b.etSearch

        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && et.text.isNullOrEmpty()) showHistory()
            else if (!hasFocus) hideHistory()
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()
                b.btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                if (q.isNotEmpty()) hideHistory() else showHistory()
                applyFilter(q)
            }
        })

        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = et.text.toString().trim()
                if (q.isNotEmpty()) {
                    SearchHistoryManager.add(requireContext(), q)
                    hideKeyboard()
                    et.clearFocus()
                }
                true
            } else false
        }

        b.btnClearSearch.setOnClickListener {
            et.setText("")
            b.btnClearSearch.visibility = View.GONE
            showHistory()
        }
    }

    private fun applyFilter(q: String) {
        val filtered = if (q.isEmpty()) allSongs
        else allSongs.filter {
            it.title.lowercase().contains(q.lowercase()) ||
            it.artist.lowercase().contains(q.lowercase())
        }
        adapter.submitList(filtered)
        b.llEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showHistory() {
        val ctx = context ?: return
        val history = SearchHistoryManager.get(ctx)
        if (history.isEmpty()) { b.scrollHistory.visibility = View.GONE; return }
        b.llHistoryChips.removeAllViews()
        history.forEach { term ->
            val chip = Chip(ctx).apply {
                text = term
                setChipBackgroundColorResource(R.color.surface_elevated)
                setTextColor(ctx.getColor(R.color.text_secondary))
                chipStrokeWidth = 0f
                isCloseIconVisible = true
                setCloseIconTintResource(R.color.text_muted)
                setOnClickListener {
                    b.etSearch.setText(term)
                    b.etSearch.setSelection(term.length)
                    hideHistory()
                }
                setOnCloseIconClickListener {
                    SearchHistoryManager.add(ctx, "") // no-op; remove manually
                    // Rebuild without this term
                    val newList = SearchHistoryManager.get(ctx).toMutableList()
                    newList.remove(term)
                    ctx.getSharedPreferences("muziolite_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("search_history", com.google.gson.Gson().toJson(newList)).apply()
                    showHistory()
                }
            }
            b.llHistoryChips.addView(chip)
        }
        b.scrollHistory.visibility = View.VISIBLE
    }

    private fun hideHistory() { b.scrollHistory.visibility = View.GONE }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
    }

    // ─── Multi-select ─────────────────────────────────────────────────────────

    private fun setupSelectionBar() {
        b.btnCancelSelection.setOnClickListener { exitSelectionMode() }
        b.btnSelectAll.setOnClickListener {
            adapter.selectAll()
            updateSelectionCount()
        }
        b.btnAddSelected.setOnClickListener { showAddSelectedToPlaylistDialog() }
    }

    private fun enterSelectionMode(firstSong: Song) {
        adapter.enterSelectionMode()
        // simulate click on the long-pressed song
        val idx = adapter.currentList.indexOf(firstSong)
        if (idx >= 0) {
            val selected = adapter.getSelectedSongs().map { it.id }.toMutableSet()
            selected.add(firstSong.id)
            adapter.notifyItemChanged(idx)
        }
        b.selectionBar.visibility = View.VISIBLE
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        b.selectionBar.visibility = View.GONE
    }

    private fun updateSelectionCount() {
        val n = adapter.getSelectedSongs().size
        b.tvSelectionCount.text = "$n selected"
    }

    private fun showAddSelectedToPlaylistDialog() {
        val ctx = context ?: return
        val songs = adapter.getSelectedSongs()
        if (songs.isEmpty()) { Toast.makeText(ctx, "Select at least one song", Toast.LENGTH_SHORT).show(); return }

        val playlists = PlaylistManager.getAll(ctx)
        if (playlists.isEmpty()) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.add_to_playlist)
                .setMessage("No playlists yet. Go to the Playlists tab to create one.")
                .setPositiveButton(R.string.cancel, null).show()
            return
        }
        val names = playlists.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Add ${songs.size} songs to…")
            .setItems(names) { _, which ->
                val p = playlists[which]
                var added = 0
                songs.forEach { if (PlaylistManager.addSong(ctx, p.id, it.id)) added++ }
                Toast.makeText(ctx, "$added song(s) added to ${p.name}", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
            }.show()
    }

    // ─── Favourites ───────────────────────────────────────────────────────────

    private fun refreshFavourites() {
        val ctx = context ?: return
        adapter.setFavourites(FavouritesManager.getAll(ctx))
    }

    private fun toggleFavourite(song: Song) {
        val ctx = context ?: return
        val nowFav = FavouritesManager.toggle(ctx, song.id)
        adapter.setFavourites(FavouritesManager.getAll(ctx))
        Toast.makeText(ctx,
            if (nowFav) "Added to Favourites" else "Removed from Favourites",
            Toast.LENGTH_SHORT).show()
    }

    // ─── Device scan ──────────────────────────────────────────────────────────

    private fun loadSongsFromDevice() {
        val ctx = context ?: return
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
            b.llEmpty.visibility = View.VISIBLE; return
        }
        allSongs = MusicScanner.scanSongs(ctx)
        adapter.submitList(allSongs)
        refreshFavourites()
        b.llEmpty.visibility = if (allSongs.isEmpty()) View.VISIBLE else View.GONE
        (activity as? com.muziolite.MainActivity)?.updateSongCount(allSongs.size)
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    private fun playSong(index: Int) {
        val c = ctrl ?: return
        val displayed = adapter.currentList
        val song = displayed.getOrNull(index) ?: return
        val globalIdx = allSongs.indexOf(song).takeIf { it >= 0 } ?: 0
        try {
            val items = allSongs.map { s ->
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
            c.setMediaItems(items, globalIdx, 0L)
            c.prepare(); c.play()
            adapter.setActiveSong(song.id)
            startActivity(Intent(requireContext(), PlayerActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not play: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
