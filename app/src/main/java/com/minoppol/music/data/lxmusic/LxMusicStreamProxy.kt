package com.minoppol.music.data.lxmusic

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonParser
import com.minoppol.music.data.stream.CloudStreamProxy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxMusicStreamProxy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LxMusicRepository,
    private val httpClient: OkHttpClient,
) : CloudStreamProxy<LxStreamId>(httpClient) {

    private data class SongMetadata(
        val name: String,
        val singer: String,
        val albumName: String = "",
        val pic: String? = null,
        val hash: String? = null,
        val copyrightId: String? = null,
        val extraFields: Map<String, String> = emptyMap(),
    )

    private val metadataLock = Any()
    // 代理 URL 里只有 source/songmid，歌名、歌手这些从此处缓存补回来取链
    private val metadataCache = object : LinkedHashMap<String, SongMetadata>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SongMetadata>?): Boolean =
            size > MAX_METADATA_ENTRIES
    }
    private val cacheFile: File by lazy {
        File(context.filesDir, "lxmusic_metadata_cache.json")
    }

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private val savePending = AtomicBoolean(false)

    private fun loadPersistedCache() {
        runCatching {
            if (!cacheFile.exists()) return
            val json = cacheFile.readText(Charsets.UTF_8)
            if (json.isBlank()) return
            val root = JsonParser.parseString(json).asJsonObject
            val count = synchronized(metadataLock) {
                for ((key, value) in root.entrySet()) {
                    val obj = value.asJsonObject
                    metadataCache[key] = SongMetadata(
                        name = obj.get("name")?.asString ?: "",
                        singer = obj.get("singer")?.asString ?: "",
                        albumName = obj.get("albumName")?.asString ?: "",
                        pic = obj.get("pic")?.asString,
                        hash = obj.get("hash")?.asString,
                        copyrightId = obj.get("copyrightId")?.asString,
                        extraFields = obj.get("extraFields")?.asJsonObject?.entrySet()?.associate { (k, v) -> k to (v.asString ?: "") } ?: emptyMap(),
                    )
                }
                metadataCache.size
            }
            Timber.d("LxMusicStreamProxy: loaded $count cached metadata entries")
        }.onFailure { Timber.w(it, "LxMusicStreamProxy: failed to load cache") }
    }

    private fun scheduleSaveCache() {
        saveScope.launch {
            if (!savePending.compareAndSet(false, true)) return@launch
            delay(SAVE_DEBOUNCE_MS)
            savePending.set(false)
            persistCache()
        }
    }

    private suspend fun persistCache() = saveMutex.withLock {
        runCatching {
            val snapshot = synchronized(metadataLock) { LinkedHashMap(metadataCache) }
            val root = com.google.gson.JsonObject()
            for ((key, meta) in snapshot) {
                val obj = com.google.gson.JsonObject().apply {
                    addProperty("name", meta.name)
                    addProperty("singer", meta.singer)
                    addProperty("albumName", meta.albumName)
                    meta.pic?.let { addProperty("pic", it) }
                    meta.hash?.let { addProperty("hash", it) }
                    meta.copyrightId?.let { addProperty("copyrightId", it) }
                    if (meta.extraFields.isNotEmpty()) {
                        val extraObj = com.google.gson.JsonObject()
                        for ((k, v) in meta.extraFields) extraObj.addProperty(k, v)
                        add("extraFields", extraObj)
                    }
                }
                root.add(key, obj)
            }
            cacheFile.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { Timber.w(it, "LxMusicStreamProxy: failed to save cache") }
    }

    override val allowAnyPublicStreamHost: Boolean = true

    override val allowedHostSuffixes: Set<String> = setOf(
        "kuwo.cn",
        "kugou.com",
        "kugou.net",
        "kgfms.com",
        "qqmusic.qq.com",
        "tc.qq.com",
        "music.126.net",
        "163.com",
        "migu.cn",
        "miguvideo.com",
        "qianqian.com",
    )

    init {
        repository.streamUrlValidator = { url ->
            com.minoppol.music.data.stream.CloudStreamSecurity.isPublicStreamUrl(
                url = url,
                allowHttp = true,
            )
        }
        loadPersistedCache()
    }
    override val cacheExpirationMs = 30L * 60 * 1000

    private val degradedCacheMs = 3L * 60 * 1000L

    override suspend fun cacheKeySuffix(id: LxStreamId): String =
        repository.currentPreferredQuality()

    fun preferredQualitySync(): String = repository.preferredQualitySync

    // ExoPlayer 缓存 key 只取域名+路径，签名时间戳那种每请求都变的得剥掉
    private fun contentTokenOf(url: String): String? = runCatching {
        val uri = Uri.parse(url)
        val rawPath = uri.path ?: return@runCatching null
        val stablePath = rawPath.replaceFirst(
            Regex("^/\\d{14}/[0-9a-fA-F]{32}/"),
            "/",
        )
        val basis = (uri.host.orEmpty() + stablePath).lowercase()
        MessageDigest.getInstance("SHA-1")
            .digest(basis.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(10)
    }.getOrNull()
    override val proxyTag = "LxMusicStreamProxy"
    override val routePath = "/lxmusic/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "lxmusic"
    override val routePrefix = "/lxmusic"

    // URL 里带的是 base64(source/songmid)，不明文出现在代理路径上
    override fun parseRouteParam(value: String): LxStreamId? {
        return runCatching {
            val decoded = String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING), StandardCharsets.UTF_8)
            val slash = decoded.indexOf('/')
            if (slash <= 0) return null
            LxStreamId(decoded.substring(0, slash), decoded.substring(slash + 1))
        }.getOrNull()?.takeIf { validateId(it) }
    }

    // 入参严格卡字符和长度，外部构造的代理 URL 不能注入路径或塞进超长 id
    override fun validateId(id: LxStreamId): Boolean {
        if (id.source.isBlank() || id.songmid.isBlank()) return false
        if (id.source.length > 16 || id.songmid.length > 200) return false
        if (id.source.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return false
        if (id.songmid.any { it == '/' || it == '?' || it == '#' || it.isWhitespace() }) return false
        return true
    }

    override fun formatIdForUrl(id: LxStreamId): String {
        val raw = "${id.source}/${id.songmid}"
        return Base64.encodeToString(raw.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    override fun extractIdFromUri(uri: Uri): String? {
        val host = uri.host ?: return null
        val songmid = uri.path?.removePrefix("/") ?: return null
        return "$host/$songmid"
    }

    override fun resolveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return null
        val source = uri.host ?: return null
        val songmid = uri.path?.removePrefix("/") ?: return null
        val id = LxStreamId(source, songmid)
        if (!validateId(id)) return null
        return getProxyUrl(id)
    }

    override suspend fun resolveStreamUrl(id: LxStreamId): String? = resolveStreamUrlMeta(id)?.url

    override suspend fun resolveStreamUrlMeta(
        id: LxStreamId,
    ): CloudStreamProxy.ResolvedStream? {
        val key = "${id.source}/${id.songmid}"
        val metadata = synchronized(metadataLock) { metadataCache[key] }
        val song = LxSong(
            source = id.source,
            songmid = id.songmid,
            name = metadata?.name ?: "",
            singer = metadata?.singer ?: "",
            albumName = metadata?.albumName ?: "",
            pic = metadata?.pic,
            hash = metadata?.hash,
            copyrightId = metadata?.copyrightId,
            extraFields = metadata?.extraFields ?: emptyMap(),
        )
        // 拿到的不是目标音质时只缓存 3 分钟，降级链接不能长期占坑
        return try {
            repository.resolveMusicUrlWithFallbackMeta(song)?.let { r ->
                CloudStreamProxy.ResolvedStream(
                    r.url,
                    if (r.fullQuality) cacheExpirationMs else degradedCacheMs,
                    r.quality,
                    contentTokenOf(r.url),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "LxMusicStreamProxy: resolve with fallback failed for $key")
            null
        }
    }

    fun resolveLxMusicUri(uriString: String): String? = resolveUri(uriString)

    fun cacheMetadata(
        source: String,
        songmid: String,
        name: String,
        singer: String,
        albumName: String = "",
        pic: String? = null,
        hash: String? = null,
        copyrightId: String? = null,
        extraFields: Map<String, String> = emptyMap(),
    ) {
        val key = "$source/$songmid"
        synchronized(metadataLock) {
            metadataCache[key] = SongMetadata(name, singer, albumName, pic, hash, copyrightId, extraFields)
        }
        scheduleSaveCache()
    }

    fun cacheMetadataIfAbsent(
        source: String,
        songmid: String,
        name: String,
        singer: String,
        albumName: String = "",
        pic: String? = null,
    ) {
        val key = "$source/$songmid"
        val needSave = synchronized(metadataLock) {
            val existing = metadataCache[key]
            if (existing != null && existing.name.isNotBlank()) false
            else {
                metadataCache[key] = SongMetadata(name, singer, albumName, pic)
                true
            }
        }
        if (needSave) scheduleSaveCache()
    }

    // 预取：列表滚到附近时先把直链解析好，点下去直接播
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "lxmusic") return
        val source = uri.host ?: return
        val songmid = uri.path?.removePrefix("/") ?: return
        val id = LxStreamId(source, songmid)
        if (!validateId(id)) return
        try {
            getOrFetchStreamUrl(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "warmUpStreamUrl failed for $uriString")
        }
    }

    private companion object {
        const val MAX_METADATA_ENTRIES = 800
        const val SAVE_DEBOUNCE_MS = 800L
    }
}

data class LxStreamId(
    val source: String,
    val songmid: String,
)
