package com.muziolite.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

    private val playlistsFragment = PlaylistsFragment()
    private val songsFragment     = SongsFragment()

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0    -> playlistsFragment
        else -> songsFragment
    }

    fun getSongsFragment(): SongsFragment = songsFragment
}
