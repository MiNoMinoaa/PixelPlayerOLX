<p align="center">
  <strong>English</strong> | <a href="README.md">简体中文</a>
</p>

<p align="center">
  <img src="assets/icon.png" alt="PixelPlayerOLX logo" width="160"/>
</p>

<h1 align="center">PixelPlayerOLX</h1>

<p align="center">
  <a href="https://github.com/MiNoMinoaa/PixelPlayerOLX/releases/latest">
    <img src="https://img.shields.io/github/v/release/MiNoMinoaa/PixelPlayerOLX?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest release">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPLv3 license">
</p>

<p align="center">
  An Android music player forked from <a href="https://github.com/PixelPlayerHQ/PixelPlayerOSS">PixelPlayerOSS</a>,
  adding <a href="https://github.com/lyswhut/lx-music-mobile">lx-music-mobile</a>-compatible online music sources (自定义音源) plus playlist-interoperable import/export and other features.
</p>

> **Note:** PixelPlayerOLX is an independent fork. It is **not** an official version of PixelPlayerOSS, lx-music-mobile, lx-lxwalnut-music-mobile, or any music platform, and it is not endorsed by their original authors.

<p align="center">This software implements lx source import and playback online features solely for personal use. Platform online features only take effect after a source script is imported and enabled. This software does not host, store, or own any copyrighted audio content.</p>

## Screenshots

<p align="center">
  <img src="assets/screenshot1.webp" alt="Screenshot 1" width="22%"/>
  <img src="assets/screenshot2.webp" alt="Screenshot 2" width="22%"/>
  <img src="assets/screenshot3.webp" alt="Screenshot 3" width="22%"/>
  <img src="assets/screenshot4.webp" alt="Screenshot 4" width="22%"/>
</p>

## Introduction

PixelPlayerOLX is a fork of [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS). It keeps the upstream project's local-playback, self-hostable foundation and adds an online music source (自定义音源 / "LX Music") system whose design references [lx-music-mobile](https://github.com/lyswhut/lx-music-mobile) and [lx-lxwalnut-music-mobile](https://github.com/WalnutBai/lx-lxwalnut-music-mobile).

Local playback, self-hosted libraries, and offline use work by default. The online source features are optional and are only active after you import and enable a source script (or sign in to a supported platform).

Package name: `com.minoppol.music`


PixelPlayerOSS keeps the player FOSS-oriented and removes integrations that are not part of that direction (Telegram, NetEase, QQ Music, Google Drive, Gemini, Cast, Wear OS, Play Store billing, Firebase, Crashlytics, and Google Play Services runtime dependencies).

