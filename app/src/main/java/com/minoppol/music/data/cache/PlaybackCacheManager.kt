package com.minoppol.music.data.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import com.minoppol.music.data.lxmusic.LxQualities
import com.minoppol.music.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    private class VariableLimitCacheEvictor(initialLimitBytes: Long) : CacheEvictor {
        @Volatile
        var limitBytes = initialLimitBytes

        @Volatile
        var evictNow = false

        override fun requiresCacheSpanTouches(): Boolean = false
        override fun onCacheInitialized() {}

        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {}

        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            evictIfNeeded(cache)
        }

        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {}
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {}

        fun evictIfNeeded(cache: Cache) {
            if (!evictNow && cache.cacheSpace <= limitBytes) return
            evictNow = false
            while (cache.cacheSpace > limitBytes) {
                val removed = cache.keys
                    .asSequence()
                    .flatMap { cache.getCachedSpans(it).asSequence() }
                    .sortedBy { it.lastTouchTimestamp }
                    .firstOrNull { span -> runCatching { cache.removeSpan(span) }.isSuccess }
                if (removed == null) break
            }
        }

        fun applyLimit(cache: Cache) {
            evictNow = true
            evictIfNeeded(cache)
        }
    }

    private val evictor = VariableLimitCacheEvictor(
        UserPreferencesRepository.DEFAULT_PLAYBACK_CACHE_LIMIT_MB * 1024L * 1024L
    )

    @Volatile
    var isEnabled: Boolean = true
        private set

    @Volatile
    private var cacheInitFailed = false

    private var cachedSimpleCache: SimpleCache? = null

    fun cacheOrNull(): Cache? {
        if (!isEnabled || cacheInitFailed) return null
        cachedSimpleCache?.let { return it }
        return runCatching {
            SimpleCache(
                File(context.cacheDir, "playback_cache"),
                evictor,
                StandaloneDatabaseProvider(context),
            )
        }.onFailure { e ->
            Timber.e(e, "PlaybackCache: SimpleCache init failed, disabling cache")
            cacheInitFailed = true
            isEnabled = false
        }.getOrNull()?.also { cachedSimpleCache = it }
    }

    val limitMbFlow: Flow<Int> = userPreferencesRepository.playbackCacheLimitMbFlow

    suspend fun setLimitMb(limitMb: Int) = userPreferencesRepository.setPlaybackCacheLimitMb(limitMb)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            limitMbFlow.collect { limitMb ->
                isEnabled = limitMb > 0
                evictor.limitBytes = limitMb.toLong() * 1024L * 1024L
                cacheOrNull()?.let { runCatching { evictor.applyLimit(it) } }
            }
        }
    }

    fun cacheSpaceBytes(): Long = cacheOrNull()?.let { runCatching { it.cacheSpace }.getOrDefault(0L) } ?: 0L

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val cache = cacheOrNull() ?: return@withContext
        runCatching {
            cache.keys.toList().forEach { key ->
                runCatching { cache.removeResource(key) }
                    .onFailure { Timber.w(it, "PlaybackCache: removeResource failed for $key") }
            }
        }.onFailure { Timber.w(it, "PlaybackCache: clearAll failed") }
        Unit
    }

    fun findCachedEntries(source: String, songmid: String): List<CachedEntry> {
        val cache = cacheOrNull() ?: return emptyList()
        val prefix = songKeyPrefix(source, songmid)
        val keys = runCatching { cache.keys.filter { it.startsWith(prefix) } }
            .getOrDefault(emptyList())
        return keys.mapNotNull { key ->
            val rest = key.removePrefix(prefix)
            val quality = LxQualities.ALL.firstOrNull { q -> rest == q || rest.startsWith(q + "_") }
                ?: return@mapNotNull null
            CachedEntry(quality, key)
        }
        .groupBy { it.quality }
        .mapValues { (_, entries) ->
            entries.firstOrNull { it.cacheKey == "${prefix}${it.quality}" } ?: entries.first()
        }
        .values
        .sortedBy { entry ->
            LxQualities.ALL.indexOf(entry.quality).let { if (it < 0) Int.MAX_VALUE else it }
        }
    }

    data class CachedEntry(val quality: String, val cacheKey: String)

    fun removeLowerQualityCaches(source: String, songmid: String, quality: String) {
        val cache = cacheOrNull() ?: return
        val curIndex = LxQualities.ALL.indexOf(quality)
        if (curIndex < 0) return
        findCachedEntries(source, songmid).forEach { e ->
            val idx = LxQualities.ALL.indexOf(e.quality)
            if (idx > curIndex) {
                runCatching { cache.removeResource(e.cacheKey) }
                Timber.d("evicted lower quality cache %s (%s < %s)", e.cacheKey, e.quality, quality)
            }
        }
    }

    fun debugDumpEntries(source: String, songmid: String): String {
        val cache = cacheOrNull() ?: return "cache disabled"
        return findCachedEntries(source, songmid).joinToString("; ") { e ->
            val metaLen = runCatching {
                cache.getContentMetadata(e.cacheKey)
                    .get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            }.getOrDefault(-99L)
            val cached = runCatching { cache.getCachedBytes(e.cacheKey, 0, Long.MAX_VALUE) }
                .getOrDefault(-2L)
            val spans = runCatching { cache.getCachedSpans(e.cacheKey).size }.getOrDefault(-1)
            "${e.quality}[cached=$cached metaLen=$metaLen spans=$spans]"
        }
    }

    fun isCacheComplete(cacheKey: String): Boolean {
        val cache = cacheOrNull() ?: return false
        val cached = runCatching { cache.getCachedBytes(cacheKey, 0, Long.MAX_VALUE) }
            .getOrDefault(0L)
        if (cached <= 0) return false
        val contentLen = runCatching {
            cache.getContentMetadata(cacheKey)
                .get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrDefault(-1L)
        return contentLen > 0 && cached >= contentLen
    }

    companion object {
        fun cacheKey(source: String, songmid: String, quality: String, contentToken: String? = null): String =
            buildString {
                append("lx_").append(source).append('_').append(songmid).append('_').append(quality)
                if (!contentToken.isNullOrBlank()) append('_').append(contentToken)
            }

        fun songKeyPrefix(source: String, songmid: String): String =
            "lx_${source}_${songmid}_"
    }
}
