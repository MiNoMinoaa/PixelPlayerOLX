package com.minoppol.music.data.preferences

import androidx.annotation.StringRes
import com.minoppol.music.R

enum class CollagePattern(
    val storageKey: String,
    @StringRes val labelRes: Int
) {
    COSMIC_SWIRL("cosmic_swirl", R.string.collage_pattern_cosmic_swirl),
    HONEYCOMB_GROOVE("honeycomb_groove", R.string.collage_pattern_honeycomb_groove),
    VINYL_STACK("vinyl_stack", R.string.collage_pattern_vinyl_stack),
    PIXEL_MOSAIC("pixel_mosaic", R.string.collage_pattern_pixel_mosaic),
    STARDUST_SCATTER("stardust_scatter", R.string.collage_pattern_stardust_scatter),
    SOFT_ORBIT("soft_orbit", R.string.collage_pattern_soft_orbit),
    ARROW_FANFARE("arrow_fanfare", R.string.collage_pattern_arrow_fanfare),
    SUNNY_BLOOM("sunny_bloom", R.string.collage_pattern_sunny_bloom),
    PIXEL_COOKIE("pixel_cookie", R.string.collage_pattern_pixel_cookie),
    HEART_GARDEN("heart_garden", R.string.collage_pattern_heart_garden);

    companion object {
        val default: CollagePattern = COSMIC_SWIRL

        fun fromStorageKey(value: String?): CollagePattern {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
