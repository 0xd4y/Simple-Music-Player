package com.muziolite.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.muziolite.R
import com.muziolite.databinding.ItemSongBinding
import com.muziolite.model.Song

class SongAdapter(
    private val onSongClick:      (Song, Int) -> Unit,
    private val onSongLongClick:  (Song) -> Unit        = {},
    private val onFavouriteClick: (Song) -> Unit        = {},
    private val onCheckedChange:  (Song, Boolean) -> Unit = { _, _ -> }
) : ListAdapter<Song, SongAdapter.SongVH>(Diff()) {

    private var activeSongId: Long = -1L
    private var favouriteIds: Set<Long> = emptySet()

    // Multi-select state
    var selectionMode: Boolean = false
        private set
    private val selectedIds = mutableSetOf<Long>()

    fun setActiveSong(id: Long) {
        val old = currentList.indexOfFirst { it.id == activeSongId }
        val new = currentList.indexOfFirst { it.id == id }
        activeSongId = id
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    fun setFavourites(ids: Set<Long>) {
        favouriteIds = ids
        notifyDataSetChanged()
    }

    fun enterSelectionMode() {
        selectionMode = true
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun getSelectedSongs(): List<Song> = currentList.filter { selectedIds.contains(it.id) }

    fun selectAll() {
        selectedIds.addAll(currentList.map { it.id })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        SongVH(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SongVH, position: Int) =
        holder.bind(getItem(position))

    inner class SongVH(private val b: ItemSongBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(song: Song) {
            val isActive   = song.id == activeSongId
            val isFav      = favouriteIds.contains(song.id)
            val isSelected = selectedIds.contains(song.id)

            b.tvTitle.text  = song.title
            b.tvArtist.text = song.artist
            b.tvDuration.text = song.durationFormatted()

            b.tvTitle.setTextColor(
                b.root.context.getColor(if (isActive) R.color.purple_primary else R.color.text_primary)
            )
            b.ivPlayingIndicator.visibility = if (isActive && !selectionMode) View.VISIBLE else View.GONE

            // Album art
            Glide.with(b.ivAlbumArt)
                .load(Uri.parse("content://media/external/audio/albumart/${song.albumId}"))
                .placeholder(R.drawable.ic_music_note_bg)
                .error(R.drawable.ic_music_note_bg)
                .centerCrop()
                .into(b.ivAlbumArt)

            // Favourite heart
            b.btnFavourite.visibility = if (selectionMode) View.GONE else View.VISIBLE
            b.btnFavourite.setImageResource(
                if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            b.btnFavourite.setColorFilter(
                b.root.context.getColor(if (isFav) R.color.purple_primary else R.color.text_muted)
            )
            b.btnFavourite.setOnClickListener { onFavouriteClick(song) }

            // Checkbox (selection mode)
            b.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            b.checkbox.isChecked  = isSelected
            b.checkbox.setOnClickListener {
                val nowChecked = b.checkbox.isChecked
                if (nowChecked) selectedIds.add(song.id) else selectedIds.remove(song.id)
                onCheckedChange(song, nowChecked)
            }

            // Root click
            b.root.setOnClickListener {
                if (selectionMode) {
                    val nowChecked = !selectedIds.contains(song.id)
                    if (nowChecked) selectedIds.add(song.id) else selectedIds.remove(song.id)
                    b.checkbox.isChecked = nowChecked
                    onCheckedChange(song, nowChecked)
                } else {
                    onSongClick(song, absoluteAdapterPosition)
                }
            }
            b.root.setOnLongClickListener {
                if (!selectionMode) { onSongLongClick(song) }
                true
            }

            // Highlight selected row
            b.root.setBackgroundColor(
                b.root.context.getColor(
                    if (selectionMode && isSelected) R.color.selection_highlight
                    else android.R.color.transparent
                )
            )
        }
    }

    class Diff : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
        override fun areContentsTheSame(a: Song, b: Song) = a == b
    }
}
