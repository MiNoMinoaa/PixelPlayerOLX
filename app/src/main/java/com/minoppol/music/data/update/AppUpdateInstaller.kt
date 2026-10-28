package com.minoppol.music.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import com.minoppol.music.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateInstallError { DOWNLOAD_FAILED, INVALID_APK, NOT_NEWER, INSTALL_FAILED }

sealed interface UpdateInstallStatus {
    data object Idle : UpdateInstallStatus
    data class Downloading(val version: String, val progress: Float?) : UpdateInstallStatus
    data class AwaitingInstallPermission(val version: String) : UpdateInstallStatus
    data class AwaitingConfirmation(val version: String, val confirmIntent: Intent) : UpdateInstallStatus
    data class Installing(val version: String) : UpdateInstallStatus
    data class Failed(val error: UpdateInstallError, val version: String?) : UpdateInstallStatus
    data object RequiresManualInstall : UpdateInstallStatus
}

/**
 * Downloads the published APK for a checked [AppUpdateInfo] and installs it through
 * [PackageInstaller]. A signature mismatch (or a debug build) is reported as
 * [UpdateInstallStatus.RequiresManualInstall] instead of attempting an install.
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
    baseClient: OkHttpClient,
) {
    private val _status = MutableStateFlow<UpdateInstallStatus>(UpdateInstallStatus.Idle)
    val status: StateFlow<UpdateInstallStatus> = _status.asStateFlow()

    private val downloadClient = baseClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var job: Job? = null

    /** The in-flight APK download; cancelled directly because OkHttp reads ignore coroutine cancellation. */
    @Volatile
    private var activeDownload: Call? = null

    private var downloadJob: Job? = null

    /**
     * The latest deletion of the updates directory. Cleanups chain onto each other and every
     * download waits for the last one, so a cleanup from an earlier cancel can't delete the files
     * of a retry. A fresh process starts with one: whatever a finished or abandoned update left
     * behind can go.
     */
    @Volatile
    private var apkCleanup: Job = scope.launch { updatesDir().deleteRecursively() }

    private fun scheduleApkCleanup() {
        val previous = apkCleanup
        apkCleanup = scope.launch(Dispatchers.IO) {
            previous.join()
            updatesDir().deleteRecursively()
        }
    }

    fun startDownload(info: AppUpdateInfo) {
        val current = _status.value
        if (current is UpdateInstallStatus.Downloading ||
            current is UpdateInstallStatus.Installing ||
            current is UpdateInstallStatus.AwaitingConfirmation ||
            current is UpdateInstallStatus.AwaitingInstallPermission
        ) return
        val apkUrl = info.apkUrl ?: return
        // Debug builds carry an applicationId suffix; the published APK would install beside them.
        if (context.packageName != PUBLISHED_PACKAGE) {
            _status.value = UpdateInstallStatus.RequiresManualInstall
            return
        }
        val version = info.latestVersion
        // A cancelled download may still be unwinding (even if a check ran since); let it
        // finish before this one reuses its files.
        val previousDownload = downloadJob
        job?.cancel()
        _status.value = UpdateInstallStatus.Downloading(version, progress = null)
        UpdateDownloadService.start(context)
        job = scope.launch {
            previousDownload?.join()
            val apk = try {
                download(apkUrl, info.apkSizeBytes, version)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // cancel() aborts the call, which surfaces here as an IOException; Idle stands.
                ensureActive()
                Timber.w(error, "Update download failed")
                _status.value = UpdateInstallStatus.Failed(UpdateInstallError.DOWNLOAD_FAILED, version)
                return@launch
            }
            val check = verify(apk)
            ensureActive()
            when (check) {
                ApkCheck.OK -> install(version, apk)
                ApkCheck.SIGNER_MISMATCH -> {
                    apk.delete()
                    _status.value = UpdateInstallStatus.RequiresManualInstall
                }
                ApkCheck.NOT_NEWER -> {
                    apk.delete()
                    _status.value = UpdateInstallStatus.Failed(UpdateInstallError.NOT_NEWER, version)
                }
                ApkCheck.INVALID -> {
                    apk.delete()
                    _status.value = UpdateInstallStatus.Failed(UpdateInstallError.INVALID_APK, version)
                }
            }
        }
        downloadJob = job
    }

    /**
     * Stops a download or an install still waiting for the "install unknown apps" grant. Once a
     * session is committed, the system prompt owns the decision and reports back on its own.
     */
    fun cancel() {
        val current = _status.value
        if (current !is UpdateInstallStatus.Downloading &&
            current !is UpdateInstallStatus.AwaitingInstallPermission
        ) return
        job?.cancel()
        activeDownload?.cancel()
        _status.value = UpdateInstallStatus.Idle
        scheduleApkCleanup()
    }

    /** Resumes an install that waited for the "install unknown apps" grant. */
    fun resumeInstall() {
        val waiting = _status.value as? UpdateInstallStatus.AwaitingInstallPermission ?: return
        val version = waiting.version
        val apk = downloadedApk(version)
        if (!apk.isFile) {
            _status.value = UpdateInstallStatus.Idle
            return
        }
        job = scope.launch { install(version, apk) }
    }

    /** Opens the system install prompt again after it was backgrounded into a notification. */
    fun openPendingConfirmation() {
        val waiting = _status.value as? UpdateInstallStatus.AwaitingConfirmation ?: return
        runCatching {
            context.startActivity(Intent(waiting.confirmIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun dismiss() {
        val current = _status.value
        if (current is UpdateInstallStatus.Failed || current is UpdateInstallStatus.RequiresManualInstall) {
            _status.value = UpdateInstallStatus.Idle
        }
    }

    internal fun onInstallStatus(status: Int, confirmIntent: Intent?, message: String?) {
        val version = when (val current = _status.value) {
            is UpdateInstallStatus.Installing -> current.version
            is UpdateInstallStatus.AwaitingConfirmation -> current.version
            else -> null
        }
        _status.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (version == null || confirmIntent == null) return
                UpdateInstallStatus.AwaitingConfirmation(version, confirmIntent)
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallStatus.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                version?.let { UpdateInstallStatus.Failed(UpdateInstallError.INSTALL_FAILED, it) }
                    ?: UpdateInstallStatus.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateInstallStatus.RequiresManualInstall
            else -> {
                Timber.w("Update install failed: status=$status message=$message")
                UpdateInstallStatus.Failed(UpdateInstallError.INSTALL_FAILED, version)
            }
        }
    }

    private suspend fun download(apkUrl: String, apkSizeBytes: Long?, version: String): File =
        withContext(Dispatchers.IO) {
            apkCleanup.join()
            val dir = updatesDir()
            dir.deleteRecursively()
            if (!dir.mkdirs()) throw IOException("Could not create $dir")
            val target = downloadedApk(version)
            val partial = File(dir, target.name + ".part")

            val request = Request.Builder().url(apkUrl).get().build()
            val call = downloadClient.newCall(request).also { activeDownload = it }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0 } ?: apkSizeBytes
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            var written = 0L
                            var lastReported = -1
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                written += count
                                if (total != null) {
                                    val percent = (written * 100 / total).toInt()
                                    if (percent != lastReported && isActive) {
                                        lastReported = percent
                                        _status.value = UpdateInstallStatus.Downloading(
                                            version,
                                            (written.toFloat() / total).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                activeDownload = null
            }
            if (!partial.renameTo(target)) throw IOException("Could not finalize $target")
            target
        }

    private enum class ApkCheck { OK, SIGNER_MISMATCH, NOT_NEWER, INVALID }

    private fun verify(apk: File): ApkCheck {
        val packageManager = context.packageManager
        val archive = archiveInfo(packageManager, apk) ?: return ApkCheck.INVALID
        if (archive.packageName != context.packageName) return ApkCheck.INVALID
        val installed = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) return ApkCheck.NOT_NEWER
        val installedSigners = installed.signingInfo?.signers().orEmpty()
        val archiveSigners = archive.signingInfo?.signers().orEmpty()
        // Without signer info, PackageInstaller still enforces the signature; let it decide.
        if (installedSigners.isEmpty() || archiveSigners.isEmpty()) return ApkCheck.OK
        return if (archiveSigners.any { it in installedSigners }) ApkCheck.OK else ApkCheck.SIGNER_MISMATCH
    }

    private fun archiveInfo(packageManager: PackageManager, apk: File) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apk.path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    /** Current signers plus the rotation history, which a rotated key still accepts. */
    private fun SigningInfo.signers(): List<Signature> =
        if (hasMultipleSigners()) apkContentsSigners.toList() else signingCertificateHistory.toList()

    private suspend fun install(version: String, apk: File) {
        if (!canRequestPackageInstalls()) {
            _status.value = UpdateInstallStatus.AwaitingInstallPermission(version)
            return
        }
        _status.value = UpdateInstallStatus.Installing(version)
        try {
            withContext(Dispatchers.IO) { commitSession(apk) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.w(error, "Could not start the update install session")
            _status.value = UpdateInstallStatus.Failed(UpdateInstallError.INSTALL_FAILED, version)
        }
    }

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            installer.abandonSession(sessionId)
            throw error
        }
    }

    private fun updatesDir() = File(context.cacheDir, UPDATES_DIR)

    private fun downloadedApk(version: String) =
        File(updatesDir(), "PixelPlayerOLX-$version.apk")

    private companion object {
        const val PUBLISHED_PACKAGE = "com.minoppol.music"
        const val UPDATES_DIR = "updates"
    }
}
