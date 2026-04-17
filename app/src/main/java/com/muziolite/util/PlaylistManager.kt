package com.muziolite.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muziolite.model.Playlist

object PlaylistManager {
    private const val PREFS = "muziolite_prefs"
    private const val KEY   = "playlists_json"
    private val gson = Gson()

    fun getAll(context: Context): MutableList<Playlist> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Playlist>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }

    private fun save(context: Context, playlists: List<Playlist>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, gson.toJson(playlists)).apply()
    }

    fun create(context: Context, name: String): Playlist {
        val list = getAll(context)
        val p = Playlist(name = name.trim())
        list.add(p)
        save(context, list)
        return p
    }

    fun rename(context: Context, id: String, newName: String) {
        val list = getAll(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = newName.trim())
            save(context, list)
        }
    }

    fun delete(context: Context, id: String) {
        val list = getAll(context)
        list.removeAll { it.id == id }
        save(context, list)
    }

    fun addSong(context: Context, playlistId: String, songId: Long): Boolean {
        val list = getAll(context)
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        if (list[idx].songIds.contains(songId)) return false
        list[idx].songIds.add(songId)
        save(context, list)
        return true
    }

    fun removeSong(context: Context, playlistId: String, songId: Long) {
        val list = getAll(context)
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            list[idx].songIds.remove(songId)
            save(context, list)
        }
    }

    fun getById(context: Context, id: String): Playlist? =
        getAll(context).find { it.id == id }
}
