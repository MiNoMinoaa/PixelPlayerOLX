package com.minoppol.music.data.navidrome

import java.net.URLDecoder
import java.net.URLEncoder

object NavidromeSongUri {
    private const val PREFIX = "navidrome://"
    private const val UTF_8 = "UTF-8"

    fun build(songId: String): String = PREFIX + encodeId(songId)

    fun parseId(uri: String?): String? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        val tail = uri.substring(PREFIX.length).takeIf { it.isNotEmpty() } ?: return null
        return decodeId(tail)?.takeIf { encodeId(it) == tail } ?: tail
    }

    fun encodeId(id: String): String = URLEncoder.encode(id, UTF_8).replace("+", "%20")

    private fun decodeId(encoded: String): String? =
        runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
}
