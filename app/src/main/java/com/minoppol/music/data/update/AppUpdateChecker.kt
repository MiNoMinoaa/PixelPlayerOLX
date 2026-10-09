package com.minoppol.music.data.update

import android.content.Context
import com.google.gson.JsonParser
import com.minoppol.music.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

data class AppUpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String?,
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object Ignored : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

@Singleton
class AppUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val noRedirectClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ignoreVersion(version: String) {
        prefs.edit().putString(KEY_IGNORED_VERSION, version).apply()
    }

    private fun ignoredVersion(): String? = prefs.getString(KEY_IGNORED_VERSION, null)

    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        var info: AppUpdateInfo? = null
        try {
            info = fromReleaseApi()
        } catch (e: Exception) {
            Timber.d(e, "AppUpdateChecker: release api failed")
            lastError = e
        }
        if (info == null) {
            try {
                info = fromRedirect(RELEASE_DIRECT_URL)
            } catch (e: Exception) {
                Timber.d(e, "AppUpdateChecker: direct redirect failed")
                lastError = e
            }
        }
        if (info == null) {
            try {
                info = fromRedirect(MIRROR_GHFAST_URL)
            } catch (e: Exception) {
                Timber.d(e, "AppUpdateChecker: ghfast redirect failed")
                lastError = e
            }
        }
        if (info == null) {
            try {
                info = fromRedirect(MIRROR_GHPROXY_URL)
            } catch (e: Exception) {
                Timber.d(e, "AppUpdateChecker: ghproxy redirect failed")
                lastError = e
            }
        }

        when {
            info == null -> UpdateCheckResult.Error(lastError.toUserMessage())
            !isNewer(parseVersion(info.latestVersion), parseVersion(currentVersion)) -> UpdateCheckResult.UpToDate
            info.latestVersion == ignoredVersion() -> UpdateCheckResult.Ignored
            else -> UpdateCheckResult.UpdateAvailable(info)
        }
    }

    private fun fromReleaseApi(): AppUpdateInfo {
        val request = Request.Builder()
            .url(RELEASE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("release api ${response.code}")
            val root = JsonParser.parseString(response.body.string()).asJsonObject
            val tag = root.get("tag_name")?.takeUnless { it.isJsonNull }?.asString
                ?: throw IOException("missing tag_name")
            if (tag.isBlank()) throw IOException("blank tag")
            val htmlUrl = root.get("html_url")?.takeUnless { it.isJsonNull }?.asString ?: ""
            val notes = root.get("body")?.takeUnless { it.isJsonNull }?.asString ?: ""
            AppUpdateInfo(
                parseVersion(tag).joinToString("."),
                htmlUrl.ifBlank { RELEASE_PAGE_URL },
                notes.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun fromRedirect(url: String): AppUpdateInfo {
        val request = Request.Builder().url(url).head().build()
        return noRedirectClient.newCall(request).execute().use { response ->
            val tag = response.header("Location")
                ?.substringAfter("/tag/", "")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("missing redirect tag")
            AppUpdateInfo(
                parseVersion(tag).joinToString("."),
                RELEASE_PAGE_URL,
                null,
            )
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

    private fun Exception?.toUserMessage(): String {
        this ?: return context.getString(R.string.update_check_error_generic)
        return when (this) {
            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException,
            is SSLException -> context.getString(R.string.update_check_error_network)
            else -> context.getString(R.string.update_check_error_generic)
        }
    }

    private companion object {
        const val PREFS_NAME = "app_update_prefs"
        const val KEY_IGNORED_VERSION = "ignored_version"
        const val RELEASE_API_URL = "https://api.github.com/repos/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val RELEASE_DIRECT_URL = "https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val MIRROR_GHFAST_URL = "https://ghfast.top/https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val MIRROR_GHPROXY_URL = "https://gh-proxy.com/https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
        const val RELEASE_PAGE_URL = "https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest"
    }
}
