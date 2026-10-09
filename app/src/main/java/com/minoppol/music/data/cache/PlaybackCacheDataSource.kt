package com.minoppol.music.data.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import com.minoppol.music.data.lxmusic.LxMusicStreamProxy
import com.minoppol.music.data.lxmusic.LxPlaybackQualityTracker
import com.minoppol.music.data.lxmusic.LxStreamId
import timber.log.Timber
import java.io.IOException

@OptIn(UnstableApi::class)
class PlaybackCacheDataSource(
    upstreamFactory: DataSource.Factory,
    private val cacheManager: PlaybackCacheManager,
    private val lxProxy: LxMusicStreamProxy,
    private val qualityTracker: LxPlaybackQualityTracker,
) : DataSource {

    private val upstreamDataSourceFactory: DataSource.Factory = upstreamFactory

    private fun cachedDataSourceFactory(): CacheDataSource.Factory? {
        val cache = cacheManager.cacheOrNull() ?: return null
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private var activeCacheKey: String? = null

    private var teeSink: CacheDataSink? = null
    private var teeWritten: Long = 0L

    private var teeHoleSpan: CacheSpan? = null

    private var teeContentLength: Long = -1L

    private var activeId: LxStreamId? = null
    private var activeQuality: String? = null

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        val uri = dataSpec.uri
        val id = lxProxy.parsePlaybackUri(uri.toString())
            ?: lxProxy.parseProxyUrl(uri.toString())
            ?: return openDelegate(upstreamDataSourceFactory.createDataSource(), dataSpec)

        if (!cacheManager.isEnabled) {
            return openDelegate(upstreamDataSourceFactory.createDataSource(), dataSpec)
        }

        val preferred = lxProxy.preferredQualitySync()
        var probe = lxProxy.peekResolved(id)
        if (probe != null && probe.quality != null &&
            qualityRank(probe.quality) < qualityRank(preferred)
        ) {
            val invalidateKey = id.source + "_" + id.songmid
            val now = System.currentTimeMillis()
            val last = lastInvalidateTime[invalidateKey] ?: 0L
            if (now - last > INVALIDATE_COOLDOWN_MS) {
                Timber.tag(TAG).d(
                    "cached quality %s < preferred %s for %s/%s — invalidate & refetch",
                    probe.quality, preferred, id.source, id.songmid,
                )
                lxProxy.invalidateUrlCacheForId(id)
                lastInvalidateTime[invalidateKey] = now
                probe = null
            } else {
                Timber.tag(TAG).d(
                    "cached quality %s < preferred %s for %s/%s — cooldown, accept",
                    probe.quality, preferred, id.source, id.songmid,
                )
            }
        }
        probe?.let { info ->
            val q = info.quality
            if (q != null) {
                val key = PlaybackCacheManager.cacheKey(id.source, id.songmid, q)
                val result = openCached(dataSpec, id, key, q, fallbackToUpstream = true)
                qualityTracker.report(id.source, id.songmid, q, trusted = true)
                return result
            }
        }

        val upstream = upstreamDataSourceFactory.createDataSource()
        try {
            val length = upstream.open(dataSpec)
            val actual = lxProxy.peekResolved(id)
            if (actual == null || actual.quality == null) {
                Timber.tag(TAG).d("no quality probe for %s/%s — pass-through", id.source, id.songmid)
                return openDelegate(upstream, dataSpec)
            }
            val key = PlaybackCacheManager.cacheKey(id.source, id.songmid, actual.quality)
            beginTeeWrite(upstream, dataSpec, id, actual.quality, key, length)
            return length
        } catch (e: IOException) {
            runCatching { upstream.close() }
            val entries = cacheManager.findCachedEntries(id.source, id.songmid)
            for (entry in entries) {
                if (!cacheManager.isCacheComplete(entry.cacheKey)) {
                    Timber.tag(TAG).w("cached quality %s incomplete, skipping", entry.quality)
                    continue
                }
                try {
                    Timber.tag(TAG).d(
                        "offline fallback to cached quality %s for %s/%s", entry.quality, id.source, id.songmid
                    )
                    val result = openCached(dataSpec, id, entry.cacheKey, entry.quality, fallbackToUpstream = false)
                    qualityTracker.report(id.source, id.songmid, entry.quality, trusted = false)
                    return result
                } catch (e2: IOException) {
                    Timber.tag(TAG).w(e2, "cached quality %s failed, trying next", entry.quality)
                    continue
                }
            }
            throw e
        }
    }

    private fun beginTeeWrite(
        upstream: DataSource,
        dataSpec: DataSpec,
        id: LxStreamId,
        quality: String,
        cacheKey: String,
        length: Long,
    ) {
        listeners.forEach { upstream.addTransferListener(it) }
        delegate = upstream
        activeCacheKey = cacheKey
        val cache = cacheManager.cacheOrNull()
        if (cache == null) {
            Timber.tag(TAG).w("cache unavailable, streaming without cache key=%s", cacheKey)
            return
        }
        val sinkSpec = dataSpec.buildUpon()
            .setKey(cacheKey)
            .setLength(C.LENGTH_UNSET.toLong())
            .setFlags(dataSpec.flags and DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN.inv())
            .build()
        fun newSink() = CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
        val holeSpan = runCatching {
            cache.startReadWriteNonBlocking(cacheKey, dataSpec.position, C.LENGTH_UNSET.toLong())
        }.onFailure { Timber.tag(TAG).w(it, "tee lock failed key=%s", cacheKey) }
            .getOrNull()
        if (holeSpan == null) {
            Timber.tag(TAG).w("tee lock unavailable, streaming without cache key=%s", cacheKey)
            return
        }
        var sink: CacheDataSink? = runCatching { newSink().also { it.open(sinkSpec) } }
            .onFailure { Timber.tag(TAG).w(it, "tee sink open failed key=%s", cacheKey) }
            .getOrNull()
        sink?.let {
            teeSink = it
            teeHoleSpan = holeSpan
            teeWritten = 0L
            teeContentLength = if (length >= 0) dataSpec.position + length else -1L
            activeId = id
            activeQuality = quality
            Timber.tag(TAG).d(
                "tee write begin key=%s len=%d pos=%d", cacheKey, length, dataSpec.position
            )
        } ?: run {
            Timber.tag(TAG).w("tee sink unavailable, streaming without cache key=%s", cacheKey)
        }
    }

    private fun openCached(
        dataSpec: DataSpec,
        id: LxStreamId,
        cacheKey: String,
        quality: String,
        fallbackToUpstream: Boolean,
    ): Long {
        val factory = cachedDataSourceFactory()
        if (factory == null) {
            if (fallbackToUpstream) {
                return openDelegate(upstreamDataSourceFactory.createDataSource(), dataSpec)
            }
            throw IOException("Cache unavailable for $cacheKey")
        }
        val cache = cacheManager.cacheOrNull()
        val cached = factory.createDataSource()
        val beforeBytes = runCatching {
            cache?.getCachedBytes(cacheKey, 0, Long.MAX_VALUE)
        }.getOrDefault(-2L)
        return try {
            val ret = openDelegate(cached, dataSpec.buildUpon().setKey(cacheKey).build())
            activeCacheKey = cacheKey
            activeId = id
            activeQuality = quality
            val afterBytes = runCatching {
                cache?.getCachedBytes(cacheKey, 0, Long.MAX_VALUE)
            }.getOrDefault(-2L)
            Timber.tag(TAG).d(
                "openCached OK key=%s before=%d after=%d len=%d", cacheKey, beforeBytes, afterBytes, ret
            )
            ret
        } catch (e: IOException) {
            if (fallbackToUpstream) {
                Timber.tag(TAG).w(e, "cached open failed, fall back to upstream (%s)", cacheKey)
                try {
                    activeCacheKey = null
                    activeId = null
                    activeQuality = null
                    openDelegate(upstreamDataSourceFactory.createDataSource(), dataSpec)
                } catch (e2: IOException) {
                    lxProxy.invalidateUrlCacheForId(id)
                    throw e2
                }
            } else {
                throw e
            }
        }
    }

    private fun openDelegate(dataSource: DataSource, dataSpec: DataSpec): Long {
        listeners.forEach { dataSource.addTransferListener(it) }
        delegate = dataSource
        return dataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = delegate?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        val sink = teeSink
        if (sink != null && n > 0) {
            if (runCatching { sink.write(buffer, offset, n) }.isSuccess) {
                teeWritten += n
            } else {
                Timber.tag(TAG).w("tee write failed at %d, stop caching", teeWritten)
                runCatching { sink.close() }
                teeSink = null
                teeHoleSpan?.let { span ->
                    runCatching { cacheManager.cacheOrNull()?.releaseHoleSpan(span) }
                }
                teeHoleSpan = null
            }
        }
        return n
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        teeSink?.let { sink ->
            runCatching { sink.close() }
            teeSink = null
        }
        val cache = cacheManager.cacheOrNull()
        teeHoleSpan?.let { runCatching { cache?.releaseHoleSpan(it) } }
        teeHoleSpan = null
        val key = activeCacheKey
        if (key != null && teeContentLength > 0 && teeWritten >= teeContentLength) {
            runCatching {
                val mutations = ContentMetadataMutations()
                ContentMetadataMutations.setContentLength(mutations, teeContentLength)
                cache?.applyContentMetadataMutations(key, mutations)
            }
        }
        delegate?.let { runCatching { it.close() } }
        val id = activeId
        val q = activeQuality
        if (key != null && id != null && q != null && cacheManager.isCacheComplete(key)) {
            Timber.tag(TAG).d("cache complete key=%s q=%s teeWritten=%d — evict lower", key, q, teeWritten)
            cacheManager.removeLowerQualityCaches(id.source, id.songmid, q)
        }
        key?.let { k ->
            val finalBytes = runCatching {
                cache?.getCachedBytes(k, 0, Long.MAX_VALUE)
            }.getOrDefault(-2L)
            Timber.tag(TAG).d("close key=%s finalCached=%d", k, finalBytes)
        }
        activeCacheKey = null
        activeId = null
        activeQuality = null
        teeWritten = 0L
        teeContentLength = -1L
        delegate = null
    }

    companion object {
        const val TAG = "PlaybackCache"
        private const val INVALIDATE_COOLDOWN_MS = 30_000L
        private val lastInvalidateTime = mutableMapOf<String, Long>()

        fun qualityRank(quality: String): Int = when (quality) {
            "128k" -> 0
            "320k" -> 1
            "flac" -> 2
            "flac24bit" -> 3
            "atmos" -> 4
            "master" -> 5
            else -> 1
        }
    }
}
