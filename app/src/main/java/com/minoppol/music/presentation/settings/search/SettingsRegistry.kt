package com.minoppol.music.presentation.settings.search

import com.minoppol.music.R
import com.minoppol.music.presentation.model.SettingsCategory
import com.minoppol.music.presentation.navigation.Screen

object SettingsRegistry {

    val allSettings: List<SettingSpec> by lazy {
        listOf(
            // --- LIBRARY CATEGORY ---
            SettingSpec(
                id = "library_excluded_directories",
                itemKey = "item_library_excluded_directories",
                titleRes = R.string.setcat_excluded_directories_title,
                subtitleRes = R.string.setcat_excluded_directories_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("folder", "ignore", "exclude", "blacklist", "directories", "hidden")
            ),
            SettingSpec(
                id = "library_artists",
                itemKey = "item_library_artists",
                titleRes = R.string.setcat_artists_title,
                subtitleRes = R.string.setcat_artists_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.ArtistSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("artist", "delimiters", "split", "multi artist", "parsing", "separator")
            ),
            SettingSpec(
                id = "library_min_duration",
                itemKey = "item_library_min_duration",
                titleRes = R.string.setcat_min_song_duration,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("duration", "short songs", "filter", "seconds", "length")
            ),
            SettingSpec(
                id = "library_min_tracks",
                itemKey = "item_library_min_tracks",
                titleRes = R.string.setcat_min_tracks_per_album,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("tracks", "album", "filter", "singles")
            ),
            SettingSpec(
                id = "library_album_art_cache",
                itemKey = "item_library_album_art_cache",
                titleRes = R.string.setcat_album_art_cache_limit,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("cache", "storage", "album art", "artwork", "size", "space")
            ),
            SettingSpec(
                id = "library_refresh",
                itemKey = "item_library_refresh",
                titleRes = R.string.presentation_batch_f_refresh_library_title,
                subtitleRes = R.string.presentation_batch_f_refresh_library_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.ACTION,
                keywordsStatic = listOf("scan", "rescan", "sync", "refresh", "rebuild", "database")
            ),
            SettingSpec(
                id = "library_m3u_sync",
                itemKey = "item_library_m3u_sync",
                titleRes = R.string.setcat_m3u_sync_title,
                subtitleRes = R.string.setcat_m3u_sync_disabled_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("m3u", "m3u8", "playlist", "sync", "folder", "import", "export")
            ),
            SettingSpec(
                id = "library_m3u_sync_now",
                itemKey = "item_library_m3u_sync_now",
                titleRes = R.string.setcat_m3u_sync_now_title,
                subtitleRes = R.string.setcat_m3u_sync_now_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.ACTION,
                keywordsStatic = listOf("m3u", "playlist", "sync now", "reconcile")
            ),
            SettingSpec(
                id = "library_folder_album_art",
                itemKey = "item_library_folder_album_art",
                titleRes = R.string.setcat_folder_album_art_title,
                subtitleRes = R.string.setcat_folder_album_art_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf(
                    "cover", "cover.jpg", "folder.jpg", "album art", "artwork", "image", "folder",
                    "artist", "artist.jpg", "artist.png", "band.jpg", "band.png", "artist photo"
                )
            ),
            SettingSpec(
                id = "library_find_duplicates",
                itemKey = "item_library_find_duplicates",
                titleRes = R.string.setcat_find_duplicates_title,
                subtitleRes = R.string.setcat_find_duplicates_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.Duplicates.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("duplicate", "cleanup", "copies", "same song")
            ),
            SettingSpec(
                id = "library_external_artist_images",
                itemKey = "item_library_external_artist_images",
                titleRes = R.string.setcat_external_artist_images_title,
                subtitleRes = R.string.setcat_external_artist_images_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.SettingsCategory.createRoute("library"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("deezer", "artist image", "artwork", "photos", "pictures"),
                getValue = { it.externalArtistImagesEnabled },
                onToggle = { viewModel, checked -> viewModel.setExternalArtistImagesEnabled(checked) }
            ),

            // --- ACCOUNTS (grouped under Library) ---
            SettingSpec(
                id = "accounts_hub",
                itemKey = "item_accounts_hub",
                titleRes = R.string.settings_accounts_row_title,
                subtitleRes = R.string.settings_accounts_row_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.Accounts.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("accounts", "navidrome", "jellyfin", "subsonic", "streaming", "server", "login")
            ),
            SettingSpec(
                id = "cloud_downloads",
                itemKey = "item_cloud_downloads",
                titleRes = R.string.cloud_downloads_title,
                subtitleRes = R.string.cloud_downloads_settings_subtitle,
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.CloudDownloads.route,
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf(
                    "downloads", "offline", "cloud", "storage", "navidrome", "jellyfin",
                    "playback cache", "cache", "cached songs", "clear cache", "缓存", "播放缓存",
                    "清除缓存", "lxmusic", "quality",
                    "thread", "threads", "concurrent", "concurrency", "parallel",
                    "线程", "下载线程", "并发", "并行", "等待中", "queued", "waiting"
                )
            ),
            SettingSpec(
                id = "accounts_listenbrainz",
                itemKey = "item_accounts_listenbrainz",
                titleRes = R.string.accounts_listenbrainz_title,
                subtitleStatic = "Scrobble your listens to ListenBrainz",
                category = SettingsCategory.LIBRARY,
                subscreenRoute = Screen.Accounts.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("scrobble", "scrobbling", "listens", "maloja", "last.fm", "history")
            ),

            // --- APPEARANCE CATEGORY ---
            SettingSpec(
                id = "appearance_app_language",
                itemKey = "item_appearance_app_language",
                titleRes = R.string.setcat_app_language_label,
                subtitleRes = R.string.setcat_app_language_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("language", "locale", "translation", "english", "region")
            ),
            SettingSpec(
                id = "appearance_app_theme",
                itemKey = "item_appearance_app_theme",
                titleRes = R.string.setcat_app_theme_label,
                subtitleRes = R.string.setcat_app_theme_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("dark mode", "light mode", "theme", "system", "night")
            ),
            SettingSpec(
                id = "appearance_global_now_playing_theme",
                itemKey = "item_appearance_global_now_playing_theme",
                titleRes = R.string.setcat_global_now_playing_theme_title,
                subtitleRes = R.string.setcat_global_now_playing_theme_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf(
                    "now playing", "album art", "dynamic colors", "app theme", "palette"
                ),
                getValue = { it.globalNowPlayingThemeEnabled },
                onToggle = { viewModel, checked ->
                    viewModel.setGlobalNowPlayingThemeEnabled(checked)
                }
            ),
            SettingSpec(
                id = "appearance_smooth_corners",
                itemKey = "item_appearance_smooth_corners",
                titleRes = R.string.setcat_smooth_corners_title,
                subtitleRes = R.string.setcat_smooth_corners_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("corners", "shape", "rounded", "squircle", "performance")
            ),
            SettingSpec(
                id = "appearance_player_theme",
                itemKey = "item_appearance_player_theme",
                titleRes = R.string.setcat_player_theme_label,
                subtitleRes = R.string.setcat_player_theme_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("player theme", "album art", "dynamic colors", "now playing")
            ),
            SettingSpec(
                id = "appearance_show_player_file_info",
                itemKey = "item_appearance_show_player_file_info",
                titleRes = R.string.setcat_show_player_file_info_title,
                subtitleRes = R.string.setcat_show_player_file_info_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("codec", "bitrate", "sample rate", "file info", "format"),
                getValue = { it.showPlayerFileInfo },
                onToggle = { viewModel, checked -> viewModel.setShowPlayerFileInfo(checked) }
            ),
            SettingSpec(
                id = "appearance_palette_style",
                itemKey = "item_appearance_palette_style",
                titleRes = R.string.setcat_album_art_palette_title,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.PaletteStyle.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("palette", "color", "monet", "material you", "accent", "album colors")
            ),
            SettingSpec(
                id = "appearance_carousel_style",
                itemKey = "item_appearance_carousel_style",
                titleRes = R.string.setcat_carousel_style_label,
                subtitleRes = R.string.setcat_carousel_style_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("carousel", "peek", "album covers", "swipe")
            ),
            SettingSpec(
                id = "appearance_collage_pattern",
                itemKey = "item_appearance_collage_pattern",
                titleRes = R.string.setcat_collage_pattern_label,
                subtitleRes = R.string.setcat_collage_pattern_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("collage", "home", "your mix", "pattern", "grid")
            ),
            SettingSpec(
                id = "appearance_collage_auto_rotate",
                itemKey = "item_appearance_collage_auto_rotate",
                titleRes = R.string.setcat_auto_rotate_patterns_title,
                subtitleRes = R.string.setcat_auto_rotate_patterns_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("rotate", "collage", "cycle", "shuffle patterns"),
                getValue = { it.collageAutoRotate },
                onToggle = { viewModel, checked -> viewModel.setCollageAutoRotate(checked) }
            ),
            SettingSpec(
                id = "appearance_navbar_style",
                itemKey = "item_appearance_navbar_style",
                titleRes = R.string.setcat_navbar_style_label,
                subtitleRes = R.string.setcat_navbar_style_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("navbar", "navigation bar", "full width", "bottom bar")
            ),
            SettingSpec(
                id = "appearance_navbar_compact",
                itemKey = "item_appearance_navbar_compact",
                titleRes = R.string.setcat_compact_mode_title,
                subtitleRes = R.string.setcat_compact_mode_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("compact", "navbar", "icons", "height", "small"),
                getValue = { it.navBarCompactMode },
                onToggle = { viewModel, checked -> viewModel.setNavBarCompactMode(checked) }
            ),
            SettingSpec(
                id = "appearance_show_search_tab",
                itemKey = "item_appearance_show_search_tab",
                titleRes = R.string.setcat_show_search_tab_title,
                subtitleRes = R.string.setcat_show_search_tab_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("search", "tab", "navbar", "hide", "show", "visibility"),
                getValue = { it.showSearchTab },
                onToggle = { viewModel, checked -> viewModel.setShowSearchTab(checked) }
            ),
            SettingSpec(
                id = "appearance_show_recent_tab",
                itemKey = "item_appearance_show_recent_tab",
                titleRes = R.string.setcat_show_recent_tab_title,
                subtitleRes = R.string.setcat_show_recent_tab_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("recent", "recently played", "history", "tab", "navbar", "hide", "show", "visibility"),
                getValue = { it.showRecentTab },
                onToggle = { viewModel, checked -> viewModel.setShowRecentTab(checked) }
            ),
            SettingSpec(
                id = "appearance_navbar_corner",
                itemKey = "item_appearance_navbar_corner",
                titleRes = R.string.setcat_navbar_corner_title,
                subtitleRes = R.string.setcat_navbar_corner_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.NavBarCrRad.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("corner radius", "rounded", "navbar", "roundness")
            ),
            SettingSpec(
                id = "appearance_splash_animation",
                itemKey = "item_appearance_splash_animation",
                titleRes = R.string.setcat_splash_animation_title,
                subtitleRes = R.string.setcat_splash_animation_subtitle,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("splash", "startup", "launch", "animation", "intro"),
                getValue = { it.splashAnimationEnabled },
                onToggle = { viewModel, checked -> viewModel.setSplashAnimationEnabled(checked) }
            ),
            SettingSpec(
                id = "appearance_default_tab",
                itemKey = "item_appearance_default_tab",
                titleRes = R.string.setcat_default_tab_label,
                subtitleRes = R.string.setcat_default_tab_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("launch", "start screen", "default tab", "startup")
            ),
            SettingSpec(
                id = "appearance_library_navigation",
                itemKey = "item_appearance_library_navigation",
                titleRes = R.string.setcat_library_navigation_label,
                subtitleRes = R.string.setcat_library_navigation_desc,
                category = SettingsCategory.APPEARANCE,
                subscreenRoute = Screen.SettingsCategory.createRoute("appearance"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("library tabs", "pill", "navigation mode", "tab row")
            ),

            // --- PLAYBACK CATEGORY ---
            SettingSpec(
                id = "playback_keep_playing",
                itemKey = "item_playback_keep_playing",
                titleRes = R.string.setcat_keep_playing_label,
                subtitleRes = R.string.setcat_keep_playing_desc,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("background", "close app", "swipe away", "recents", "keep playing"),
                getValue = { it.keepPlayingInBackground },
                onToggle = { viewModel, checked -> viewModel.setKeepPlayingInBackground(checked) }
            ),
            SettingSpec(
                id = "playback_keep_screen_awake",
                itemKey = "item_playback_keep_screen_awake",
                titleRes = R.string.setcat_keep_screen_awake_title,
                subtitleRes = R.string.setcat_keep_screen_awake_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf(
                    "screen", "awake", "display", "driving", "sleep", "lock"
                ),
                getValue = { it.keepScreenAwakeWhilePlaying },
                onToggle = { viewModel, checked ->
                    viewModel.setKeepScreenAwakeWhilePlaying(checked)
                }
            ),
            SettingSpec(
                id = "playback_replaygain",
                itemKey = "item_playback_replaygain",
                titleRes = R.string.setcat_replaygain_enable_title,
                subtitleRes = R.string.setcat_replaygain_enable_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("replaygain", "volume", "normalization", "gain", "loudness"),
                getValue = { it.replayGainEnabled },
                onToggle = { viewModel, checked -> viewModel.setReplayGainEnabled(checked) }
            ),
            SettingSpec(
                id = "playback_headset_resume",
                itemKey = "item_playback_headset_resume",
                titleRes = R.string.setcat_headphones_resume_title,
                subtitleRes = R.string.setcat_headphones_resume_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("headphones", "bluetooth", "resume", "reconnect", "earbuds"),
                getValue = { it.resumeOnHeadsetReconnect },
                onToggle = { viewModel, checked -> viewModel.setResumeOnHeadsetReconnect(checked) }
            ),
            SettingSpec(
                id = "playback_crossfade",
                itemKey = "item_playback_crossfade",
                titleRes = R.string.setcat_crossfade_label,
                subtitleRes = R.string.setcat_crossfade_desc,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("crossfade", "fade", "transition", "smooth", "blend"),
                getValue = { it.isCrossfadeEnabled },
                onToggle = { viewModel, checked -> viewModel.setCrossfadeEnabled(checked) }
            ),
            SettingSpec(
                id = "playback_smart_crossfade",
                itemKey = "item_playback_smart_crossfade",
                titleRes = R.string.setcat_smart_crossfade_title,
                subtitleRes = R.string.setcat_smart_crossfade_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("smart", "crossfade", "bpm", "tempo", "beat", "dj", "blend"),
                getValue = { it.smartCrossfadeEnabled },
                onToggle = { viewModel, checked -> viewModel.setSmartCrossfadeEnabled(checked) }
            ),
            SettingSpec(
                id = "playback_speed",
                itemKey = "item_playback_speed",
                titleRes = R.string.setcat_playback_speed,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("speed", "tempo", "rate", "faster", "slower")
            ),
            SettingSpec(
                id = "playback_audio_output_mode",
                itemKey = "item_playback_audio_output_mode",
                titleRes = R.string.setcat_audio_output_mode_title,
                subtitleRes = R.string.setcat_audio_output_mode_description,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf(
                    "audio", "output", "direct", "hifi", "high fidelity",
                    "pcm", "float", "quality", "lossless"
                )
            ),
            SettingSpec(
                id = "playback_persistent_shuffle",
                itemKey = "item_playback_persistent_shuffle",
                titleRes = R.string.setcat_persistent_shuffle_title,
                subtitleRes = R.string.setcat_persistent_shuffle_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("shuffle", "remember", "persist", "random"),
                getValue = { it.persistentShuffleEnabled },
                onToggle = { viewModel, checked -> viewModel.setPersistentShuffleEnabled(checked) }
            ),
            SettingSpec(
                id = "playback_show_queue_history",
                itemKey = "item_playback_show_queue_history",
                titleRes = R.string.setcat_show_queue_history_title,
                subtitleRes = R.string.setcat_show_queue_history_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("queue", "history", "previous songs", "played"),
                getValue = { it.showQueueHistory },
                onToggle = { viewModel, checked -> viewModel.setShowQueueHistory(checked) }
            ),
            SettingSpec(
                id = "playback_lx_board_include_queue",
                itemKey = "item_playback_lx_board_include_queue",
                titleRes = R.string.setcat_lx_board_include_queue_title,
                subtitleRes = R.string.setcat_lx_board_include_queue_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("discover", "board", "queue", "online", "chart", "playlist queue"),
                getValue = { it.lxBoardIncludeQueue },
                onToggle = { viewModel, checked -> viewModel.setLxBoardIncludeQueue(checked) }
            ),
            SettingSpec(
                id = "playback_lx_board_show_shuffle_fab",
                itemKey = "item_playback_lx_board_show_shuffle_fab",
                titleRes = R.string.setcat_lx_board_show_shuffle_fab_title,
                subtitleRes = R.string.setcat_lx_board_show_shuffle_fab_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("discover", "board", "shuffle", "floating", "button", "random"),
                getValue = { it.lxBoardShowShuffleFab },
                onToggle = { viewModel, checked -> viewModel.setLxBoardShowShuffleFab(checked) }
            ),
            SettingSpec(
                id = "playback_lx_board_show_play_all_fab",
                itemKey = "item_playback_lx_board_show_play_all_fab",
                titleRes = R.string.setcat_lx_board_show_play_all_fab_title,
                subtitleRes = R.string.setcat_lx_board_show_play_all_fab_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.SettingsCategory.createRoute("playback"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("discover", "board", "play all", "floating", "button"),
                getValue = { it.lxBoardShowPlayAllFab },
                onToggle = { viewModel, checked -> viewModel.setLxBoardShowPlayAllFab(checked) }
            ),
            SettingSpec(
                id = "playback_lyrics_subscreen",
                itemKey = "item_playback_lyrics_subscreen",
                titleRes = R.string.setcat_lyrics_title,
                subtitleRes = R.string.setcat_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("lyrics", "歌词", "notification", "mini player", "font", "blur", "highlight")
            ),
            SettingSpec(
                id = "lyrics_immersive_mode",
                itemKey = "item_lyrics_immersive_mode",
                titleRes = R.string.setcat_immersive_lyrics_title,
                subtitleRes = R.string.setcat_immersive_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("immersive", "lyrics", "fullscreen", "auto hide", "controls"),
                getValue = { it.immersiveLyricsEnabled },
                onToggle = { viewModel, checked -> viewModel.setImmersiveLyricsEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_font_size",
                itemKey = "item_lyrics_font_size",
                titleRes = R.string.setcat_lyrics_font_size_title,
                subtitleRes = R.string.setcat_lyrics_font_size_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("lyrics", "font", "size", "text", "歌词", "字体", "大小", "字号")
            ),
            SettingSpec(
                id = "lyrics_word_highlight_enabled",
                itemKey = "item_lyrics_word_highlight_enabled",
                titleRes = R.string.lyrics_word_highlight_enabled_title,
                subtitleRes = R.string.lyrics_word_highlight_enabled_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf(
                    "word", "highlight", "karaoke", "逐字", "单词", "高亮", "字级", "进度"
                ),
                getValue = { it.lyricsWordHighlightEnabled },
                onToggle = { viewModel, checked -> viewModel.setLyricsWordHighlightEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_word_highlight_style",
                itemKey = "item_lyrics_word_highlight",
                titleRes = R.string.lyrics_highlight_style_title,
                subtitleRes = R.string.lyrics_highlight_style_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf(
                    "word", "highlight", "style", "wipe", "fade", "classic", "逐字", "样式", "风格"
                )
            ),
            SettingSpec(
                id = "lyrics_duet_layout",
                itemKey = "item_lyrics_duet_layout",
                titleRes = R.string.lyrics_duet_layout_title,
                subtitleRes = R.string.lyrics_duet_layout_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("duet", "voice", "singer", "对唱", "声部", "左右", "合唱"),
                getValue = { it.lyricsDuetLayoutEnabled },
                onToggle = { viewModel, checked -> viewModel.setLyricsDuetLayoutEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_background_vocals",
                itemKey = "item_lyrics_background_vocals",
                titleRes = R.string.lyrics_background_vocals_title,
                subtitleRes = R.string.lyrics_background_vocals_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("harmony", "backing", "vocals", "和声", "背景", "伴唱"),
                getValue = { it.showLyricsBackgroundVocals },
                onToggle = { viewModel, checked -> viewModel.setShowLyricsBackgroundVocals(checked) }
            ),
            SettingSpec(
                id = "lyrics_animated_blur",
                itemKey = "item_lyrics_animated_blur",
                titleRes = R.string.presentation_batch_f_exp_lyric_blur_title,
                subtitleRes = R.string.presentation_batch_f_exp_lyric_blur_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("blur", "animated", "effect", "模糊", "动效", "背景"),
                getValue = { it.animatedLyricsBlurEnabled },
                onToggle = { viewModel, checked -> viewModel.setAnimatedLyricsBlurEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_immersive_delay",
                itemKey = "item_lyrics_immersive_delay",
                titleRes = R.string.setcat_auto_hide_delay_label,
                subtitleRes = R.string.setcat_auto_hide_delay_desc,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("immersive", "delay", "hide", "沉浸", "延迟", "隐藏")
            ),
            SettingSpec(
                id = "lyrics_notification",
                itemKey = "item_lyrics_notification",
                titleRes = R.string.setcat_notification_lyrics_title,
                subtitleRes = R.string.setcat_notification_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("notification", "status bar", "通知", "通知栏"),
                getValue = { it.notificationLyricsEnabled },
                onToggle = { viewModel, checked -> viewModel.setNotificationLyricsEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_mini_player",
                itemKey = "item_lyrics_mini_player",
                titleRes = R.string.setcat_mini_player_lyrics_title,
                subtitleRes = R.string.setcat_mini_player_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("mini player", "mini", "迷你", "迷你播放器"),
                getValue = { it.miniPlayerLyricsEnabled },
                onToggle = { viewModel, checked -> viewModel.setMiniPlayerLyricsEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_in_player",
                itemKey = "item_lyrics_in_player",
                titleRes = R.string.setcat_in_player_lyrics_title,
                subtitleRes = R.string.setcat_in_player_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("player", "now playing", "播放页", "播放器"),
                getValue = { it.inPlayerLyricsEnabled },
                onToggle = { viewModel, checked -> viewModel.setInPlayerLyricsEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_in_player_size",
                itemKey = "item_lyrics_in_player_size",
                titleRes = R.string.setcat_in_player_lyrics_size_label,
                subtitleRes = R.string.setcat_in_player_lyrics_size_desc,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("player", "size", "font", "播放页", "大小", "字号")
            ),
            SettingSpec(
                id = "lyrics_source_priority",
                itemKey = "item_lyrics_source_priority",
                titleRes = R.string.setcat_lyrics_source_priority_label,
                subtitleRes = R.string.setcat_lyrics_source_priority_desc,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("source", "priority", "embedded", "online", "local", "歌词源", "优先级", "内嵌", "本地", "在线")
            ),
            SettingSpec(
                id = "lyrics_auto_scan_lrc",
                itemKey = "item_lyrics_auto_scan_lrc",
                titleRes = R.string.setcat_auto_scan_lrc_title,
                subtitleRes = R.string.setcat_auto_scan_lrc_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("scan", "lrc", "file", "自动扫描", "本地文件"),
                getValue = { it.autoScanLrcFiles },
                onToggle = { viewModel, checked -> viewModel.setAutoScanLrcFiles(checked) }
            ),
            SettingSpec(
                id = "lyrics_external",
                itemKey = "item_lyrics_external",
                titleRes = R.string.setcat_external_lyrics_title,
                subtitleRes = R.string.setcat_external_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("online", "fetch", "external", "在线", "获取"),
                getValue = { it.externalLyricsEnabled },
                onToggle = { viewModel, checked -> viewModel.setExternalLyricsEnabled(checked) }
            ),
            SettingSpec(
                id = "lyrics_reset_imported",
                itemKey = "item_lyrics_reset_imported",
                titleRes = R.string.setcat_reset_imported_lyrics_title,
                subtitleRes = R.string.setcat_reset_imported_lyrics_subtitle,
                category = SettingsCategory.PLAYBACK,
                subscreenRoute = Screen.LyricsSettings.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("reset", "imported", "clear", "重置", "导入", "清除")
            ),

            // --- BEHAVIOR CATEGORY ---
            SettingSpec(
                id = "behavior_folder_back_gesture",
                itemKey = "item_behavior_folder_back_gesture",
                titleRes = R.string.setcat_folder_back_gesture_title,
                subtitleRes = R.string.setcat_folder_back_gesture_subtitle,
                category = SettingsCategory.BEHAVIOR,
                subscreenRoute = Screen.SettingsCategory.createRoute("behavior"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("back gesture", "folders", "navigation", "system back"),
                getValue = { it.folderBackGestureNavigation },
                onToggle = { viewModel, checked -> viewModel.setFolderBackGestureNavigation(checked) }
            ),
            SettingSpec(
                id = "behavior_tap_bg_closes",
                itemKey = "item_behavior_tap_bg_closes",
                titleRes = R.string.setcat_tap_bg_closes_title,
                subtitleRes = R.string.setcat_tap_bg_closes_subtitle,
                category = SettingsCategory.BEHAVIOR,
                subscreenRoute = Screen.SettingsCategory.createRoute("behavior"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("tap", "background", "close player", "gesture", "dismiss"),
                getValue = { it.tapBackgroundClosesPlayer },
                onToggle = { viewModel, checked -> viewModel.setTapBackgroundClosesPlayer(checked) }
            ),
            SettingSpec(
                id = "behavior_haptics",
                itemKey = "item_behavior_haptics",
                titleRes = R.string.setcat_haptic_feedback_title,
                subtitleRes = R.string.setcat_haptic_feedback_subtitle,
                category = SettingsCategory.BEHAVIOR,
                subscreenRoute = Screen.SettingsCategory.createRoute("behavior"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("vibration", "haptic", "feedback", "buzz"),
                getValue = { it.hapticsEnabled },
                onToggle = { viewModel, checked -> viewModel.setHapticsEnabled(checked) }
            ),
            SettingSpec(
                id = "behavior_autoplay_skip",
                itemKey = "item_behavior_autoplay_skip",
                titleRes = R.string.setcat_autoplay_on_skip_title,
                subtitleRes = R.string.setcat_autoplay_on_skip_subtitle,
                category = SettingsCategory.BEHAVIOR,
                subscreenRoute = Screen.SettingsCategory.createRoute("behavior"),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("autoplay", "auto play", "skip", "next song", "previous song", "paused", "resume"),
                getValue = { it.autoplayOnSkip },
                onToggle = { viewModel, checked -> viewModel.setAutoplayOnSkip(checked) }
            ),

            // --- BACKUP & RESTORE CATEGORY ---
            SettingSpec(
                id = "backup_export",
                itemKey = "item_backup_export",
                titleRes = R.string.setcat_export_backup_title,
                category = SettingsCategory.BACKUP_RESTORE,
                subscreenRoute = Screen.SettingsCategory.createRoute("backup_restore"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("backup", "export", "save", "pxpl", "transfer")
            ),
            SettingSpec(
                id = "backup_import",
                itemKey = "item_backup_import",
                titleRes = R.string.setcat_import_backup_title,
                subtitleRes = R.string.setcat_import_backup_subtitle,
                category = SettingsCategory.BACKUP_RESTORE,
                subscreenRoute = Screen.SettingsCategory.createRoute("backup_restore"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("restore", "import", "backup", "recover")
            ),

            // --- DEVELOPER CATEGORY ---
            SettingSpec(
                id = "developer_experimental",
                itemKey = "item_developer_experimental",
                titleRes = R.string.setcat_experimental_title,
                subtitleRes = R.string.setcat_experimental_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.Experimental.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("experimental", "labs", "tweaks", "beta")
            ),
            SettingSpec(
                id = "developer_daily_mix",
                itemKey = "item_developer_daily_mix",
                titleRes = R.string.setcat_force_daily_mix_title,
                subtitleRes = R.string.setcat_force_daily_mix_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.SettingsCategory.createRoute("developer"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("daily mix", "regenerate", "playlist")
            ),
            SettingSpec(
                id = "developer_stats_regen",
                itemKey = "item_developer_stats_regen",
                titleRes = R.string.setcat_force_stats_title,
                subtitleRes = R.string.setcat_force_stats_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.SettingsCategory.createRoute("developer"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("statistics", "stats", "recalculate", "listening")
            ),
            SettingSpec(
                id = "developer_palette_regen",
                itemKey = "item_developer_palette_regen",
                titleRes = R.string.setcat_force_palette_title,
                subtitleRes = R.string.setcat_force_palette_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.SettingsCategory.createRoute("developer"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("palette", "regenerate", "colors", "album art")
            ),
            SettingSpec(
                id = "developer_test_setup",
                itemKey = "item_developer_test_setup",
                titleRes = R.string.setcat_test_setup_title,
                subtitleRes = R.string.setcat_test_setup_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.SettingsCategory.createRoute("developer"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("onboarding", "setup", "first run", "welcome")
            ),
            SettingSpec(
                id = "developer_test_crash",
                itemKey = "item_developer_test_crash",
                titleRes = R.string.setcat_trigger_crash_title,
                subtitleRes = R.string.setcat_trigger_crash_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.SettingsCategory.createRoute("developer"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("crash", "test", "diagnostics", "debug")
            ),
            SettingSpec(
                id = "developer_export_runlog",
                itemKey = "item_developer_export_runlog",
                titleRes = R.string.setcat_export_runlog_title,
                subtitleRes = R.string.setcat_export_runlog_subtitle,
                category = SettingsCategory.DEVELOPER,
                subscreenRoute = Screen.SettingsCategory.createRoute("developer"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("log", "export", "diagnostics", "debug", "runlog")
            ),

            // --- EQUALIZER & DEVICE ---
            SettingSpec(
                id = "equalizer_main",
                itemKey = "item_equalizer_main",
                titleRes = R.string.settings_category_equalizer_title,
                subtitleRes = R.string.settings_category_equalizer_subtitle,
                category = SettingsCategory.EQUALIZER,
                subscreenRoute = Screen.Equalizer.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("equalizer", "eq", "bass", "treble", "bands", "frequencies")
            ),
            SettingSpec(
                id = "device_capabilities",
                itemKey = "item_device_capabilities",
                titleRes = R.string.settings_category_device_capabilities_title,
                subtitleRes = R.string.settings_category_device_capabilities_subtitle,
                category = SettingsCategory.DEVICE_CAPABILITIES,
                subscreenRoute = Screen.DeviceCapabilities.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("device", "audio output", "capabilities", "formats", "hardware")
            ),
            SettingSpec(
                id = "device_capabilities_performance_diagnostics",
                itemKey = "item_device_capabilities_performance_diagnostics",
                titleRes = R.string.device_capabilities_advanced_diagnostics_title,
                subtitleRes = R.string.device_capabilities_advanced_diagnostics_description,
                category = SettingsCategory.DEVICE_CAPABILITIES,
                subscreenRoute = Screen.DeviceCapabilities.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("performance", "diagnostics", "lag", "stall", "report", "beta", "debug")
            ),

            // --- ABOUT ---
            SettingSpec(
                id = "updates_page",
                itemKey = "item_updates_page",
                titleRes = R.string.settings_updates_row_title,
                subtitleRes = R.string.settings_updates_row_subtitle,
                category = SettingsCategory.ABOUT,
                subscreenRoute = Screen.Updates.createRoute(),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("update", "version", "upgrade", "check", "更新", "升级", "版本")
            ),
            SettingSpec(
                id = "about_app",
                itemKey = "item_about_app",
                titleRes = R.string.setcat_about_pixelplayer_title,
                subtitleRes = R.string.setcat_about_pixelplayer_subtitle,
                category = SettingsCategory.ABOUT,
                subscreenRoute = Screen.SettingsCategory.createRoute("about"),
                type = SettingType.NAVIGABLE_CARD,
                keywordsStatic = listOf("version", "credits", "about", "info", "app")
            ),
            SettingSpec(
                id = "about_auto_check_updates",
                itemKey = "item_about_auto_check_updates",
                titleRes = R.string.setcat_auto_check_updates_title,
                subtitleRes = R.string.setcat_auto_check_updates_subtitle,
                category = SettingsCategory.ABOUT,
                subscreenRoute = Screen.Updates.createRoute(),
                type = SettingType.SWITCH,
                keywordsStatic = listOf("update", "check", "auto", "version", "startup", "更新"),
                getValue = { it.autoCheckUpdates },
                onToggle = { viewModel, checked -> viewModel.setAutoCheckUpdates(checked) }
            )
        )
    }
}
