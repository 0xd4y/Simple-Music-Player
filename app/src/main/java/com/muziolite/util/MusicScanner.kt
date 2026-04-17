package com.muziolite.util

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.muziolite.model.Song

object MusicScanner {
    fun scanSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        // Most recently added first
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )
        cursor?.use {
            val idCol      = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol     = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateCol    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (it.moveToNext()) {
                val path = it.getString(pathCol) ?: continue
                songs.add(
                    Song(
                        id        = it.getLong(idCol),
                        title     = it.getString(titleCol)   ?: "Unknown Title",
                        artist    = it.getString(artistCol)  ?: "Unknown Artist",
                        album     = it.getString(albumCol)   ?: "Unknown Album",
                        duration  = it.getLong(durCol),
                        path      = path,
                        albumId   = it.getLong(albumIdCol),
                        dateAdded = it.getLong(dateCol)
                    )
                )
            }
        }
        return songs
    }
}
