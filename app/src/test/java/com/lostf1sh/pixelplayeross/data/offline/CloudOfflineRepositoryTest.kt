package com.lostf1sh.pixelplayeross.data.offline

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import org.junit.jupiter.api.Test

class CloudOfflineRepositoryTest {
    @Test
    fun `provider detection accepts only supported cloud schemes`() {
        assertThat(CloudOfflineRepository.providerFor("navidrome://track_1")).isEqualTo("navidrome")
        assertThat(CloudOfflineRepository.providerFor("jellyfin://ABC123")).isEqualTo("jellyfin")
        assertThat(CloudOfflineRepository.providerFor("https://example.com/song.mp3")).isNull()
        assertThat(CloudOfflineRepository.providerFor("file:///music/song.mp3")).isNull()
    }

    @Test
    fun `download ids are stable and do not expose provider identifiers`() {
        val uri = "navidrome://private-track-id"
        val first = CloudOfflineRepository.downloadId(uri)
        val second = CloudOfflineRepository.downloadId(uri)

        assertThat(first).isEqualTo(second)
        assertThat(first).hasLength(64)
        assertThat(first).doesNotContain("private-track-id")
        assertThat(first).isNotEqualTo(CloudOfflineRepository.downloadId("navidrome://other"))
    }

    @Test
    fun `cloud song detection uses canonical playback uri`() {
        assertThat(CloudOfflineRepository.isCloudSong(song("navidrome://abc"))).isTrue()
        assertThat(CloudOfflineRepository.isCloudSong(song("jellyfin://ABC123"))).isTrue()
        assertThat(CloudOfflineRepository.isCloudSong(song("content://media/audio/1"))).isFalse()
    }

    private fun song(uri: String) = Song.emptySong().copy(contentUriString = uri)
}
