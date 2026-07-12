# PixelPlayerOSS ProGuard/R8 rules.
#
# Every keep rule here exists for a reason — usually reflection, JNI, or a
# serialized contract. Before adding a broad `-keep class x.** { *; }`, check
# whether the library already ships consumer rules (most AndroidX/Kotlin/Ktor
# artifacts do) and prefer `-dontwarn` for compile-time-only references.

# ─── Stack traces ────────────────────────────────────────────────────────────
# Keep file/line info so release crash reports (CrashHandler) stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures + annotations: required by Gson TypeToken, Room, and Hilt.
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*

# ─── Audio metadata (TagLib JNI + JAudioTagger reflection) ───────────────────
# TagLib is accessed over JNI; native code looks classes/members up by name.
-keep class com.kyant.taglib.** { *; }

# JAudioTagger instantiates ID3 frame bodies (FrameBodyTIT2, FrameBodyTXXX, ...)
# and related tag types via Class.forName based on frame IDs found in files.
# Keeping the tag surface is required; the audio.* parsers are referenced
# directly and can be shrunk normally.
-keep class org.jaudiotagger.tag.** { *; }
-dontwarn org.jaudiotagger.**

# JAudioTagger references desktop-Java APIs (java.awt, javax.imageio,
# javax.sound.sampled, javax.swing) that don't exist on Android. Those code
# paths never execute on-device — suppress the missing-class warnings only.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.sound.sampled.**
-dontwarn javax.swing.filechooser.FileFilter
-dontwarn javax.lang.model.**

# ─── Media3 decoder extensions ───────────────────────────────────────────────
# FFmpeg decoder is wired up reflectively by DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }

# MIDI extension + its JSyn synthesizer backend.
-keep class androidx.media3.decoder.midi.** { *; }
-keep class com.jsyn.** { *; }
-keep class com.softsynth.** { *; }
-dontwarn com.jsyn.**
-dontwarn com.softsynth.**

# ─── Gson (backup/restore + queue snapshot persistence) ──────────────────────
# Domain models are (de)serialized reflectively by Gson.
-keepclassmembers class com.lostf1sh.pixelplayeross.data.model.** { *; }

# Generic type capture for TypeToken subclasses in release builds.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Backup payload types are part of the persisted .pxpl file contract — field
# names must remain stable across releases.
-keep class com.lostf1sh.pixelplayeross.data.preferences.PreferenceBackupEntry { *; }
-keep class com.lostf1sh.pixelplayeross.data.backup.model.** { *; }
-keep class com.lostf1sh.pixelplayeross.data.backup.module.** { *; }
-keep class com.lostf1sh.pixelplayeross.data.database.FavoritesEntity { *; }
-keep class com.lostf1sh.pixelplayeross.data.database.SongEngagementEntity { *; }
-keep class com.lostf1sh.pixelplayeross.data.database.LyricsEntity { *; }
-keep class com.lostf1sh.pixelplayeross.data.database.SearchHistoryEntity { *; }
-keep class com.lostf1sh.pixelplayeross.data.database.TransitionRuleEntity { *; }

# ─── Ktor embedded server (CloudStreamProxy) ─────────────────────────────────
# Server engines are discovered via ServiceLoader and internal wiring is
# reached reflectively; stripping it breaks the local stream proxy in release.
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.cio.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**

# ─── Lyrics romanization ─────────────────────────────────────────────────────
# Kuromoji loads its bundled dictionary through classloader lookups.
-keep class com.atilika.kuromoji.** { *; }
-keepnames class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**

-keep class net.sourceforge.pinyin4j.** { *; }
-keepclassmembers class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# ─── Glance widgets ──────────────────────────────────────────────────────────
# ActionCallback implementations are instantiated reflectively by Glance.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# ─── Log stripping ───────────────────────────────────────────────────────────
# Remove VERBOSE/DEBUG/INFO calls at the bytecode level in release builds
# (ReleaseTree only logs WARN+ anyway); this also drops the string-building
# work at each call site. Timber.tag() is side-effect free, so once the
# following d()/i() call is stripped the tag() call is removed too.
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static timber.log.Timber$Tree tag(java.lang.String);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ─── Removed rules (kept here as history so they don't get cargo-culted back) ─
# * `-keep class kotlin.reflect.** { *; }` — kotlin-reflect ships its own
#   consumer rules; the blanket keep forced the entire library into the APK.
# * `-keep class io.netty...` + io.netty.internal.tcnative / org.eclipse.jetty
#   dontwarns — Netty is no longer on the runtime classpath (CIO engine only).
# * `-keep class javax.lang.model.**` / `com.squareup.javapoet.**` /
#   `javax.sound.sampled.**` — compile-time or desktop-only APIs; -dontwarn
#   covers the dangling references, keeping them only added dead weight.
# * `-keep class org.slf4j.** { *; }` — referenced directly by Ktor, so R8
#   retains what is used; providers resolve via ServiceLoader.
# * `-keep class kotlin.Metadata { *; }` — covered by kotlin consumer rules.
