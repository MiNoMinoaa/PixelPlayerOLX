package com.minoppol.music.data.service

import androidx.media3.common.MediaItem

internal data class TrustedMediaItemsResolution(
    val mediaItems: MutableList<MediaItem>,
    val trustedArtworkGrantItems: List<MediaItem>,
)

internal fun resolveMediaItemsWithTrustedArtworkGrants(
    requestedItems: List<MediaItem>,
    trustedItemResolver: (String) -> MediaItem?
): TrustedMediaItemsResolution {
    val resolvedItems = ArrayList<MediaItem>(requestedItems.size)
    val trustedArtworkGrantItems = ArrayList<MediaItem>()

    requestedItems.forEach { requestedItem ->
        val trustedItem = trustedItemResolver(requestedItem.mediaId)
        if (trustedItem != null) {
            val fragment = requestedItem.localConfiguration?.uri?.fragment
            val carriedItem = if (fragment.isNullOrBlank()) {
                trustedItem
            } else {
                val baseUri = trustedItem.localConfiguration?.uri
                if (baseUri == null || baseUri.fragment != null) {
                    trustedItem
                } else {
                    trustedItem.buildUpon()
                        .setUri(baseUri.buildUpon().fragment(fragment).build())
                        .build()
                }
            }
            resolvedItems += carriedItem
            trustedArtworkGrantItems += trustedItem
        } else {
            resolvedItems += requestedItem
        }
    }

    return TrustedMediaItemsResolution(
        mediaItems = resolvedItems,
        trustedArtworkGrantItems = trustedArtworkGrantItems
    )
}
