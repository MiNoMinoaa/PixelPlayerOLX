package com.minoppol.music.data.service

import com.minoppol.music.data.lxmusic.kugou.KugouCookieStore
import com.minoppol.music.data.lxmusic.kuwo.KuwoCookieStore
import com.minoppol.music.data.lxmusic.netease.NeteaseCookieStore
import com.minoppol.music.data.lxmusic.tencent.TencentCookieStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicServiceCookieStoresEntryPoint {
    fun neteaseCookieStore(): NeteaseCookieStore
    fun tencentCookieStore(): TencentCookieStore
    fun kugouCookieStore(): KugouCookieStore
    fun kuwoCookieStore(): KuwoCookieStore
}
