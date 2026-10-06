package com.minoppol.music.data.lxmusic.netease

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.minoppol.music.data.lxmusic.LxQualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeteaseOfficialApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: NeteaseCookieStore,
) {
    companion object {
        private const val TAG = "NeteaseOfficialApi"
        private const val URL_ENDPOINT = "https://music.163.com/weapi/song/enhance/player/url/v1"
        private const val ACCOUNT_ENDPOINT = "https://music.163.com/weapi/nuser/account/get"
        private const val PURCHASED_ENDPOINT = "https://music.163.com/api/digitalAlbum/purchased"
        private const val BASE_URL = "https://music.163.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val URL_CONNECT_TIMEOUT_SECONDS = 5L
        private const val URL_READ_TIMEOUT_SECONDS = 6L
        private const val URL_CALL_TIMEOUT_SECONDS = 15L
    }

    sealed class Result {
        data class Success(val url: String, val level: String) : Result()
        object VipOrNoCopyright : Result()
        object NetworkError : Result()
        object CookieInvalid : Result()
    }

    sealed class AccountResult {
        data class Ok(val uid: Long, val vipType: Int) : AccountResult()
        object CookieInvalid : AccountResult()
        object NetworkError : AccountResult()
    }

    sealed class PurchasedResult {
        data class Ok(val albumIds: Set<String>) : PurchasedResult()
        object CookieInvalid : PurchasedResult()
        object NetworkError : PurchasedResult()
    }

    data class LoginProfile(
        val uid: Long,
        val vipType: Int,
        val nickname: String,
        val avatarUrl: String?,
    )

    sealed class ProfileResult {
        data class Ok(val profile: LoginProfile) : ProfileResult()
        object CookieInvalid : ProfileResult()
        object NetworkError : ProfileResult()
    }

    sealed class WeapiResult {
        data class Ok(val json: JsonObject) : WeapiResult()
        object CookieInvalid : WeapiResult()
        data class Error(val apiCode: Int? = null, val httpCode: Int? = null) : WeapiResult()
    }

    suspend fun resolveUrl(songmid: String, lxQuality: String): Result = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie().orEmpty()

        val ladder = if (cookie.isBlank()) {
            listOf(LxQualities.Q128)
        } else {
            buildList {
                add(lxQuality)
                when (lxQuality) {
                    LxQualities.MASTER, LxQualities.ATMOS, LxQualities.FLAC24 -> {
                        add(LxQualities.FLAC); add(LxQualities.Q320); add(LxQualities.Q128)
                    }
                    LxQualities.FLAC -> {
                        add(LxQualities.Q320); add(LxQualities.Q128)
                    }
                    LxQualities.Q320 -> add(LxQualities.Q128)
                    else -> {}
                }
            }.distinct()
        }

        val client = okHttpClient.newBuilder()
            .connectTimeout(URL_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(URL_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(URL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        var lowestOutcome: UrlReq = UrlReq.Retryable
        for ((index, quality) in ladder.withIndex()) {
            val (level, encodeType) = mapQuality(quality)
            val isLowest = index == ladder.lastIndex

            for (attempt in 0..1) {
                val outcome = requestPlayUrl(client, cookie, songmid, level, encodeType)
                when (outcome) {
                    is UrlReq.OkUrl -> return@withContext Result.Success(outcome.url, outcome.level)
                    UrlReq.CookieInvalid -> return@withContext Result.CookieInvalid
                    is UrlReq.Empty -> {
                        if (isLowest) lowestOutcome = outcome
                        if (index == 0 && attempt == 0) {
                            Timber.d("$TAG: empty url at requested level, retry once level=$level for $songmid")
                            delay(300)
                            continue
                        }
                        break
                    }
                    UrlReq.Retryable -> {
                        if (isLowest) lowestOutcome = UrlReq.Retryable
                        if (attempt == 0) {
                            delay(300)
                            continue
                        }
                    }
                }
            }
        }
        val empty = lowestOutcome as? UrlReq.Empty
        return@withContext if (empty != null && (cookie.isNotBlank() || empty.fee == 1 || empty.fee == 4)) {
            Result.VipOrNoCopyright
        } else {
            Result.NetworkError
        }
    }

    private sealed class UrlReq {
        data class OkUrl(val url: String, val level: String) : UrlReq()
        data class Empty(val fee: Int?) : UrlReq()
        object Retryable : UrlReq()
        object CookieInvalid : UrlReq()
    }

    private suspend fun requestPlayUrl(
        client: OkHttpClient,
        cookie: String,
        songmid: String,
        level: String,
        encodeType: String,
    ): UrlReq = withContext(Dispatchers.IO) {
        val csrfToken = extractCsrfToken(cookie)
        val payload =
            """{"ids":"[$songmid]","level":"$level","encodeType":"$encodeType","csrf_token":"$csrfToken"}"""
        val (params, encSecKey) = NeteaseWeapiCrypto.encrypt(payload)
        try {
            val body = FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
                .build()
            val request = Request.Builder()
                .url(URL_ENDPOINT)
                .post(body)
                .applyCommonHeaders(cookie)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: HTTP ${response.code} for $songmid level=$level")
                    return@withContext UrlReq.Retryable
                }
                val json = response.body?.string().orEmpty()
                val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
                    ?: return@withContext UrlReq.Retryable
                when (root.get("code")?.asInt) {
                    200 -> { }
                    301 -> return@withContext UrlReq.CookieInvalid
                    else -> {
                        Timber.w("$TAG: API code=${root.get("code")?.asInt} for $songmid level=$level")
                        return@withContext UrlReq.Retryable
                    }
                }

                val data = root.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
                    ?: return@withContext UrlReq.Empty(null)
                val url = data.get("url")?.takeUnless { it.isJsonNull }?.asString
                if (url.isNullOrBlank() || !url.startsWith("http")) {
                    val fee = data.get("fee")?.takeUnless { it.isJsonNull }?.asInt
                    Timber.w("$TAG: empty url level=$level fee=$fee for $songmid")
                    return@withContext UrlReq.Empty(fee)
                }
                val resultLevel = data.get("level")?.takeUnless { it.isJsonNull }?.asString ?: level
                Timber.d("$TAG: resolved url level=$resultLevel (requested $level) for $songmid")
                UrlReq.OkUrl(url, resultLevel)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: request failed for $songmid level=$level")
            UrlReq.Retryable
        }
    }

    suspend fun fetchAccountInfo(): AccountResult = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()
        if (cookie.isNullOrBlank()) return@withContext AccountResult.CookieInvalid
        val csrfToken = extractCsrfToken(cookie)
        val payload = """{"csrf_token":"$csrfToken"}"""
        val (params, encSecKey) = NeteaseWeapiCrypto.encrypt(payload)

        try {
            val body = FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
                .build()
            val request = Request.Builder()
                .url(ACCOUNT_ENDPOINT)
                .post(body)
                .applyCommonHeaders(cookie)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext AccountResult.NetworkError
                val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                if (root.get("code")?.asInt != 200) return@withContext AccountResult.NetworkError
                val account: JsonObject = root.getAsJsonObject("account")
                    ?: return@withContext AccountResult.CookieInvalid
                val uid = account.get("id")?.asLong ?: return@withContext AccountResult.NetworkError
                val vipType = account.get("vipType")?.asInt ?: 0
                return@withContext AccountResult.Ok(uid, vipType)
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: fetchAccountInfo failed")
            AccountResult.NetworkError
        }
    }

    suspend fun fetchPurchasedAlbumIds(): PurchasedResult = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()
        if (cookie.isNullOrBlank()) return@withContext PurchasedResult.CookieInvalid

        try {
            val request = Request.Builder()
                .url("$PURCHASED_ENDPOINT?limit=500&offset=0")
                .get()
                .applyCommonHeaders(cookie)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 301) return@withContext PurchasedResult.CookieInvalid
                if (!response.isSuccessful) return@withContext PurchasedResult.NetworkError
                val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                if (root.get("code")?.asInt != 200) return@withContext PurchasedResult.NetworkError
                val ids = root.getAsJsonArray("digitalAlbums")
                    ?.mapNotNull { element ->
                        val obj = element.asJsonObject
                        obj.get("albumId")?.takeUnless { it.isJsonNull }?.let { idEl ->
                            when {
                                idEl.isJsonPrimitive -> idEl.asString
                                else -> null
                            }
                        } ?: obj.getAsJsonObject("album")?.get("id")?.asString
                    }
                    ?.toSet()
                    .orEmpty()
                return@withContext PurchasedResult.Ok(ids)
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: fetchPurchasedAlbumIds failed")
            PurchasedResult.NetworkError
        }
    }

    suspend fun fetchLoginProfile(): ProfileResult = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()
        if (cookie.isNullOrBlank()) return@withContext ProfileResult.CookieInvalid
        when (val res = postWeapi("/weapi/nuser/account/get", emptyMap())) {
            is WeapiResult.Ok -> {
                val root = res.json
                val account = root.getAsJsonObject("account")
                    ?: return@withContext ProfileResult.CookieInvalid
                val uid = account.get("id")?.takeIf { it.isJsonPrimitive }?.asLong
                    ?: return@withContext ProfileResult.NetworkError
                val vipType = account.get("vipType")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                val profile = root.getAsJsonObject("profile")
                val nickname = profile?.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val avatar = profile?.get("avatarUrl")?.takeIf { it.isJsonPrimitive }?.asString
                ProfileResult.Ok(LoginProfile(uid, vipType, nickname, avatar))
            }
            is WeapiResult.CookieInvalid -> ProfileResult.CookieInvalid
            is WeapiResult.Error -> ProfileResult.NetworkError
        }
    }

    suspend fun postWeapi(
        path: String,
        params: Map<String, Any?>,
        cookieOverride: String? = null,
    ): WeapiResult =
        postWeapiInternal(path, params, cookieOverride)

    private suspend fun postWeapiInternal(
        path: String,
        params: Map<String, Any?>,
        cookieOverride: String? = null,
    ): WeapiResult =
        withContext(Dispatchers.IO) {
            val cookie = (cookieOverride ?: cookieStore.getCookie()).orEmpty()
            if (cookieOverride == null && cookie.isBlank()) return@withContext WeapiResult.CookieInvalid
            val csrfToken = extractCsrfToken(cookie)
            val payload = JsonObject().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        null -> if (!has(key)) addProperty(key, null as String?)
                        is String -> addProperty(key, value)
                        is Number -> addProperty(key, value)
                        is Boolean -> addProperty(key, value)
                        else -> addProperty(key, value.toString())
                    }
                }
                if (!has("csrf_token")) addProperty("csrf_token", csrfToken)
            }
            val (paramsEnc, encSecKey) = NeteaseWeapiCrypto.encrypt(payload.toString())
            try {
                val body = FormBody.Builder()
                    .add("params", paramsEnc)
                    .add("encSecKey", encSecKey)
                    .build()
                val request = Request.Builder()
                    .url("$BASE_URL$path")
                    .post(body)
                    .applyCommonHeaders(cookie)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 301) return@withContext WeapiResult.CookieInvalid
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: weapi $path http=${response.code}")
                        return@withContext WeapiResult.Error(httpCode = response.code)
                    }
                    val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    val code = root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt
                    if (code == 301) return@withContext WeapiResult.CookieInvalid
                    if (code != 200) {
                        Timber.w("$TAG: weapi $path code=$code")
                        return@withContext WeapiResult.Error(apiCode = code)
                    }
                    WeapiResult.Ok(root)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "$TAG: postWeapi $path failed")
                WeapiResult.Error()
            }
        }

    private fun mapQuality(lxQuality: String): Pair<String, String> = when (lxQuality) {
        "128k" -> "standard" to "flac"
        "320k" -> "exhigh" to "flac"
        "flac" -> "lossless" to "aac"
        "flac24bit" -> "hires" to "flac"
        "atmos" -> "jyeffect" to "flac"
        "master" -> "jymaster" to "flac"
        else -> "exhigh" to "flac"
    }

    private fun extractCsrfToken(cookie: String): String {
        return Regex("_csrf=([^;]+)").find(cookie)?.groupValues?.get(1) ?: ""
    }

    private fun Request.Builder.applyCommonHeaders(cookie: String): Request.Builder {
        header("User-Agent", USER_AGENT)
        header("Accept", "application/json")
        header("Accept-Language", "zh-CN,zh;q=0.9")
        header("Referer", "https://music.163.com")
        header("Origin", "https://music.163.com")
        if (cookie.isNotBlank()) header("Cookie", cookie)
        return this
    }
}
