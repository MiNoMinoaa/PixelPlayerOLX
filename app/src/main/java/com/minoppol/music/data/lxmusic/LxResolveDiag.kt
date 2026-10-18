package com.minoppol.music.data.lxmusic

import timber.log.Timber

object LxResolveDiag {
    fun log(stage: String, vararg attrs: Pair<String, Any?>) {
        val sb = StringBuilder(stage)
        for ((key, value) in attrs) {
            sb.append(' ').append(key).append('=').append(value ?: "null")
        }
        Timber.tag("LxResolveDiag").w(sb.toString())
    }
}