PixelPlayerOLX builds on that FOSS base and adds back an *optional, user-controlled* online music source system, implemented with reference to [lx-music-mobile](https://github.com/lyswhut/lx-music-mobile) and [lx-lxwalnut-music-mobile](https://github.com/WalnutBai/lx-lxwalnut-music-mobile):

- A sandboxed **QuickJS** script engine that runs lx-music-mobile-compatible source scripts (音源脚本).
- Built-in official API paths for several platforms, used alongside scripts to pick the best available quality.
- Basic login for platforms that require it.
- `.lxmc` playlist import/export so playlists can be shared with lx-music-mobile.

The offline/self-hosted experience remains the core of the app.

## Features

### Inherited from PixelPlayerOSS

| Area | Highlights |
| --- | --- |
| Playback | Media3 playback engine, FFmpeg support, gapless playback, crossfade, custom transitions, queue controls, shuffle, repeat, sleep timer, external file playback |
| Library | Local scanning for MP3, FLAC, AAC, OGG, WAV, M4A, albums, artists, genres, folders, favorites, playlists, stats, metadata editing |
| Self-hosted | Navidrome/Subsonic and Jellyfin login, sync, streaming, artwork, and app-private offline downloads |
| Lyrics | Embedded lyrics, local `.lrc` files, lyrics import/editing, optional LRCLIB lookup |
| Artwork | Local artwork, album-art palette extraction, optional Deezer artist image lookup |
| Metadata | On-demand MusicBrainz matching for recording, release, and artist identifiers |
| UI | Jetpack Compose, Material 3, dynamic color, light/dark themes, Glance widgets, animated player surfaces |
| Backup | Preferences, playlists, favorites, lyrics, stats, and app state backup/restore |

### Added by PixelPlayerOLX

| Area | Highlights |
| --- | --- |
| Online sources | lx-music-mobile-compatible source scripts (音源脚本), imported from a `.js` file or URL, managed from the in-app "LX Music" tab |
| Script engine | Sandboxed QuickJS runtime (`user-api-preload.js` bridge) with per-script isolation and the lx-music `lx_setup` API |
| Platforms | Built-in web browsing paths for major platforms; source scripts can declare additional sources |
| Quality | Per-platform quality selection (master, atmos, 24-bit FLAC, FLAC, 320 kbps, 128 kbps) with automatic fallback/downgrade |
| Playlists | Import/export `.lxmc` (落雪 / lx-music) playlists for interchange with lx-music-mobile |
| Downloads | Download online tracks with quality selection, plus a cache/offline path for source songs |
| Accounts | Public data display after basic web-based login for supported platforms |

## LX Music Sources (自定义音源)

The "LX Music" tab lets you search and play music from online platforms through **source scripts**. A source script is a plain JavaScript file that implements the lx-music-mobile source API (`lx_setup`).

- Scripts run in an isolated QuickJS instance so one slow or failing script does not affect the others.
- You can enable multiple sources; the preferred script is tried first, then enabled backup scripts, then the built-in official API path — the best available quality wins.
- You can import scripts from a local `.js` file or from a URL, enable/disable them, and delete them (long-press a script).

> **Security:** Some scripts require you to sign in. Only use scripts you trust.

## Online Services & Data Sources

| Service | Purpose | Default |
| --- | --- | --- |
| Navidrome/Subsonic | Self-hosted library sync, streaming, and offline downloads | User login required |
| Jellyfin | Self-hosted library sync, streaming, and offline downloads | User login required |
| LX Music sources | Optional online search/playback via imported source scripts | Off until a script is enabled |
| Various platforms | Official web service | Off until used |
| MusicBrainz | On-demand metadata matching and identifier enrichment | Only when requested |
| LRCLIB | Search online lyrics when local or embedded lyrics are missing | ON |
| Deezer | Fetch missing artist artwork and cache it locally | OFF |
| ListenBrainz | Optional scrobbling (incl. self-hosted Maloja-compatible servers) | Off |

LRCLIB and Deezer are partially enabled by default and can be toggled later from `Settings > Music Management > Optional online services`.

## Download

Releases are published on GitHub:

```text
https://github.com/MiNoMinoaa/PixelPlayerOLX/releases
```

To install a release, download the APK for your device and install it: `arm64-v8a` fits most modern devices, and `armeabi-v7a` is for older 32-bit ones.

## Upstream Projects & Attribution

PixelPlayerOLX is a fork of [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS). Some online-source features are implemented with reference to [lx-music-mobile](https://github.com/lyswhut/lx-music-mobile) and [lx-lxwalnut-music-mobile](https://github.com/WalnutBai/lx-lxwalnut-music-mobile). The relevant licenses and copyrights are retained.

| Project | Repository | License | Copyright / attribution |
| --- | --- | --- | --- |
| PixelPlayerOSS | https://github.com/PixelPlayerHQ/PixelPlayerOSS | GPL-3.0 | Copyright © 2026 Theo Vilardo |
| Pixel Player (upstream of PixelPlayerOSS) | — | — | Logo/design attributed to **Aureal** |
| lx-music-mobile | https://github.com/lyswhut/lx-music-mobile | Apache-2.0 | Copyright © lyswhut |
| lx-lxwalnut-music-mobile | https://github.com/WalnutBai/lx-lxwalnut-music-mobile | Apache-2.0 | Copyright © WalnutBai |

The LX Music source-script system in this project is compatible with the lx-music-mobile source API and is implemented with reference to it (Apache-2.0). No code is forked from lx-music-mobile or lx-lxwalnut-music-mobile.

## Icon Attribution and Third-Party Notices

This project's app icon is a derivative work based on icon assets from the following open-source music player projects:

- **lx-music-mobile**  
  Repository: https://github.com/lyswhut/lx-music-mobile  
  License: Apache License 2.0  
  The original icon assets are used and modified under the Apache License 2.0.  
  lx-music-mobile also states that some images, fonts, and other resources included in the project may come from the Internet and may not be owned by the project itself.

- **PixelPlayerOSS**  
  Repository: https://github.com/PixelPlayerHQ/PixelPlayerOSS  
  License: GNU General Public License v3.0 (GPL-3.0)  
  The original icon/logo design is attributed to **Aureal**, as stated in the PixelPlayerOSS project.

The combined/modified icon in this repository is provided for identification, attribution, and derivative-work documentation purposes only.  
This project is **not** an official version of lx-music-mobile or PixelPlayerOSS, and it is not endorsed by their original authors.

All respective copyrights belong to their original owners.  
If you are the copyright holder of any asset used here and wish to request removal or modification, please open an issue or contact the repository maintainer.

## Disclaimer — Respect Copyright

The online music source feature retrieves data from third-party platforms through their public APIs and through user-provided source scripts. This project:

- Does not host, store, or own any copyrighted audio content.
- Cannot verify the legality, accuracy, or licensing of content returned by third-party sources or scripts.
- Is provided free of charge for technical learning and research only, and is not affiliated with or endorsed by any music platform.
- Is intended to be used in compliance with local laws and the terms of the platforms you access.

**Please respect copyright and support the official platforms.** Do not use this software in ways that infringe the rights of artists, labels, or platforms. Only use source scripts and accounts you are authorized to use.

## Contributing

Contributions are welcome. Open an issue or pull request with a focused change and include test/build results when possible.

Useful local checks:

```sh
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions, and [SECURITY.md](SECURITY.md) for reporting vulnerabilities.

Privacy policy: [PRIVACY.md](PRIVACY.md)

## Acknowledgements

Thanks to the following projects and their maintainers for their contributions to the open-source community:

- [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS)
- [lx-music-mobile](https://github.com/lyswhut/lx-music-mobile)
- [lx-lxwalnut-music-mobile](https://github.com/WalnutBai/lx-lxwalnut-music-mobile)
- All source-script maintainers

## License

PixelPlayerOLX is licensed under the [GNU General Public License v3.0](LICENSE) (`SPDX-License-Identifier: GPL-3.0-or-later`).

```
PixelPlayerOLX
Copyright (C) 2026 MiNoMinoaa

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

PixelPlayerOLX is a fork of PixelPlayerOSS (GPL-3.0, Copyright © 2026 Theo Vilardo). Some online-source features are implemented with reference to lx-music-mobile (Apache-2.0, Copyright © lyswhut) and lx-lxwalnut-music-mobile (Apache-2.0, Copyright © WalnutBai), and are used under their respective licenses.

Distributed APKs include third-party components under their own licenses. In particular, the optional FFmpeg decoder dependency `org.jellyfin.media3:media3-ffmpeg-decoder` is GPL-3.0; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

<p align="center">
  Maintained by <a href="https://github.com/MiNoMinoaa">MiNoMinoaa</a>
</p>
