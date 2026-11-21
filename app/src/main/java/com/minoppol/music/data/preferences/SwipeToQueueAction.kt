package com.minoppol.music.data.preferences

enum class SwipeToQueueAction(val storageKey: String) {
    OFF("off"),
    ADD_TO_QUEUE("add_to_queue"),
    PLAY_NEXT("play_next");
    companion object {
        val DEFAULT = ADD_TO_QUEUE
        fun fromStorageKey(value: String?): SwipeToQueueAction =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
