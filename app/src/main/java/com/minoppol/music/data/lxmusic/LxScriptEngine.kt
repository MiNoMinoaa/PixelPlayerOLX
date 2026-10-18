package com.minoppol.music.data.lxmusic

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LxMusicUrlEvent {
    data class Success(val url: String) : LxMusicUrlEvent
    data class ScriptRejected(val message: String) : LxMusicUrlEvent
    data object Unavailable : LxMusicUrlEvent
}

@Singleton
class LxScriptEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val neteaseCookieStore: com.minoppol.music.data.lxmusic.netease.NeteaseCookieStore,
    private val tencentCookieStore: com.minoppol.music.data.lxmusic.tencent.TencentCookieStore,
    private val kugouCookieStore: com.minoppol.music.data.lxmusic.kugou.KugouCookieStore,
    private val kuwoCookieStore: com.minoppol.music.data.lxmusic.kuwo.KuwoCookieStore,
) {
    private val gson = Gson()
    private val requestKeyCounter = AtomicLong(0)

    private val runtimes = ConcurrentHashMap<String, Runtime>()

    private val _updateAlerts = MutableSharedFlow<LxScriptUpdateAlert>(extraBufferCapacity = 8)
    val updateAlerts: SharedFlow<LxScriptUpdateAlert> = _updateAlerts.asSharedFlow()

    private val eventWaiters = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

    private class Runtime(
        val meta: LxScriptMeta,
        val key: String,
        val thread: HandlerThread,
        val handler: Handler,
    ) {
        @Volatile var context: QuickJSContext? = null
        @Volatile var capabilities: LxScriptCapabilities = LxScriptCapabilities()
        val networkCalls = ConcurrentHashMap<String, Call>()
        val initDeferred = CompletableDeferred<LxScriptCapabilities>()
    }


    // 每个脚本独立线程 + 独立 QuickJS 实例，一个卡了不拖累别的
    suspend fun loadScript(meta: LxScriptMeta): Result<LxScriptCapabilities> {
        runtimes[meta.assetPath]?.let { return Result.success(it.capabilities) }
        val thread = HandlerThread("LxScript-${meta.name}").apply { start() }
        val handler = Handler(thread.looper)
        val runtime = Runtime(meta, UUID.randomUUID().toString(), thread, handler)
        handler.post {
            try {
                Timber.d("LxScriptEngine[${meta.name}]: QuickJSLoader.init")
                QuickJSLoader.init()
                Timber.d("LxScriptEngine[${meta.name}]: create context")
                val ctx = QuickJSContext.create()
                ctx.setConsole(LxConsole(meta.name))
                runtime.context = ctx
                installNativeFunctions(ctx, runtime)

                Timber.d("LxScriptEngine[${meta.name}]: eval preload")
                val preload = context.assets.open(PRELOAD_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
                ctx.evaluate(preload, PRELOAD_ASSET)
                Timber.d("LxScriptEngine[${meta.name}]: lx_setup")
                ctx.globalObject.getJSFunction("lx_setup").call(
                    runtime.key,
                    "lx",
                    meta.name,
                    meta.description,
                    meta.version,
                    meta.author,
                    "",
                    readScriptWithCookie(meta),
                )
                Timber.d("LxScriptEngine[${meta.name}]: eval script body")
                ctx.evaluate(readScriptWithCookie(meta), scriptFileName(meta))
                Timber.d("LxScriptEngine[${meta.name}]: script body done, waiting for init event")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "LxScriptEngine: failed to load script ${meta.name}")
                runtime.initDeferred.completeExceptionally(e)
                runCatching { runtime.context?.close() }
                thread.quitSafely()
            }
        }
        val capsResult: Result<LxScriptCapabilities> = runCatching {
            withTimeoutOrNull(15_000) { runtime.initDeferred.await() }
                ?: throw LxScriptInitTimeoutException(meta.name)
        }
        return capsResult.fold(
            onSuccess = { caps ->
                Timber.d("LxScriptEngine[${meta.name}]: init done, sources=${caps.sources.keys}")
                runtimes[meta.assetPath] = runtime
                Result.success(caps)
            },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Timber.e(e, "LxScriptEngine: script ${meta.name} never reported capabilities")
                runtimes.remove(meta.assetPath)
                cancelRuntimeNetworkCalls(runtime)
                runCatching { runtime.context?.close() }
                runtime.thread.quitSafely()
                Result.failure(e)
            },
        )
    }

    fun unloadScript(assetPath: String) {
        val runtime = runtimes.remove(assetPath) ?: return
        runtime.handler.post {
            cancelRuntimeNetworkCalls(runtime)
            runCatching { runtime.context?.close() }
            runtime.thread.quitSafely()
        }
    }

    private fun cancelRuntimeNetworkCalls(runtime: Runtime) {
        runtime.networkCalls.values.forEach { runCatching { it.cancel() } }
        runtime.networkCalls.clear()
    }

    fun isLoaded(assetPath: String): Boolean = runtimes.containsKey(assetPath)

    fun loadedScripts(): Set<String> = runtimes.keys.toSet()

    fun isSourceSupported(assetPath: String, source: String): Boolean {
        return runtimes[assetPath]?.capabilities?.sources?.containsKey(source) == true
    }

    fun findScriptForSource(source: String): String? {
        return runtimes.entries.firstOrNull { (_, rt) ->
            rt.capabilities.sources.containsKey(source)
        }?.key
    }

    fun getCapabilities(assetPath: String): LxScriptCapabilities? {
        return runtimes[assetPath]?.capabilities
    }

    suspend fun requestMusicUrl(
        assetPath: String,
        source: String,
        songmid: String,
        quality: String,
        extraInfo: Map<String, String> = emptyMap(),
        timeoutMs: Long = EVENT_TIMEOUT_MS,
    ): String? = requestEventUrl(assetPath, "musicUrl", source, songmid, quality, extraInfo, timeoutMs)
        ?.let { parseEventResult(it) }?.also { url ->
            Timber.d("LxScriptEngine: musicUrl result url=${url?.take(80) ?: "null"}")
        }

    suspend fun requestMusicUrlDetailed(
        assetPath: String,
        source: String,
        songmid: String,
        quality: String,
        extraInfo: Map<String, String> = emptyMap(),
        timeoutMs: Long = EVENT_TIMEOUT_MS,
    ): LxMusicUrlEvent =
        when (val raw = requestEventUrl(assetPath, "musicUrl", source, songmid, quality, extraInfo, timeoutMs)) {
            null -> LxMusicUrlEvent.Unavailable
            else -> parseMusicUrlEvent(raw)
        }

    suspend fun requestPic(
        assetPath: String,
        source: String,
        songmid: String,
        extraInfo: Map<String, String> = emptyMap(),
    ): String? = requestEventUrl(assetPath, "pic", source, songmid, "", extraInfo)
        ?.let { parseEventResult(it) }

    suspend fun requestLyric(
        assetPath: String,
        source: String,
        songmid: String,
        extraInfo: Map<String, String> = emptyMap(),
    ): LxLyricResult? = requestEventUrl(assetPath, "lyric", source, songmid, "", extraInfo)
        ?.let { parseLyricResult(it) }?.also {
            Timber.d("LxScriptEngine: lyric result lyric=ok")
        }

    suspend fun requestSearch(
        assetPath: String,
        source: String,
        keyword: String,
        page: Int = 1,
    ): List<LxSong>? {
        val runtime = runtimes[assetPath] ?: run {
            Timber.w("LxScriptEngine: runtime not found for $assetPath")
            return null
        }
        val requestKey = nextKey()
        val deferred = CompletableDeferred<JsonElement>()
        eventWaiters[requestKey] = deferred
        runtime.handler.post {
            val info = JsonObject().apply {
                addProperty("name", keyword)
                addProperty("singer", "")
                addProperty("page", page)
                addProperty("type", "music")
            }
            val data = JsonObject().apply {
                addProperty("source", source)
                addProperty("action", "search")
                add("info", info)
            }
            val payload = JsonObject().apply {
                addProperty("requestKey", requestKey)
                add("data", data)
            }
            callIntoJs(runtime, "request", payload)
        }
        return try {
            withTimeoutOrNull(EVENT_TIMEOUT_MS) { deferred.await() }?.let { parseSearchResult(it) }
        } catch (e: CancellationException) {
            eventWaiters.remove(requestKey)
            throw e
        } finally {
            eventWaiters.remove(requestKey)
        }
    }


    private suspend fun requestEventUrl(
        assetPath: String,
        action: String,
        source: String,
        songmid: String,
        quality: String,
        extraInfo: Map<String, String>,
        timeoutMs: Long = EVENT_TIMEOUT_MS,
    ): JsonElement? {
        val runtime = runtimes[assetPath] ?: run {
            Timber.w("LxScriptEngine: runtime not found for $assetPath")
            return null
        }
        val requestKey = nextKey()
        val deferred = CompletableDeferred<JsonElement>()
        eventWaiters[requestKey] = deferred
        runtime.handler.post {
            val meta = JsonObject().apply {
                addProperty("songId", songmid)
                addProperty("albumName", extraInfo["albumName"] ?: "")
                extraInfo["pic"]?.let { addProperty("picUrl", it) }
                extraInfo["hash"]?.let { addProperty("hash", it) }
                extraInfo["copyrightId"]?.let { addProperty("copyrightId", it) }
                val skipKeys = setOf("name", "singer", "albumName", "interval", "pic", "hash", "copyrightId")
                for ((k, v) in extraInfo) {
                    if (k in skipKeys) continue
                    if (v.length > 2 && (v.startsWith("{") || v.startsWith("["))) {
                        runCatching { add(k, JsonParser.parseString(v)) }
                            .getOrElse { addProperty(k, v) }
                    } else {
                        addProperty(k, v)
                    }
                }
            }
            val musicInfo = JsonObject().apply {
                addProperty("id", songmid)
                addProperty("songmid", songmid)
                addProperty("name", extraInfo["name"] ?: "")
                addProperty("singer", extraInfo["singer"] ?: "")
                addProperty("source", source)
                addProperty("albumName", extraInfo["albumName"] ?: "")
                extraInfo["pic"]?.let { addProperty("img", it) }
                extraInfo["hash"]?.let { addProperty("hash", it) }
                extraInfo["copyrightId"]?.let { addProperty("copyrightId", it) }
                val intervalSec = extraInfo["interval"]?.toLongOrNull() ?: 0L
                addProperty("interval", formatInterval(intervalSec))
                val skipKeys = setOf("name", "singer", "albumName", "interval", "pic", "hash", "copyrightId")
                for ((k, v) in extraInfo) {
                    if (k in skipKeys) continue
                    if (v.length > 2 && (v.startsWith("{") || v.startsWith("["))) {
                        runCatching { add(k, JsonParser.parseString(v)) }
                            .getOrElse { addProperty(k, v) }
                    } else {
                        addProperty(k, v)
                    }
                }
                add("meta", meta)
            }
            val data = JsonObject().apply {
                addProperty("source", source)
                addProperty("action", action)
                val info = JsonObject().apply {
                    if (action == "musicUrl") addProperty("type", quality)
                    add("musicInfo", musicInfo)
                }
                add("info", info)
            }
            val payload = JsonObject().apply {
                addProperty("requestKey", requestKey)
                add("data", data)
            }
            callIntoJs(runtime, "request", payload)
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run {
                Timber.w("LxScriptEngine: timeout waiting for $action $source/$songmid")
                null
            }
        } catch (e: CancellationException) {
            eventWaiters.remove(requestKey)
            throw e
        } finally {
            eventWaiters.remove(requestKey)
        }
    }

    private fun parseEventResult(result: JsonElement): String? {
        if (!result.isJsonObject) return null
        val obj = result.asJsonObject
        val status = obj.get("status")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: return null
        if (!status) {
            val errorMessage = obj.get("errorMessage")?.takeIf { it.isJsonPrimitive }?.asString
            Timber.w("LxScriptEngine: event failed: $errorMessage")
            return null
        }
        val data = obj.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val url = data?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
        if (url.isNullOrBlank()) return null
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun parseMusicUrlEvent(result: JsonElement): LxMusicUrlEvent {
        if (!result.isJsonObject) return LxMusicUrlEvent.Unavailable
        val status = result.asJsonObject.get("status")?.takeIf { it.isJsonPrimitive }?.asBoolean
            ?: return LxMusicUrlEvent.Unavailable
        if (!status) {
            val message = result.asJsonObject.get("errorMessage")?.takeIf { it.isJsonPrimitive }?.asString
            Timber.w("LxScriptEngine: musicUrl rejected: $message")
            return message?.takeIf { it.isNotBlank() }?.let { LxMusicUrlEvent.ScriptRejected(it) }
                ?: LxMusicUrlEvent.Unavailable
        }
        return parseEventResult(result)?.let { LxMusicUrlEvent.Success(it) }
            ?: LxMusicUrlEvent.Unavailable
    }

    private fun parseLyricResult(result: JsonElement): LxLyricResult? {
        if (!result.isJsonObject) return null
        val obj = result.asJsonObject
        val status = obj.get("status")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: return null
        if (!status) {
            val errorMessage = obj.get("errorMessage")?.takeIf { it.isJsonPrimitive }?.asString
            Timber.w("LxScriptEngine: lyric failed: $errorMessage")
            return null
        }
        val data = obj.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val lyric = data.get("lyric")?.takeIf { it.isJsonPrimitive }?.asString
        if (lyric.isNullOrBlank()) return null
        return LxLyricResult(
            lyric = lyric,
            tlyric = data.get("tlyric")?.takeIf { it.isJsonPrimitive }?.asString,
            rlyric = data.get("rlyric")?.takeIf { it.isJsonPrimitive }?.asString,
            lxlyric = data.get("lxlyric")?.takeIf { it.isJsonPrimitive }?.asString,
        )
    }

    private fun parseSearchResult(result: JsonElement): List<LxSong>? {
        if (!result.isJsonObject) return null
        val obj = result.asJsonObject
        val status = obj.get("status")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: return null
        if (!status) return null
        val data = obj.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val list = data?.get("list")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        val knownKeys = setOf(
            "source", "songmid", "name", "singer", "albumName",
            "interval", "pic", "lrc", "qualitys", "hash", "copyrightId",
        )
        return list.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val item = e.asJsonObject
            val songmid = item.get("songmid")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return@mapNotNull null
            val extraFields = item.entrySet()
                .filter { it.key !in knownKeys && it.value.isJsonPrimitive }
                .associate { it.key to it.value.asString }
            LxSong(
                source = item.get("source")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                songmid = songmid,
                name = item.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                singer = item.get("singer")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                albumName = item.get("albumName")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                interval = parseIntervalSeconds(item.get("interval")),
                pic = item.get("pic")?.takeIf { it.isJsonPrimitive }?.asString,
                lrc = item.get("lrc")?.takeIf { it.isJsonPrimitive }?.asString,
                qualitys = item.get("qualitys")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { q -> q.takeIf { it.isJsonPrimitive }?.asString } ?: emptyList(),
                hash = item.get("hash")?.takeIf { it.isJsonPrimitive }?.asString,
                copyrightId = item.get("copyrightId")?.takeIf { it.isJsonPrimitive }?.asString,
                extraFields = extraFields,
            )
        }
    }

    private fun parseIntervalSeconds(el: JsonElement?): Long {
        val primitive = el?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return 0L
        if (primitive.isNumber) return primitive.asLong
        val str = primitive.asString
        str.toLongOrNull()?.let { return it }
        val parts = str.split(":")
        return if (parts.size == 2) {
            (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
        } else 0L
    }

    // ---------------------------------------------------------------------
    // ---------------------------------------------------------------------

    // 脚本注入 http 请求、本地缓存、定时器
    private fun installNativeFunctions(ctx: QuickJSContext, runtime: Runtime) {
        val global = ctx.globalObject

        global.setProperty("__lx_native_call__", JSCallFunction { args ->
            handleNativeCall(runtime, args)
            null
        })

        // __lx_native_call__set_timeout(id, ms)
        global.setProperty("__lx_native_call__set_timeout", JSCallFunction { args ->
            val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@JSCallFunction null
            val delayMs = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            runtime.handler.postDelayed(
                { callIntoJs(runtime, "__set_timeout__", id) },
                delayMs.coerceIn(0L, Int.MAX_VALUE.toLong()),
            )
            null
        })

        // utils_str2b64(str) -> base64
        global.setProperty("__lx_native_call__utils_str2b64", JSCallFunction { args ->
            val s = args.getOrNull(0) as? String ?: return@JSCallFunction ""
            android.util.Base64.encodeToString(s.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        })

        global.setProperty("__lx_native_call__utils_b642buf", JSCallFunction { args ->
            val b64 = args.getOrNull(0) as? String ?: return@JSCallFunction "[]"
            val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.NO_WRAP) }.getOrNull()
                ?: return@JSCallFunction "[]"
            gson.toJson(bytes.toList())
        })

        global.setProperty("__lx_native_call__utils_str2md5", JSCallFunction { args ->
            val s = args.getOrNull(0) as? String ?: return@JSCallFunction ""
            val decoded = java.net.URLDecoder.decode(s, "UTF-8")
            val digest = MessageDigest.getInstance("MD5")
                .digest(decoded.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        })

        // utils_aes_encrypt(dataB64, keyB64, ivB64, mode) -> base64
        global.setProperty("__lx_native_call__utils_aes_encrypt", JSCallFunction { args ->
            runCatching {
                val data = decodeB64(args.getOrNull(0) as? String ?: return@JSCallFunction "")
                val key = decodeB64(args.getOrNull(1) as? String ?: return@JSCallFunction "")
                val iv = decodeB64(args.getOrNull(2) as? String ?: "")
                val mode = args.getOrNull(3) as? String ?: "AES/CBC/PKCS7Padding"
                val cipher = javax.crypto.Cipher.getInstance(mode)
                val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
                if (iv.isEmpty()) {
                    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
                } else {
                    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.IvParameterSpec(iv))
                }
                val out = cipher.doFinal(data)
                android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
            }.getOrElse {
                Timber.w(it, "LxScriptEngine: aes encrypt failed")
                ""
            }
        })

        global.setProperty("__lx_native_call__utils_rsa_encrypt", JSCallFunction { args ->
            runCatching {
                val data = decodeB64(args.getOrNull(0) as? String ?: return@JSCallFunction "")
                val keyB64 = args.getOrNull(1) as? String ?: return@JSCallFunction ""
                val padding = args.getOrNull(2) as? String ?: "RSA/ECB/NoPadding"
                val pem = buildString {
                    append("-----BEGIN PUBLIC KEY-----\n")
                    val wrapped = keyB64.chunked(64)
                    for (i in wrapped.indices) {
                        if (i > 0) append('\n')
                        append(wrapped[i])
                    }
                    append("\n-----END PUBLIC KEY-----")
                }
                val keyFactory = java.security.KeyFactory.getInstance("RSA")
                val publicKey = keyFactory.generatePublic(
                    java.security.spec.X509EncodedKeySpec(
                        java.util.Base64.getMimeDecoder().decode(pem)
                    )
                )
                val cipher = javax.crypto.Cipher.getInstance(padding)
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey)
                val out = cipher.doFinal(data)
                android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
            }.getOrElse {
                Timber.w(it, "LxScriptEngine: rsa encrypt failed")
                ""
            }
        })
    }

    private fun handleNativeCall(runtime: Runtime, args: Array<out Any?>) {
        val key = args.getOrNull(0) as? String
        if (key != runtime.key) return
        val action = args.getOrNull(1) as? String ?: return
        val dataJson = args.getOrNull(2) as? String
        when (action) {
            "init" -> handleInitEvent(runtime, dataJson)
            "request" -> handleNetworkRequest(runtime, dataJson)
            "cancelRequest" -> handleCancelRequest(dataJson)
            "response" -> handleEventResponse(dataJson)
            "showUpdateAlert" -> {
                val json = dataJson?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                val log = json?.get("log")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val updateUrl = json?.get("updateUrl")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                Timber.d("LxScriptEngine[${runtime.meta.name}]: update alert: ${redactSensitive(log)}")
                _updateAlerts.tryEmit(
                    LxScriptUpdateAlert(
                        scriptName = runtime.meta.name,
                        version = runtime.meta.version,
                        updateUrl = updateUrl,
                        log = log,
                        assetPath = runtime.meta.assetPath,
                        isImported = runtime.meta.isImported,
                    )
                )
            }
            "log" -> Timber.d("LxScriptEngine[${runtime.meta.name}]: ${redactSensitive(dataJson)}")
            else -> Timber.d("LxScriptEngine[${runtime.meta.name}]: unknown action $action")
        }
    }

    // 脚本支持哪些音源、每个音源有哪些动作和音质
    private fun handleInitEvent(runtime: Runtime, dataJson: String?) {
        if (runtime.capabilities.sources.isNotEmpty()) return
        if (runtime.initDeferred.isCompleted) return
        val info = dataJson
            ?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("info")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: run {
                runtime.initDeferred.complete(LxScriptCapabilities())
                return
            }
        val sources = info.get("sources")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: run {
                runtime.initDeferred.complete(LxScriptCapabilities())
                return
            }
        val sourceMap = LinkedHashMap<String, LxSourceInfo>()
        for ((source, value) in sources.entrySet()) {
            val obj = value.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val actions = obj.get("actions")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: emptyList()
            val qualitys = obj.get("qualitys")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: emptyList()
            val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "music"
            sourceMap[source] = LxSourceInfo(source, type, actions, qualitys)
        }
        val caps = LxScriptCapabilities(sourceMap)
        runtime.capabilities = caps
        runtime.initDeferred.complete(caps)
        Timber.d("LxScriptEngine[${runtime.meta.name}]: inited sources=${sourceMap.keys}")
    }

    // JS 里的 http 请求都桥到 OkHttp：支持 header/form/二进制/单独超时，回包再丢回 JS
    private fun handleNetworkRequest(runtime: Runtime, dataJson: String?) {
        val obj = dataJson
            ?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return
        val requestKey = obj.get("requestKey")?.takeIf { it.isJsonPrimitive }?.asString ?: return
        val url = obj.get("url")?.takeIf { it.isJsonPrimitive }?.asString ?: return
        val options = obj.get("options")?.takeIf { it.isJsonObject }?.asJsonObject

        val builder = Request.Builder().url(url)
        val headersObj = options?.get("headers")?.takeIf { it.isJsonObject }?.asJsonObject
        var hasUserAgent = false
        var hasAccept = false
        headersObj?.entrySet()?.forEach { (name, value) ->
            if (value.isJsonPrimitive) {
                if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                if (name.equals("Accept", ignoreCase = true)) hasAccept = true
                builder.header(name, value.asString)
            }
        }
        if (!hasUserAgent) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36",
            )
        }
        if (!hasAccept) {
            builder.header("Accept", "application/json")
        }
        val method = options?.get("method")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase() ?: "GET"
        if (method != "GET" && method != "HEAD") {
            val bodyPrimitive = options?.get("body")?.takeIf { it.isJsonPrimitive }?.asString
            val bodyObject = options?.get("body")?.takeIf { it.isJsonObject || it.isJsonArray }
            val formObj = options?.get("form")?.takeIf { it.isJsonObject }?.asJsonObject
            val formDataObj = options?.get("formData")?.takeIf { it.isJsonObject }?.asJsonObject
            val contentType = options?.get("headers")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("Content-Type")?.takeIf { it.isJsonPrimitive }?.asString
            val requestBody = when {
                bodyPrimitive != null -> bodyPrimitive.toRequestBody(contentType?.toMediaTypeOrNull())
                bodyObject != null -> bodyObject.toString().toRequestBody(contentType?.toMediaTypeOrNull())
                formDataObj != null -> {
                    builder.removeHeader("Content-Type")
                    val multipartBody = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                    for ((k, v) in formDataObj.entrySet()) {
                        if (v.isJsonPrimitive) multipartBody.addFormDataPart(k, v.asString)
                    }
                    multipartBody.build()
                }
                formObj != null -> {
                    val formBody = okhttp3.FormBody.Builder()
                    for ((k, v) in formObj.entrySet()) {
                        if (v.isJsonPrimitive) formBody.add(k, v.asString)
                    }
                    formBody.build()
                }
                else -> "".toRequestBody(null)
            }
            builder.method(method, requestBody)
        } else {
            builder.method(method, null)
        }
        val binary = options?.get("binary")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        val effectiveTimeoutMs = options?.get("timeout")?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() }
            ?.takeIf { it > 0L }
            ?: DEFAULT_SCRIPT_REQUEST_TIMEOUT_MS
        val okHttpClient = if (effectiveTimeoutMs == DEFAULT_SCRIPT_REQUEST_TIMEOUT_MS) {
            this.okHttpClient
        } else {
            this.okHttpClient.newBuilder()
                .callTimeout(effectiveTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
        }

        val request = builder.build()
        val call = okHttpClient.newCall(request)
        runtime.networkCalls[requestKey] = call
        try {
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    runtime.networkCalls.remove(requestKey)
                    val response = JsonObject().apply {
                        addProperty("requestKey", requestKey)
                        add("error", JsonParser.parseString(gson.toJson(e.message ?: e.javaClass.simpleName)))
                    }
                    runtime.handler.post { callIntoJs(runtime, "response", response) }
                    Timber.d("LxScriptEngine[${runtime.meta.name}]: request failed url=${redactUrl(url)}")
                }

                override fun onResponse(call: Call, response: Response) {
                    runtime.networkCalls.remove(requestKey)
                    val bodyElement = response.body?.let { body ->
                        if (binary) {
                            val bytes = body.bytes()
                            gson.toJsonTree(bytes.toList())
                        } else {
                            val text = body.string()
                            parseBodyToElement(text)
                        }
                    } ?: JsonParser.parseString("null")
                    val headers = JsonObject()
                    response.headers.forEach { (name, value) -> headers.addProperty(name, value) }
                    val responseObj = JsonObject().apply {
                        addProperty("requestKey", requestKey)
                        add("error", JsonParser.parseString("null"))
                        val resp = JsonObject().apply {
                            addProperty("statusCode", response.code)
                            addProperty("statusMessage", response.message)
                            add("headers", headers)
                            add("body", bodyElement)
                        }
                        add("response", resp)
                    }
                    response.close()
                    runtime.handler.post { callIntoJs(runtime, "response", responseObj) }
                }
            })
        } catch (e: Exception) {
            runtime.networkCalls.remove(requestKey)
            val response = JsonObject().apply {
                addProperty("requestKey", requestKey)
                add("error", JsonParser.parseString(gson.toJson(e.message ?: "request failed")))
            }
            callIntoJs(runtime, "response", response)
            Timber.d("LxScriptEngine[${runtime.meta.name}]: request build failed url=${redactUrl(url)}")
        }
    }

    private fun parseBodyToElement(text: String): JsonElement {
        return runCatching { JsonParser.parseString(text) }.getOrElse { JsonParser.parseString(gson.toJson(text)) }
    }

    private fun handleCancelRequest(dataJson: String?) {
        val requestKey = dataJson?.trim('"') ?: return
        runtimes.values.forEach { runtime ->
            runtime.networkCalls.remove(requestKey)?.cancel()
        }
    }

    private fun handleEventResponse(dataJson: String?) {
        val obj = dataJson
            ?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return
        val requestKey = obj.get("requestKey")?.takeIf { it.isJsonPrimitive }?.asString ?: return
        eventWaiters.remove(requestKey)?.complete(obj)
    }


    private fun callIntoJs(runtime: Runtime, action: String, data: Any?) {
        val ctx = runtime.context ?: return
        runCatching {
            val fn = ctx.globalObject.getJSFunction("__lx_native__") ?: return
            when (data) {
                null -> fn.call(runtime.key, action)
                is Int -> fn.call(runtime.key, action, data)
                is String -> fn.call(runtime.key, action, data)
                else -> fn.call(runtime.key, action, gson.toJson(data))
            }
        }.onFailure { e ->
            Timber.e(e, "LxScriptEngine[${runtime.meta.name}]: callIntoJs($action) failed")
        }
    }

    private fun readScriptWithCookie(meta: LxScriptMeta): String {
        val entries = listOf(
            "@wy_cookie" to neteaseCookieStore.getCookie(),
            "@tx_cookie" to tencentCookieStore.getCookie(),
            "@kg_cookie" to kugouCookieStore.getCookie(),
            "@kw_cookie" to kuwoCookieStore.getCookie(),
        ).mapNotNull { (marker, cookie) ->
            cookie?.takeIf { it.isNotBlank() }?.let { marker to it }
        }
        return entries.fold(readScriptContent(meta.assetPath)) { raw, (marker, cookie) ->
            injectCookie(raw, marker, cookie)
        }.removePrefix("\uFEFF")
    }

    private fun scriptFileName(meta: LxScriptMeta): String =
        "lx://${meta.name}@${meta.version.ifBlank { "unknown" }}.js"

    private fun injectCookie(raw: String, marker: String, cookie: String): String {
        val line = " * $marker $cookie"
        val headerMatch = Regex("^/\\*!(?:.|\\n)+?\\*/").find(raw)
        val header = headerMatch?.value
        return when {
            header == null -> "/*!\n$line\n */\n$raw"
            header.lines().any { it.contains(marker) } ->
                header.lines().joinToString("\n") {
                    if (it.contains(marker)) line else it
                } + raw.removePrefix(header)
            else -> raw.replaceFirst("*/", "$line\n */")
        }
    }

    // 同路径本地文件优先于 assets，调试脚本直接推文件覆盖，不用重新打包
    private fun readScriptContent(path: String): String {
        return if (java.io.File(path).exists()) {
            java.io.File(path).readText()
        } else {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun formatInterval(seconds: Long): String {
        if (seconds <= 0) return ""
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    private fun nextKey(): String {
        val n = requestKeyCounter.incrementAndGet()
        return "k$n-${UUID.randomUUID().toString().substring(0, 8)}"
    }

    private fun decodeB64(s: String): ByteArray = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    private class LxConsole(private val name: String) : QuickJSContext.Console {
        override fun log(message: String) { Timber.d("JS[$name] log: ${redactSensitive(message)}") }
        override fun info(message: String) { Timber.d("JS[$name] info: ${redactSensitive(message)}") }
        override fun warn(message: String) { Timber.w("JS[$name] warn: ${redactSensitive(message)}") }
        override fun error(message: String) { Timber.e("JS[$name] error: ${redactSensitive(message)}") }
    }

    private companion object {
        const val PRELOAD_ASSET = "lxmusic/user-api-preload.js"
        const val EVENT_TIMEOUT_MS = 30_000L
        const val DEFAULT_SCRIPT_REQUEST_TIMEOUT_MS = 13_000L
        const val REDACTED = "[redacted]"

        val cookieMarkers = listOf(
            "@wy_cookie", "@tx_cookie", "@kg_cookie", "@kw_cookie",
            "MUSIC_U", "__remember_me", "os", "NMTID", "WEVNSM",
        )

        fun redactSensitive(text: String?): String {
            if (text.isNullOrEmpty()) return "null"
            var out: String = text
            for (marker in cookieMarkers) {
                out = Regex(Regex.escape(marker) + "[=:\\s]+\\S+").replace(out) { "${marker}=$REDACTED" }
            }
            return out
        }

        fun redactUrl(url: String): String {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd < 0) return url
            val pathStart = url.indexOf('/', schemeEnd + 3)
            if (pathStart < 0) return url.substring(0, schemeEnd + 3) + REDACTED
            val queryStart = url.indexOf('?', pathStart)
            return if (queryStart < 0) {
                url.substring(0, pathStart) + REDACTED
            } else {
                url.substring(0, pathStart) + REDACTED + url.substring(queryStart)
            }
        }
    }
}

class LxScriptInitTimeoutException(scriptName: String) :
    IllegalStateException("Script $scriptName did not report capabilities in time")
