package com.minoppol.music.data.update

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String?,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val noRedirectClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val info = fromReleaseApi()
            ?: fromRedirect(RELEASE_DIRECT_URL)
            ?: fromRedirect(MIRROR_GHFAST_URL)
            ?: fromRedirect(MIRROR_GHPROXY_URL)
        info?.takeIf { isNewer(parseVersion(it.latestVersion), parseVersion(currentVersion)) }
    }

    private fun fromReleaseApi(): AppUpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JsonParser.parseString(response.body.string()).asJsonObject
                val tag = root.get("tag_name")?.takeUnless { it.isJsonNull }?.asString ?: return@use null
                if (tag.isBlank()) return@use null
                val htmlUrl = root.get("html_url")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val notes = root.get("body")?.takeUnless { it.isJsonNull }?.asString ?: ""
                AppUpdateInfo(
                    parseVersion(tag).joinToString("."),
                    htmlUrl.ifBlank { RELEASE_PAGE_URL },
                    notes.takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "AppUpdateChecker: release api failed")
            null
        }
    }

    private fun fromRedirect(url: String): AppUpdateInfo? {
        return try {
            val request = Request.Builder().url(url).head().build()
            noRedirectClient.newCall(request).execute().use { response ->
                val tag = response.header("Location")
                    ?.substringAfter("/tag/", "")
                    ?.takeIf { it.isNotBlank() } ?: return@use null
                AppUpdateInfo(
                    parseVersion(tag).joinToString("."),
                    RELEASE_PAGE_URL,
                    null,
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "AppUpdateChecker: redirect endpoint failed")
            null
        }
    }

    private fun parseVersion(value: String): List<Long> =
        value.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }

    private fun isNewer(latest: List<Long>, current: List<Long>): Boolean {
        val size = maxOf(latest.size, current.size)
        for (index in 0 until size) {
            val l = latest.getOrElse(index) { 0L }
            val c = current.getOrElse(index) { 0L }
            if (l != c) return l > c
        }
        return false
    }

    private companion object {
        const val RELEASE_API_URL = "https://api.github.com/repos/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val RELEASE_DIRECT_URL = "https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val MIRROR_GHFAST_URL = "https://ghfast.top/https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val MIRROR_GHPROXY_URL = "https://gh-proxy.com/https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val RELEASE_PAGE_URL = "https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
    }
}
