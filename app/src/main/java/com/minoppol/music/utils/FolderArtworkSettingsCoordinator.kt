package com.minoppol.music.utils

import android.content.Context
import com.minoppol.music.data.media.ImageCacheManager
import com.minoppol.music.data.preferences.UserPreferencesRepository
import com.minoppol.music.data.repository.ArtistImageRepository
import com.minoppol.music.data.worker.SyncManager
import com.minoppol.music.presentation.viewmodel.ColorSchemeProcessor
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderArtworkSettingsCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val imageCacheManager: ImageCacheManager,
    private val artistImageRepository: ArtistImageRepository,
    private val syncManager: SyncManager,
    private val colorSchemeProcessor: Lazy<ColorSchemeProcessor>
) {
    private val mutex = Mutex()
    private var legacyCacheMigrated = false

    suspend fun setEnabled(enabled: Boolean) {
        update { preferences.setUseFolderAlbumArt(enabled) }
    }

    suspend fun reconcile() {
        update {}
    }

    private suspend fun update(writePreference: suspend () -> Unit) = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                writePreference()
                reconcileLocked()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Unable to reconcile folder artwork settings")
        }
    }

    private suspend fun reconcileLocked() {
        if (!legacyCacheMigrated) {
            AlbumArtUtils.migrateLegacyCacheLocation(context)
            legacyCacheMigrated = true
        }

        val enabled = preferences.useFolderAlbumArtFlow.first()
        AlbumArtUtils.setFolderAlbumArtPreference(enabled)
        val effective = AlbumArtUtils.isFolderAlbumArtEnabled(context)
        val recorded = preferences.folderAlbumArtCacheStateFlow.first()
        if (resolveFolderAlbumArtUpdate(recorded, effective) == FolderAlbumArtUpdate.IGNORE) return

        AlbumArtUtils.bumpArtworkCacheGeneration()
        AlbumArtCacheManager.clearAllCache(context)
        AlbumArtUtils.bumpArtworkCacheGeneration()
        imageCacheManager.clearAllCoverArtCaches()
        colorSchemeProcessor.get().invalidateLocalArtworkSchemes()
        artistImageRepository.clearCache()
        AlbumArtUtils.notifyArtworkCacheInvalidated()

        if (preferences.initialSetupDoneFlow.first()) {
            syncManager.fullSync()
        }
        preferences.setFolderAlbumArtCacheState(effective)
    }
}

internal enum class FolderAlbumArtUpdate {
    IGNORE,
    MIRROR_AND_INVALIDATE
}

internal fun resolveFolderAlbumArtUpdate(
    previous: Boolean?,
    observed: Boolean
): FolderAlbumArtUpdate = when (previous) {
    observed -> FolderAlbumArtUpdate.IGNORE
    else -> FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE
}
