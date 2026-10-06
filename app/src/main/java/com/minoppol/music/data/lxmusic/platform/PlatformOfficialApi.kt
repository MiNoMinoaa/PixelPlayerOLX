package com.minoppol.music.data.lxmusic.platform

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class PlatformResult {
    data class Success(val url: String, val level: String) : PlatformResult()
    object VipOrNoCopyright : PlatformResult()
    object NetworkError : PlatformResult()
}

private const val URL_CONNECT_TIMEOUT_SECONDS = 5L
private const val URL_READ_TIMEOUT_SECONDS = 6L
private const val URL_CALL_TIMEOUT_SECONDS = 15L

private fun OkHttpClient.fastClient(): OkHttpClient = newBuilder()
    .connectTimeout(URL_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(URL_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(URL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()

@Singleton
class TencentOfficialApi @Inject constructor(private val okHttpClient: OkHttpClient) {
    companion object {
        private const val TAG = "TencentOfficialApi"
        private const val ENDPOINT = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val GUID = buildString {
            repeat(10) { append(('0'..'9').random()) }
        }
    }

    suspend fun resolveUrl(songmid: String, mediaMid: String?): PlatformResult =
        withContext(Dispatchers.IO) {
            val media = mediaMid?.takeIf { it.isNotBlank() } ?: songmid
            val filename = "M500$media.mp3"
            val payload = com.google.gson.JsonObject().apply {
                add("comm", com.google.gson.JsonObject().apply {
                    addProperty("ct", 24)
                    addProperty("cv", 0)
                    addProperty("uin", 0)
                    addProperty("format", "json")
                })
                add("req_1", com.google.gson.JsonObject().apply {
                    addProperty("module", "vkey.GetVkeyServer")
                    addProperty("method", "CgiGetVkey")
                    add("param", com.google.gson.JsonObject().apply {
                        addProperty("guid", GUID)
                        add("songmid", com.google.gson.JsonArray().apply { add(songmid) })
                        add("songtype", com.google.gson.JsonArray().apply { add(0) })
                        addProperty("uin", "0")
                        addProperty("loginflag", 1)
                        addProperty("platform", "20")
                        add("filename", com.google.gson.JsonArray().apply { add(filename) })
                    })
                })
            }
            try {
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .post(
                        payload.toString()
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                    )
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://y.qq.com/")
                    .build()
                okHttpClient.fastClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: HTTP ${response.code} for $songmid")
                        return@withContext PlatformResult.NetworkError
                    }
                    val root = runCatching {
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    }.getOrNull() ?: return@withContext PlatformResult.NetworkError
                    val req1 = root.getAsJsonObject("req_1")?.takeIf { it.get("code")?.asInt == 0 }
                        ?: return@withContext PlatformResult.NetworkError
                    val data = req1.getAsJsonObject("data")
                        ?: return@withContext PlatformResult.NetworkError
                    val purl = data.getAsJsonArray("midurlinfo")?.firstOrNull()?.asJsonObject
                        ?.get("purl")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    if (purl.isBlank()) {
                        Timber.d("$TAG: empty purl for $songmid (vip/no-copyright)")
                        return@withContext PlatformResult.VipOrNoCopyright
                    }
                    val url = if (purl.startsWith("http")) purl else {
                        val sip = data.getAsJsonArray("sip")
                            ?.mapNotNull { it.takeUnless { e -> e.isJsonNull }?.asString }
                            ?.firstOrNull { it.isNotBlank() }
                        if (sip.isNullOrBlank()) null else sip.trimEnd('/') + "/" + purl.trimStart('/')
                    }
                    if (url.isNullOrBlank() || !url.startsWith("http")) {
                        return@withContext PlatformResult.NetworkError
                    }
                    PlatformResult.Success(url, "standard")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "$TAG: resolve failed for $songmid")
                PlatformResult.NetworkError
            }
        }
}

@Singleton
class KugouOfficialApi @Inject constructor(private val okHttpClient: OkHttpClient) {
    companion object {
        private const val TAG = "KugouOfficialApi"
        private const val ENDPOINT = "https://m.kugou.com/app/i/getSongInfo.php"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    suspend fun resolveUrl(hash: String): PlatformResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$ENDPOINT?cmd=playInfo&hash=$hash")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://m.kugou.com/")
                .build()
            okHttpClient.fastClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: HTTP ${response.code} for hash=$hash")
                    return@withContext PlatformResult.NetworkError
                }
                val root = runCatching {
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }.getOrNull() ?: return@withContext PlatformResult.NetworkError
                if (root.get("status")?.asInt != 1) {
                    Timber.d("$TAG: status!=1 for hash=$hash (vip/no-copyright)")
                    return@withContext PlatformResult.VipOrNoCopyright
                }
                val url = root.get("url")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                if (!url.startsWith("http")) {
                    Timber.d("$TAG: empty url for hash=$hash (vip/no-copyright)")
                    return@withContext PlatformResult.VipOrNoCopyright
                }
                PlatformResult.Success(url, "standard")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "$TAG: resolve failed for hash=$hash")
            PlatformResult.NetworkError
        }
    }
}
