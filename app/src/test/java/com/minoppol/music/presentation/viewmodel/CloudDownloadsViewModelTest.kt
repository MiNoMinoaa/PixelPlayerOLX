package com.minoppol.music.presentation.viewmodel

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.minoppol.music.MainCoroutineExtension
import com.minoppol.music.data.cache.PlaybackCacheManager
import com.minoppol.music.data.lxmusic.LxMusicRepository
import com.minoppol.music.data.offline.CloudOfflineRepository
import com.minoppol.music.data.offline.OfflineDownload
import com.minoppol.music.data.offline.OfflineDownloadStatus
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.preferences.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class CloudDownloadsViewModelTest {

    @Test
    fun `downloads are grouped and storage includes completed and partial files`() {
        val downloads = listOf(
            download("complete", OfflineDownloadStatus.COMPLETE, bytes = 12_000L),
            download("queued", OfflineDownloadStatus.QUEUED),
            download("downloading", OfflineDownloadStatus.DOWNLOADING, bytes = 4_000L),
            download("failed", OfflineDownloadStatus.FAILED, bytes = 8_000L)
        )

        val state = downloads.toCloudDownloadsUiState()

        assertThat(state.completed.map { it.downloadId }).containsExactly("complete")
        assertThat(state.downloading.map { it.downloadId }).containsExactly("downloading")
        assertThat(state.queued.map { it.downloadId }).containsExactly("queued")
        assertThat(state.failed.map { it.downloadId }).containsExactly("failed")
        assertThat(state.usedBytes).isEqualTo(16_000L)
        assertThat(state.totalCount).isEqualTo(4)
    }

    @Test
    fun `remove and retry actions are delegated to repository`() = runTest {
        val failed = download("failed", OfflineDownloadStatus.FAILED)
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(listOf(failed))
        val viewModel = createViewModel(repository)

        advanceUntilIdle()
        viewModel.retry(failed)
        viewModel.remove(failed)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.retry(failed.sourceUri) }
        coVerify(exactly = 1) { repository.remove(failed.sourceUri) }
    }

    @Test
    fun `retry ignores downloads that are not failed`() = runTest {
        val complete = download("complete", OfflineDownloadStatus.COMPLETE)
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(listOf(complete))
        val viewModel = createViewModel(repository)

        viewModel.retry(complete)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.retry(any()) }
    }

    @Test
    fun `batch download keeps supported cloud songs and removes duplicates`() = runTest {
        val navidrome = song("navidrome", "navidrome://one")
        val duplicate = song("duplicate", "navidrome://one")
        val jellyfin = song("jellyfin", "jellyfin://two")
        val local = song("local", "content://media/audio/3")
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(emptyList())
        val viewModel = createViewModel(repository)

        viewModel.downloadSelected(listOf(navidrome, duplicate, jellyfin, local))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.enqueue(navidrome) }
        coVerify(exactly = 1) { repository.enqueue(jellyfin) }
        coVerify(exactly = 0) { repository.enqueue(local) }
    }

    @Test
    fun `batch download ignores a local only selection`() = runTest {
        val repository = mockk<CloudOfflineRepository>(relaxed = true)
        every { repository.observeAll() } returns flowOf(emptyList())
        val viewModel = createViewModel(repository)

        viewModel.downloadSelected(listOf(song("local", "content://media/audio/3")))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.enqueue(any<Song>()) }
    }

    private fun createViewModel(repository: CloudOfflineRepository): CloudDownloadsViewModel {
        val appContext = mockk<Context>(relaxed = true)
        val playbackCacheManager = mockk<PlaybackCacheManager>(relaxed = true)
        every { playbackCacheManager.limitMbFlow } returns flowOf(1024)
        val lxMusicRepository = mockk<LxMusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        every { userPreferencesRepository.downloadMaxThreadsFlow } returns
            flowOf(UserPreferencesRepository.DEFAULT_DOWNLOAD_MAX_THREADS)
        return CloudDownloadsViewModel(
            appContext = appContext,
            repository = repository,
            playbackCacheManager = playbackCacheManager,
            lxMusicRepository = lxMusicRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    private fun download(
        id: String,
        status: OfflineDownloadStatus,
        bytes: Long = 0L
    ) = OfflineDownload(
        downloadId = id,
        sourceUri = "navidrome://$id",
        status = status,
        bytesDownloaded = bytes,
        totalBytes = null,
        localPath = null,
        errorMessage = null,
        title = id,
        provider = "navidrome"
    )

    private fun song(id: String, uri: String) = Song.emptySong().copy(
        id = id,
        title = id,
        contentUriString = uri
    )
}
