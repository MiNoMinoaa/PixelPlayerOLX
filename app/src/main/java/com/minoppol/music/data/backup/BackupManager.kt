package com.minoppol.music.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import com.minoppol.music.data.backup.format.BackupCrypto
import com.minoppol.music.data.backup.format.BackupEncryptedException
import com.minoppol.music.data.backup.format.BackupFormatDetector
import com.minoppol.music.data.backup.format.BackupReader
import com.minoppol.music.data.backup.format.BackupWrongPassphraseException
import com.minoppol.music.data.backup.format.BackupWriter
import com.minoppol.music.data.backup.history.BackupHistoryRepository
import com.minoppol.music.data.backup.model.BackupHistoryEntry
import com.minoppol.music.data.backup.model.BackupManifest
import com.minoppol.music.data.backup.model.BackupOperationType
import com.minoppol.music.data.backup.model.BackupSection
import com.minoppol.music.data.backup.model.BackupTransferProgressUpdate
import com.minoppol.music.data.backup.model.BackupValidationResult
import com.minoppol.music.data.backup.model.DeviceInfo
import com.minoppol.music.data.backup.model.RestorePlan
import com.minoppol.music.data.backup.model.RestoreResult
import com.minoppol.music.data.backup.module.BackupModuleHandler
import com.minoppol.music.data.backup.restore.RestoreExecutor
import com.minoppol.music.data.backup.restore.RestorePlanner
import com.minoppol.music.data.backup.validation.BackupFileValidator
import com.minoppol.music.data.backup.validation.ValidationPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupWriter: BackupWriter,
    private val backupReader: BackupReader,
    private val restorePlanner: RestorePlanner,
    private val restoreExecutor: RestoreExecutor,
    private val validationPipeline: ValidationPipeline,
    private val backupHistoryRepository: BackupHistoryRepository,
    private val handlers: Map<BackupSection, @JvmSuppressWildcards BackupModuleHandler>
) {
    /**
     * Decrypted copies of encrypted backups, keyed by the original URI string.
     * Populated by [inspectBackup]; consumed and cleaned up by [restore] or
     * [discardDecryptedBackup]. App-private cache only.
     */
    private val decryptedBackups = LinkedHashMap<String, DecryptedBackupHandle>()
    private val tempCounter = java.util.concurrent.atomic.AtomicLong(0)

    private class DecryptedBackupHandle(val file: File) {
        private val refs = java.util.concurrent.atomic.AtomicInteger(1)

        fun release() {
            if (refs.decrementAndGet() <= 0) {
                runCatching { file.delete() }
            }
        }
    }

    private val decryptedDir: File
        get() = File(context.cacheDir, "backup_dec").apply { mkdirs() }

    suspend fun export(
        uri: Uri,
        sections: Set<BackupSection>,
        passphrase: String? = null,
        onProgress: (BackupTransferProgressUpdate) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val selectedSections = sections.toList()
            val totalSteps = selectedSections.size + 3
            var step = 0

            reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                "Preparing backup", "Building your selected backup sections.")

            val modulePayloads = mutableMapOf<String, String>()
            selectedSections.forEach { section ->
                reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                    "Collecting ${section.label}", section.description, section)
                val handler = handlers[section]
                    ?: throw IllegalStateException("No handler for module ${section.key}")
                modulePayloads[section.key] = handler.export()
            }

            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (_: Exception) { null }

            val manifest = BackupManifest(
                schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
                appVersion = packageInfo?.versionName ?: "unknown",
                appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.longVersionCode?.toInt() ?: 0
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo?.versionCode ?: 0
                },
                createdAt = System.currentTimeMillis(),
                deviceInfo = DeviceInfo(
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.SDK_INT
                )
            )

            reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                "Packaging backup", "Creating .pxpl archive.")

            backupWriter.write(uri, manifest, modulePayloads, passphrase = passphrase).getOrThrow()

            reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                "Backup complete", "Your PixelPlayerOLX backup was created successfully.")
        }
    }

    /** True when the file at [uri] is an encrypted .pxpl archive. */
    suspend fun isBackupEncrypted(uri: Uri): Boolean =
        backupReader.detectFormat(uri).getOrNull() == BackupFormatDetector.Format.PXPL_V4_ENCRYPTED

    /**
     * Inspects a backup file and returns a RestorePlan (without actually restoring).
     *
     * Encrypted backups need a [passphrase]; without one this fails with
     * [BackupEncryptedException], and with a wrong one with
     * [BackupWrongPassphraseException]. The decrypted copy is kept in app
     * cache so the follow-up [restore] can run without re-entering the
     * passphrase; call [discardDecryptedBackup] if the user cancels instead.
     */
    suspend fun inspectBackup(uri: Uri, passphrase: String? = null): Result<RestorePlan> = withContext(Dispatchers.IO) {
        runCatching {
            val originalUri = uri
            @Suppress("NAME_SHADOWING")
            val uri = resolveReadableUri(uri, passphrase)
            val fileValidation = validationPipeline.validateFile(uri)
            val warnings = mutableListOf<String>()
            if (fileValidation is BackupValidationResult.Invalid && fileValidation.fatalErrors.isNotEmpty()) {
                throw IllegalArgumentException(fileValidation.fatalErrors.first().message)
            }
            if (fileValidation is BackupValidationResult.Invalid) {
                warnings.addAll(fileValidation.warnings.map { it.message })
            }

            val plan = restorePlanner.buildRestorePlan(uri).getOrThrow()

            val manifestValidation = validationPipeline.validateManifest(plan.manifest)
            warnings.addAll(plan.warnings)
            if (manifestValidation is BackupValidationResult.Invalid) {
                if (manifestValidation.fatalErrors.isNotEmpty()) {
                    throw IllegalArgumentException(manifestValidation.fatalErrors.first().message)
                }
                warnings.addAll(manifestValidation.warnings.map { it.message })
            }

            plan.availableModules.toList().sortedBy { it.key }.forEach { section ->
                val moduleInfo = plan.manifest.modules[section.key]
                if (moduleInfo != null && moduleInfo.sizeBytes > BackupReader.MAX_MODULE_PAYLOAD_BYTES) {
                    warnings.add(
                        "${section.label}: payload is ${moduleInfo.sizeBytes / (1024 * 1024)}MB, " +
                            "so preview validation was skipped to avoid running out of memory."
                    )
                    return@forEach
                }

                val payload = backupReader.readModulePayload(uri, section.key).getOrThrow()

                val moduleValidation = validationPipeline.validateModulePayload(
                    section = section,
                    payload = payload,
                    manifest = plan.manifest
                )
                if (moduleValidation is BackupValidationResult.Invalid) {
                    if (moduleValidation.fatalErrors.isNotEmpty()) {
                        throw IllegalArgumentException(
                            "${section.label}: ${moduleValidation.fatalErrors.first().message}"
                        )
                    }
                    warnings.addAll(
                        moduleValidation.warnings.map { warning ->
                            "${section.label}: ${warning.message}"
                        }
                    )
                }
            }

            // Report the user's original URI, not the decrypted temp copy.
            plan.copy(backupUri = originalUri.toString(), warnings = warnings)
        }
    }

    /**
     * Executes a restore according to the given plan. For encrypted backups
     * the decrypted copy produced by [inspectBackup] is used and cleaned up
     * afterwards regardless of outcome.
     */
    suspend fun restore(
        uri: Uri,
        plan: RestorePlan,
        onProgress: (BackupTransferProgressUpdate) -> Unit
    ): RestoreResult = withContext(Dispatchers.IO) {
        val readableUri = try {
            resolveReadableUri(uri, passphrase = null)
        } catch (e: BackupEncryptedException) {
            return@withContext RestoreResult.TotalFailure(
                "This backup is encrypted. Inspect it with its password before restoring."
            )
        }

        val fileValidation = validationPipeline.validateFile(readableUri)
        if (fileValidation is BackupValidationResult.Invalid && fileValidation.fatalErrors.isNotEmpty()) {
            discardDecryptedBackup(uri)
            return@withContext RestoreResult.TotalFailure(
                "Backup file failed validation: ${fileValidation.fatalErrors.first().message}"
            )
        }

        val result = try {
            restoreExecutor.execute(readableUri, plan, onProgress)
        } finally {
            discardDecryptedBackup(uri)
        }

        if (result is RestoreResult.Success) {
            try {
                val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                backupHistoryRepository.addEntry(
                    BackupHistoryEntry(
                        uri = uri.toString(),
                        displayName = docFile?.name ?: "backup.pxpl",
                        createdAt = plan.manifest.createdAt,
                        schemaVersion = plan.manifest.schemaVersion,
                        modules = plan.manifest.modules.keys,
                        sizeBytes = docFile?.length() ?: 0,
                        appVersion = plan.manifest.appVersion
                    )
                )
            } catch (_: Exception) {
            }
        }

        result
    }

    fun getBackupHistory(): Flow<List<BackupHistoryEntry>> {
        return backupHistoryRepository.historyFlow
    }

    suspend fun removeBackupHistoryEntry(uri: String) {
        backupHistoryRepository.removeEntry(uri)
    }

    /**
     * Drops (and deletes) the cached decrypted copy for [uri], e.g. when the
     * user dismisses the restore plan of an encrypted backup.
     */
    fun discardDecryptedBackup(uri: Uri) {
        val handle = synchronized(decryptedBackups) { decryptedBackups.remove(uri.toString()) }
        handle?.release()
    }

    /**
     * Returns a URI the read pipeline can parse directly: the input itself for
     * plain archives, or a decrypted temp copy for encrypted ones. The full
     * ciphertext is copied (and thus GCM-authenticated) before anything parses
     * it, so a wrong passphrase always fails here and never mid-restore.
     */
    private suspend fun resolveReadableUri(uri: Uri, passphrase: String?): Uri {
        if (!isBackupEncrypted(uri)) return uri

        val key = uri.toString()
        synchronized(decryptedBackups) { decryptedBackups[key] }
            ?.takeIf { it.file.exists() }
            ?.let { return Uri.fromFile(it.file) }
        if (passphrase == null) throw BackupEncryptedException()
        val temp = File(decryptedDir, "decrypted_${System.currentTimeMillis()}_${tempCounter.incrementAndGet()}.pxpl")
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                skipFully(raw, BackupFormatDetector.PXPL_MAGIC_SIZE)
                BackupCrypto.decryptingStream(raw, passphrase).use { decrypted ->
                    temp.outputStream().buffered().use { out ->
                        out.write(BackupFormatDetector.PXPL_MAGIC)
                        copyLimited(decrypted, out, BackupFileValidator.MAX_BACKUP_SIZE_BYTES)
                    }
                }
            } ?: throw IllegalStateException("Unable to open backup file")
        } catch (e: Exception) {
            runCatching { temp.delete() }
            if (e is AEADBadTagException || (e is IOException && e.cause is AEADBadTagException)) {
                throw BackupWrongPassphraseException()
            }
            throw e
        }

        val superseded = mutableListOf<DecryptedBackupHandle>()
        synchronized(decryptedBackups) {
            val replaced = decryptedBackups.put(key, DecryptedBackupHandle(temp))
            if (replaced != null && replaced.file.absolutePath != temp.absolutePath) {
                superseded.add(replaced)
            }
            while (decryptedBackups.size > MAX_CACHED_DECRYPTIONS) {
                val oldestKey = decryptedBackups.keys.firstOrNull { it != key } ?: break
                val evicted = decryptedBackups.remove(oldestKey)
                if (evicted != null && evicted.file.absolutePath != temp.absolutePath) superseded.add(evicted)
            }
        }
        superseded.forEach { stale -> stale.release() }
        return Uri.fromFile(temp)
    }

    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException(
                    "Decrypted backup exceeds the ${maxBytes / (1024 * 1024)}MB safety limit."
                )
            }
            output.write(buffer, 0, read)
        }
    }

    private fun skipFully(input: InputStream, byteCount: Int) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped > 0) {
                remaining -= skipped.toInt()
                continue
            }
            if (input.read() == -1) {
                throw IllegalArgumentException("Backup file is truncated.")
            }
            remaining--
        }
    }

    private fun reportProgress(
        onProgress: (BackupTransferProgressUpdate) -> Unit,
        operation: BackupOperationType,
        step: Int,
        totalSteps: Int,
        title: String,
        detail: String,
        section: BackupSection? = null
    ) {
        onProgress(
            BackupTransferProgressUpdate(
                operation = operation,
                step = step,
                totalSteps = totalSteps,
                title = title,
                detail = detail,
                section = section
            )
        )
    }

    private companion object {
        const val MAX_CACHED_DECRYPTIONS = 2
    }
}
