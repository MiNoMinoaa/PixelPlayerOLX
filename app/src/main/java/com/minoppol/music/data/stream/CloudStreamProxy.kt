package com.minoppol.music.data.stream

import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Abstract base class for local HTTP proxy servers that stream cloud music audio.
 *
 * Subclasses define the route, ID type, validation, allowed hosts, and URL resolution.
 * The base class handles the full Ktor CIO server lifecycle, URL caching, and OkHttp
 * proxying with security checks via [CloudStreamSecurity].
 *
 * @param K The service-specific song identifier type.
 */
abstract class CloudStreamProxy<K : Any>(
    private val okHttpClient: OkHttpClient
) {

    protected abstract val allowedHostSuffixes: Set<String>
    protected open val allowAnyPublicStreamHost: Boolean = false
    protected abstract val cacheExpirationMs: Long
    protected abstract val proxyTag: String

    /** Route path registered with Ktor, e.g. "/navidrome/{songId}" */
    protected abstract val routePath: String
    /** The parameter name inside the route path, e.g. "songId" */
    protected abstract val routeParamName: String
    /** URI scheme this proxy handles, e.g. "navidrome" or "jellyfin" */
    protected abstract val uriScheme: String
    /** URL path prefix for proxy URLs, e.g. "/navidrome" or "/jellyfin" */
    protected abstract val routePrefix: String

    /** Parse the raw route parameter string into the typed ID, or null if invalid */
    protected abstract fun parseRouteParam(value: String): K?
    /** Validate whether the given ID is acceptable */
    protected abstract fun validateId(id: K): Boolean
    /** Convert the ID to a string for use in URLs */
    protected abstract fun formatIdForUrl(id: K): String
    /** Resolve the actual streaming URL for the given song ID */
    protected abstract suspend fun resolveStreamUrl(id: K): String?

    protected data class ResolvedStream(
        val url: String,
        val ttlMs: Long,
        val quality: String? = null,
        val contentToken: String? = null,
    )

    protected open suspend fun resolveStreamUrlMeta(id: K): ResolvedStream? =
        resolveStreamUrl(id)?.let { ResolvedStream(it, cacheExpirationMs) }

    protected open suspend fun cacheKeySuffix(id: K): String? = null

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private val streamingClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var sessionToken: String = ""

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()
    private val inFlightFetch = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<String?>>()

    @Volatile
    private var currentPlaybackKey: String? = null

    fun markCurrentPlaybackKey(key: String?) {
        currentPlaybackKey = key
        if (key == null) {
            inFlightFetch.values.forEach { it.cancel() }
            inFlightFetch.clear()
        }
    }

    fun markCurrentPlaybackUri(uriString: String?) {
        markCurrentPlaybackKey(uriString?.let { parsePlaybackUri(it)?.toString() })
    }

    private fun cancelStaleInFlight(keepKey: String) {
        val current = currentPlaybackKey
        for ((key, existing) in inFlightFetch) {
            if (key == keepKey) continue
            if (current != null && (key == current || key.startsWith("$current|"))) continue
            if (inFlightFetch.remove(key, existing)) existing.cancel()
        }
    }

    private data class CachedUrl(
        val url: String,
        val timestamp: Long,
        val expirationMs: Long,
        val quality: String? = null,
        val contentToken: String? = null,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expirationMs
    }

    data class ResolvedInfo(val quality: String?, val contentToken: String?)

    private fun trimUrlCache() {
        if (urlCache.size <= MAX_URL_CACHE_ENTRIES) return
        for ((key, cached) in urlCache.entries) {
            if (cached.isExpired() && urlCache.remove(key, cached) &&
                urlCache.size <= MAX_URL_CACHE_ENTRIES
            ) return
        }
        if (urlCache.size > MAX_URL_CACHE_ENTRIES) {
            val excess = urlCache.size - MAX_URL_CACHE_ENTRIES
            urlCache.entries.sortedBy { it.value.timestamp }
                .take(excess)
                .forEach { (key, cached) -> urlCache.remove(key, cached) }
        }
    }

    private companion object {
        const val STREAM_READ_TIMEOUT_SECONDS = 60L
        const val MAX_URL_CACHE_ENTRIES = 200
    }

    fun peekResolved(id: K): ResolvedInfo? {
        val exactKey = "$id"
        val idPrefix = "$id|"
        return urlCache.entries
            .filter { (key, cached) ->
                (key == exactKey || key.startsWith(idPrefix)) && !cached.isExpired()
            }
            .maxByOrNull { it.value.timestamp }
            ?.let { ResolvedInfo(it.value.quality, it.value.contentToken) }
    }

    fun parsePlaybackUri(uriString: String): K? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme != uriScheme) return null
        val rawId = extractIdFromUri(uri) ?: return null
        return parseRouteParam(rawId)?.takeIf { validateId(it) }
    }

    fun parseProxyUrl(urlString: String): K? {
        val uri = runCatching { Uri.parse(urlString) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (host != "127.0.0.1" && host != "localhost") return null
        val path = uri.path ?: return null
        if (!path.startsWith("$routePrefix/")) return null
        val param = path.removePrefix("$routePrefix/")
        return parseRouteParam(param)?.takeIf { validateId(it) }
    }

    fun isReady(): Boolean = actualPort > 0

    fun startIfNeeded() {
        if (isReady() || startJob?.isActive == true) return
        start()
    }

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return false
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        startIfNeeded()
        return awaitReady(timeoutMs)
    }

    fun getProxyUrl(id: K): String {
        if (actualPort == 0) return ""
        if (!validateId(id)) return ""
        return "http://127.0.0.1:$actualPort$routePrefix/${formatIdForUrl(id)}?t=$sessionToken"
    }

    /**
     * Parse a cloud URI (e.g. "navidrome://song-id" or "jellyfin://item-id") and return
     * the local proxy URL. Returns null if the URI doesn't match this proxy's scheme.
     * Subclasses can override to handle custom URI formats.
     */
    open fun resolveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return null
        val rawId = extractIdFromUri(uri) ?: return null
        val id = parseRouteParam(rawId) ?: return null
        if (!validateId(id)) return null
        return getProxyUrl(id)
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                sessionToken = generateSessionToken()
                val createdServer = createServer(0)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = createdServer.engine.resolvedConnectors().first().port
                Timber.d("$proxyTag started on port $actualPort")
            } catch (_: CancellationException) {
                Timber.d("$proxyTag start cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start $proxyTag")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        proxyScope.coroutineContext.cancelChildren()
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        sessionToken = ""
        urlCache.clear()
        Timber.d("$proxyTag stopped")
    }

    fun clearUrlCache() {
        urlCache.clear()
        for (deferred in inFlightFetch.values) deferred.cancel()
        inFlightFetch.clear()
    }

    fun invalidateUrlCacheForId(id: K) {
        val exactKey = "$id"
        val idPrefix = "$id|"
        val staleKeys = urlCache.keys.filter { it == exactKey || it.startsWith(idPrefix) }
        staleKeys.forEach { urlCache.remove(it) }
        inFlightFetch.keys.filter { it == exactKey || it.startsWith(idPrefix) }.forEach { key ->
            inFlightFetch.remove(key)?.cancel()
        }
    }

    /** Extract the raw ID string from a parsed URI. Override for custom URI layouts. */
    protected open fun extractIdFromUri(uri: Uri): String? = uri.host

    /**
     * Extra headers for the upstream stream request. Lets subclasses send auth tokens as
     * headers instead of baking them into the cached stream URL (where they'd end up in
     * URL caches and server access logs).
     */
    protected open fun upstreamHeaders(): Map<String, String> = emptyMap()

    private fun generateSessionToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val hex = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hex[v ushr 4]).append(hex[v and 0x0F])
        }
        return sb.toString()
    }

    /** Constant-time check of the per-session token supplied in the request. */
    private fun isAuthorized(provided: String?): Boolean {
        val expected = sessionToken
        if (expected.isEmpty() || provided.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    protected suspend fun getOrFetchStreamUrl(id: K): String? {
        val suffix = cacheKeySuffix(id)
        val cacheKey = if (suffix.isNullOrBlank()) "$id" else "$id|$suffix"
        urlCache[cacheKey]?.let { cached ->
            if (!cached.isExpired()) return cached.url
        }
        val running = inFlightFetch[cacheKey]
        if (running != null && running.isActive) {
            return runCatching { running.await() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
        }
        cancelStaleInFlight(cacheKey)
        val deferred = proxyScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            resolveStreamUrlMeta(id)?.also { meta ->
                urlCache[cacheKey] = CachedUrl(
                    meta.url,
                    System.currentTimeMillis(),
                    meta.ttlMs,
                    meta.quality,
                    meta.contentToken,
                )
                trimUrlCache()
            }?.url
        }
        val previous = inFlightFetch.putIfAbsent(cacheKey, deferred)
        if (previous != null) {
            deferred.cancel()
            return runCatching { previous.await() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
        }
        try {
            return deferred.await()
        } finally {
            inFlightFetch.remove(cacheKey)
        }
    }

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get(routePath) {
                    if (!isAuthorized(call.request.queryParameters["t"])) {
                        call.respond(HttpStatusCode.NotFound, "Not found")
                        return@get
                    }
                    val rawParam = call.parameters[routeParamName]
                    val id = rawParam?.let { parseRouteParam(it) }
                    if (id == null || !validateId(id)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@get
                    }

                    try {
                        val rangeValidation = CloudStreamSecurity.validateRangeHeader(
                            call.request.headers["Range"]
                        )
                        if (!rangeValidation.isValid) {
                            call.respond(
                                HttpStatusCode(416, "Range Not Satisfiable"),
                                "Invalid range header"
                            )
                            return@get
                        }

                        val streamUrl = getOrFetchStreamUrl(id)
                        if (streamUrl.isNullOrBlank()) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "No stream URL available")
                            return@get
                        }
                        val upstreamAllowed = if (allowAnyPublicStreamHost) {
                            CloudStreamSecurity.isPublicStreamUrl(streamUrl, allowHttp = true)
                        } else {
                            CloudStreamSecurity.isSafeRemoteStreamUrl(
                                url = streamUrl,
                                allowedHostSuffixes = allowedHostSuffixes,
                                allowHttpForAllowedHosts = true
                            )
                        }
                        if (!upstreamAllowed) {
                            call.respond(HttpStatusCode.BadGateway, "Rejected upstream stream URL")
                            return@get
                        }

                        val requestBuilder = Request.Builder().url(streamUrl)
                        rangeValidation.normalizedHeader?.let {
                            requestBuilder.header("Range", it)
                        }
                        upstreamHeaders().forEach { (name, value) ->
                            requestBuilder.header(name, value)
                        }

                        val response = withContext(Dispatchers.IO) {
                            streamingClient.newCall(requestBuilder.build()).execute()
                        }

                        response.use { upstream ->
                            Timber.tag("LxProxyDiag").d(
                                "upstream resp code=%d reqRange=%s len=%s contentRange=%s type=%s url=%s",
                                upstream.code,
                                rangeValidation.normalizedHeader ?: "(none)",
                                upstream.header("Content-Length") ?: "(none)",
                                upstream.header("Content-Range") ?: "(none)",
                                upstream.header("Content-Type") ?: "(none)",
                                streamUrl.take(120),
                            )
                            if (upstream.code != 200 && upstream.code != 206) {
                                call.respond(
                                    CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                                    "Upstream stream request failed"
                                )
                                return@get
                            }

                            val body = upstream.body
                            val contentTypeHeader = upstream.header("Content-Type")

                            if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                                call.respond(
                                    HttpStatusCode.BadGateway,
                                    "Unsupported stream content type"
                                )
                                return@get
                            }

                            val contentLength = upstream.header("Content-Length")
                            if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                                call.respond(
                                    HttpStatusCode(413, "Payload Too Large"),
                                    "Stream content too large"
                                )
                                return@get
                            }

                            val contentRange = upstream.header("Content-Range")
                            val acceptRanges = upstream.header("Accept-Ranges")
                            val responseContentType = contentTypeHeader
                                ?.substringBefore(';')
                                ?.trim()
                                ?.let { raw ->
                                    runCatching { ContentType.parse(raw) }.getOrNull()
                                }
                                ?: ContentType.Audio.Any

                            if (upstream.code == 206) {
                                call.response.status(HttpStatusCode.PartialContent)
                            } else {
                                call.response.status(HttpStatusCode.OK)
                            }
                            call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                            contentLength?.let { call.response.header("Content-Length", it) }
                            contentRange?.let { call.response.header("Content-Range", it) }

                            call.respondBytesWriter(contentType = responseContentType) {
                                withContext(Dispatchers.IO) {
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer)
                                                .also { bytesRead = it } != -1
                                        ) {
                                            writeFully(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")
                        ) {
                        } else {
                            Timber.w(e, "$proxyTag stream failed")
                        }
                    }
                }
            }
        }
    }
}
