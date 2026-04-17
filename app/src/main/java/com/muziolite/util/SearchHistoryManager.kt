package com.muziolite.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SearchHistoryManager {
    private const val PREFS   = "muziolite_prefs"
    private const val KEY     = "search_history"
    private const val MAX     = 8
    private val gson = Gson()

    fun get(ctx: Context): List<String> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun add(ctx: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = get(ctx).toMutableList()
        list.remove(q)          // remove duplicate
        list.add(0, q)          // add to front
        val trimmed = list.take(MAX)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, gson.toJson(trimmed)).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
