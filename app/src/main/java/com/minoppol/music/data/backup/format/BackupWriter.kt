package com.minoppol.music.data.backup.format

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.minoppol.music.data.backup.model.BackupManifest
import com.minoppol.music.data.backup.model.BackupModuleInfo
import com.minoppol.music.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackupGson private val gson: Gson
) {
    suspend fun write(
        uri: Uri,
        manifest: BackupManifest,
        modulePayloads: Map<String, String>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        passphrase: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "backup_stage_${stagingCounter.incrementAndGet()}.pxpl")
        try {
            stageArchive(staged, manifest, modulePayloads, onProgress, passphrase)
            publish(staged, uri)
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            runCatching { staged.delete() }
            throw cancellation
        } catch (failure: Throwable) {
            runCatching { staged.delete() }
            Result.failure(failure)
        } finally {
            runCatching { staged.delete() }
        }
    }

    private fun stageArchive(
        target: File,
        manifest: BackupManifest,
        modulePayloads: Map<String, String>,
        onProgress: (current: Int, total: Int) -> Unit,
        passphrase: String?
    ) {
        val totalSteps = modulePayloads.size + 1
        var currentStep = 0

        val modulesInfo = mutableMapOf<String, BackupModuleInfo>()
        val payloadBytes = mutableMapOf<String, ByteArray>()

        modulePayloads.forEach { (key, jsonPayload) ->
            val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
            payloadBytes[key] = bytes
            modulesInfo[key] = BackupModuleInfo(
                checksum = "sha256:${sha256(bytes)}",
                entryCount = countJsonArrayEntries(jsonPayload),
                sizeBytes = bytes.size.toLong()
            )
        }

        val finalManifest = manifest.copy(modules = modulesInfo)
        val manifestJson = gson.toJson(finalManifest)

        target.outputStream().buffered().use { rawOutput ->
            rawOutput.write(BackupFormatDetector.PXPL_MAGIC)

            val archiveOutput = if (passphrase != null) {
                BackupCrypto.encryptingStream(rawOutput, passphrase)
            } else {
                rawOutput
            }

            ZipOutputStream(archiveOutput).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_FILENAME))
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                onProgress(++currentStep, totalSteps)

                payloadBytes.forEach { (key, bytes) ->
                    zip.putNextEntry(ZipEntry("$key.json"))
                    zip.write(bytes)
                    zip.closeEntry()
                    onProgress(++currentStep, totalSteps)
                }
            }
        }
    }

    private fun publish(staged: File, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Unable to open output stream for backup")
        output.use { destination ->
            staged.inputStream().buffered().use { source -> source.copyTo(destination) }
            destination.flush()
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun countJsonArrayEntries(json: String): Int {
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                gson.fromJson(trimmed, com.google.gson.JsonArray::class.java)?.size() ?: 0
            } else {
                1
            }
        } catch (_: Exception) {
            0
        }
    }

    private companion object {
        val stagingCounter = AtomicLong(0)
    }
}
