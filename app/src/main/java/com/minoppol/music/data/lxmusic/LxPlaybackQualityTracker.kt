package com.minoppol.music.data.lxmusic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxPlaybackQualityTracker @Inject constructor() {

    data class TrackedQuality(val quality: String, val trusted: Boolean)

    private val _qualities = MutableStateFlow<Map<String, TrackedQuality>>(emptyMap())
    val qualities: StateFlow<Map<String, TrackedQuality>> = _qualities.asStateFlow()

    fun report(source: String, songmid: String, quality: String?, trusted: Boolean = false) {
        if (quality.isNullOrBlank()) return
        _qualities.update { map ->
            val next = map.toMutableMap()
            next[key(source, songmid)] = TrackedQuality(quality, trusted)
            if (next.size > MAX_ENTRIES) {
                next.keys.take(next.size - MAX_ENTRIES).forEach(next::remove)
            }
            next
        }
    }

    fun get(source: String?, songmid: String?): TrackedQuality? {
        if (source.isNullOrBlank() || songmid.isNullOrBlank()) return null
        return _qualities.value[key(source, songmid)]
    }

    companion object {
        private const val MAX_ENTRIES = 200

        fun key(source: String, songmid: String): String = "$source/$songmid"
    }
}
