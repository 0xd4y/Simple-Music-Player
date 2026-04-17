package com.muziolite.model

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: MutableList<Long> = mutableListOf()
) {
    fun songCount(): Int = songIds.size
}
