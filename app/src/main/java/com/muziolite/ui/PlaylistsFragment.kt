package com.muziolite.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muziolite.PlaylistDetailActivity
import com.muziolite.R
import com.muziolite.adapter.PlaylistAdapter
import com.muziolite.databinding.FragmentPlaylistsBinding
import com.muziolite.model.Playlist
import com.muziolite.util.FavouritesManager
import com.muziolite.util.PlaylistManager

class PlaylistsFragment : Fragment() {

    private var _b: FragmentPlaylistsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPlaylistsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PlaylistAdapter(
            onClick      = { pl -> openPlaylist(pl.id, pl.name) },
            onLongClick  = { pl -> if (!pl.id.startsWith("__")) showOptionsDialog(pl) }
        )
        b.rvPlaylists.adapter = adapter
        b.fabNewPlaylist.setOnClickListener { showCreateDialog() }
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun refresh() {
        val ctx = requireContext()
        val favCount = FavouritesManager.getAll(ctx).size
        val favEntry = Playlist(id = "__favourites", name = "❤ Favourites")
            .also { it.songIds.addAll(List(favCount) { 0L }) } // just for count display

        val regular = PlaylistManager.getAll(ctx)
        val all = mutableListOf(favEntry) + regular
        adapter.submitList(all)
        b.tvEmpty.visibility = if (regular.isEmpty() && favCount == 0) View.VISIBLE else View.GONE
    }

    private fun openPlaylist(id: String, name: String) {
        val intent = Intent(requireContext(), PlaylistDetailActivity::class.java)
        intent.putExtra("playlist_id", id)
        intent.putExtra("playlist_name", name)
        startActivity(intent)
    }

    private fun showCreateDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = getString(R.string.playlist_name_hint)
            setHintTextColor(ctx.getColor(R.color.text_muted))
            setTextColor(ctx.getColor(R.color.text_primary))
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.create_playlist)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { PlaylistManager.create(ctx, name); refresh() }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showOptionsDialog(pl: Playlist) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(pl.name)
            .setItems(arrayOf(getString(R.string.rename_playlist), getString(R.string.delete_playlist))) { _, which ->
                when (which) {
                    0 -> showRenameDialog(pl)
                    1 -> MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.delete_playlist)
                        .setMessage("Delete \"${pl.name}\"?")
                        .setPositiveButton(R.string.delete) { _, _ ->
                            PlaylistManager.delete(ctx, pl.id); refresh()
                        }
                        .setNegativeButton(R.string.cancel, null).show()
                }
            }.show()
    }

    private fun showRenameDialog(pl: Playlist) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(pl.name)
            setTextColor(ctx.getColor(R.color.text_primary))
            setHintTextColor(ctx.getColor(R.color.text_muted))
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.rename_playlist)
            .setView(input)
            .setPositiveButton(R.string.rename) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { PlaylistManager.rename(ctx, pl.id, name); refresh() }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
