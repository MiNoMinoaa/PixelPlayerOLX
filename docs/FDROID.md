# F-Droid Readiness

PixelPlayerOSS includes Fastlane metadata under `fastlane/metadata/android/en-US` for F-Droid and other open-source app stores.

A draft `fdroiddata` recipe is included at `metadata/com.lostf1sh.pixelplayeross.yml`. Copy or adapt it into the official `fdroiddata` repository when submitting.

## Current Status

Official F-Droid listing is not submitted from this repository yet. The project is prepared for submission with:

1. MIT license.
2. OSS-focused package name: `com.lostf1sh.pixelplayeross`.
3. No Firebase, Crashlytics, Play Store billing, ads, analytics, Cast, Wear OS, or Google Play Services runtime dependencies.
4. Optional network services documented in `PRIVACY.md`.
5. Release builds left unsigned when local signing keys are absent.
6. Store metadata in Fastlane format.
7. No ListenBrainz or Last.fm public scrobbling integration.

## Asset Licenses

General third-party notices are tracked in `THIRD_PARTY_NOTICES.md`; a short copy is also bundled at `app/src/main/assets/licenses/THIRD_PARTY_NOTICES.md`.

The dependency license review table is tracked in `docs/DEPENDENCY_LICENSES.md`.

| Asset | Source | License evidence |
| --- | --- | --- |
| `app/src/main/res/font/gflex_variable.ttf` | Google Sans Flex, `https://fonts.google.com/specimen/Google+Sans+Flex/license?preview.script=Latn` | Google Fonts license page declares SIL Open Font License 1.1; OFL text is included at `app/src/main/assets/licenses/OFL.txt`. |
| `app/src/main/res/font/genre_variable.ttf` | Roboto Flex, `https://github.com/googlefonts/roboto-flex` | SIL Open Font License 1.1 text is included at `app/src/main/assets/licenses/OFL.txt`. |

## Native And Binary Maven Artifacts

These artifacts can produce native `.so` files in the APK. Keep the source/license trail current when versions change.

The project source is MIT-licensed, but release APKs include `org.jellyfin.media3:media3-ffmpeg-decoder`, whose Maven POM declares GPL-3.0. Treat distributed APK artifacts as containing a GPL-3.0 component and preserve the source/license trail below.

| Artifact | Native library seen in APK | License/source evidence |
| --- | --- | --- |
| `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` | `libffmpegJNI.so` | Maven POM declares GPL-3.0 and SCM `https://github.com/jellyfin/jellyfin-androidx-media`. |
| `io.github.kyant0:taglib:1.0.6` | `libtaglib.so` | Maven POM declares Apache-2.0 and SCM `https://github.com/Kyant0/taglib`. |
| `androidx.graphics:graphics-shapes:1.1.0` | `libandroidx.graphics.path.so` | Google Maven POM declares Apache-2.0 and SCM `https://android.googlesource.com/platform/frameworks/support`. |

## JitPack Dependencies

`settings.gradle.kts` allows JitPack only for these groups:

| Group | Why it is allowed | License/source evidence |
| --- | --- | --- |
| `com.github.racra` | Direct dependency `libs.smooth.corner.rect.android.compose`. | POM and upstream repository declare MIT License: `https://github.com/racra/smooth-corner-rect-android-compose`. |
| `com.github.philburk` | Transitive dependency `com.github.philburk:jsyn` required by `androidx.media3:media3-exoplayer-midi`. | POM declares Apache-2.0 and SCM `https://github.com/philburk/jsyn`. |

## Local Verification Build

Build a universal unsigned release APK. Pass `pixelplayer.disableReleaseSigning=true` so local ignored signing files cannot affect the artifact:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false -Ppixelplayer.disableReleaseSigning=true
```

Expected artifact:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Run the standard checks before submitting a tagged release:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
```

## Submission Notes

Use the upstream repository and a signed release tag for the F-Droid metadata recipe. Keep ABI splits disabled for a single reproducible APK unless the app store recipe intentionally builds split artifacts.

Build from git source or `git archive`, not from a manual copy of the working tree. Ignored local artifacts such as `app/release/`, `vz-pixelplay.jks`, `keystore.properties`, and `local.properties` must not be included in any source tarball.
