package com.muziolite.util

import android.content.Context

object FavouritesManager {
    private const val PREFS = "muziolite_prefs"
    private const val KEY   = "favourites"

    fun getAll(ctx: Context): MutableSet<Long> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toMutableSet()
    }

    fun isFavourite(ctx: Context, id: Long) = getAll(ctx).contains(id)

    fun toggle(ctx: Context, id: Long): Boolean {
        val set = getAll(ctx)
        val nowFav = if (set.contains(id)) { set.remove(id); false } else { set.add(id); true }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, set.map { it.toString() }.toSet()).apply()
        return nowFav
    }
}
