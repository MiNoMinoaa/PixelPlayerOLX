package com.minoppol.music.data.playlist

/**
 * Applies a reorder of the songs a playlist currently shows to its full stored order.
 *
 * The detail screen only lists songs that resolve in the library right now, so songs in
 * excluded folders, on unmounted storage or on a signed-out server are missing from
 * [newVisibleOrder]. Visible slots in [storedIds] take the new order one by one, and every
 * hidden id keeps its original position.
 *
 * Playlists may contain the same id more than once, so the visible ids are compared as a
 * multiset. Returns null when [newVisibleOrder] is not a permutation of the visible stored
 * entries, for example when it holds an id that is not stored or drops one occurrence.
 */
internal fun mergeVisiblePlaylistOrder(
    storedIds: List<String>,
    newVisibleOrder: List<String>
): List<String>? {
    val visible = newVisibleOrder.toHashSet()
    val slots = storedIds.filter { it in visible }
    if (slots.groupingBy { it }.eachCount() != newVisibleOrder.groupingBy { it }.eachCount()) {
        return null
    }
    val reordered = newVisibleOrder.iterator()
    return storedIds.map { id -> if (id in visible) reordered.next() else id }
}
