package com.minoppol.music.utils

import java.io.File
import java.text.Normalizer
import java.util.Locale

internal object FolderArtistArtUtils {

    private val artworkNames = listOf("artist.jpg", "artist.png", "band.jpg", "band.png")

    fun findArtistImage(
        audioDirectoryPaths: List<String>,
        artistName: String,
        isUsableArtwork: (File) -> Boolean = AlbumArtUtils::isUsableExternalArtwork
    ): File? {
        val normalizedArtistName = normalizeName(artistName)
        val visited = mutableSetOf<String>()
        for (path in audioDirectoryPaths) {
            val audioDirectory = canonicalDirectory(path) ?: continue
            val artistAncestors = listOfNotNull(
                audioDirectory.parentFile,
                audioDirectory.parentFile?.parentFile
            ).filter {
                normalizedArtistName.isNotEmpty() && normalizeName(it.name) == normalizedArtistName
            }
            for (directory in artistAncestors + audioDirectory) {
                if (!visited.add(directory.path)) continue
                if (!AlbumArtUtils.shouldTrustDirectoryArtwork(directory.name)) continue

                val candidates = runCatching {
                    directory.listFiles { file ->
                        file.name.lowercase(Locale.ROOT) in artworkNames
                    }?.sortedWith(
                        compareBy<File> { artworkNames.indexOf(it.name.lowercase(Locale.ROOT)) }
                            .thenBy { it.name }
                    )
                }.getOrNull().orEmpty()

                for (candidate in candidates) {
                    val image = runCatching { candidate.canonicalFile }.getOrNull() ?: continue
                    if (image.parentFile != directory || !image.isFile || !image.canRead()) continue
                    if (runCatching { isUsableArtwork(image) }.getOrDefault(false)) return image
                }
            }
        }
        return null
    }

    private fun normalizeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    private fun canonicalDirectory(path: String): File? {
        if (path.isBlank()) return null
        val directory = File(path)
        if (!directory.isAbsolute) return null
        return runCatching { directory.canonicalFile }.getOrNull()?.takeIf { it.isDirectory }
    }
}
