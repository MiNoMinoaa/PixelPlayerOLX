package com.minoppol.music.data.lxmusic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs

/**
 * Golden vectors for the QRC path were produced by running the ENCRYPT direction
 * of lx-music's non-standard 3DES (qrcDecode.js tables, fixed key, zlib
 * Z_SYNC_FLUSH) in node, then verified to round-trip through the original
 * decrypt implementation (qrc_gen/verify.mjs, 4/4 OK).
 */
class LxLyricDecodersTest {

    // ------------------------------------------------------------------
    // QRC golden vectors (see qrc_gen/vectors.json)
    // ------------------------------------------------------------------

    private val qrcVector0Plain =
        "[offset:0]\n[1000,2000]<0,300,0>你<300,300,0>好<600,800,0>世界\n[4000,1000]<0,500,0>再<500,500,0>见\n"
    private val qrcVector0Hex =
        "318967b79244609087efcf9e46cbcc2071badc40933dccbdea5adf6ce7de393829db1e5be31b926cc7e0531cb5ae8bd550aa509b414d23f6b702a758fca5a9389c5835ea53cf1e50eab9ff628a0850e55cf5cef7e099f20b"
    private val qrcVector1Plain =
        "[offset:0]\n[500,800]<0,200,0>ラ<200,300,0>イ<500,300,0>ト\n"
    private val qrcVector1Hex =
        "318967b79244609060db526b3c32cbbc0f1e5c3dad42164da24cdcafe51a9810efa4e731a23ba5055d1f6a7360ea98363f9bf460e5994bca"

    @Test
    fun `decodeQrc golden vector 0 round-trips`() {
        val decoded = LxLyricDecoders.decodeQrc(qrcVector0Hex)
        assertThat(decoded).isEqualTo(qrcVector0Plain)
    }

    @Test
    fun `decodeQrc golden vector 1 round-trips`() {
        val decoded = LxLyricDecoders.decodeQrc(qrcVector1Hex)
        assertThat(decoded).isEqualTo(qrcVector1Plain)
    }

    @Test
    fun `decodeQrc rejects odd-length and empty input`() {
        assertThat(LxLyricDecoders.decodeQrc("")).isNull()
        assertThat(LxLyricDecoders.decodeQrc("abc")).isNull()
    }

    // ------------------------------------------------------------------
    // QRC → Enhanced LRC conversion
    // ------------------------------------------------------------------

    @Test
    fun `convertQrc produces absolute-timestamp enhanced lrc`() {
        val converted = LxLyricDecoders.convertQrc(qrcVector0Plain)!!
        // line starts at 1000ms, words at 1000/1300/1600; second line 4000/4500
        assertThat(converted).contains("[00:01.000]<00:01.000>你<00:01.300>好<00:01.600>世界")
        assertThat(converted).contains("[00:04.000]<00:04.000>再<00:04.500>见")
    }

    @Test
    fun `convertQrc strips xml wrapper`() {
        val wrapped = "<?xml version=\"1.0\" encoding=\"utf-8\"?><QrcInfos><QrcHeadInfo Title=\"x\"/><LyricInfo LyricCount=\"1\" LyricType=\"1\">" +
            "<Lyric_1 LyricType=\"1\" LyricContent=\"[1000,2000]<0,500,0>测<500,500,0>试\"/></LyricInfo></QrcInfos>"
        val converted = LxLyricDecoders.convertQrc(wrapped)!!
        assertThat(converted).contains("[00:01.000]<00:01.000>测<00:01.500>试")
        assertThat(converted).doesNotContain("xml")
    }

    // ------------------------------------------------------------------
    // YRC → Enhanced LRC conversion
    // ------------------------------------------------------------------

    @Test
    fun `convertYrc converts json lines to absolute word timestamps`() {
        val yrc = listOf(
            """{"t":1200,"c":[{"tx":"你","t":1200},{"tx":"好","t":1500},{"tx":"世界","t":1800}]}""",
            """{"t":5000,"c":[{"tx":"再","t":5000},{"tx":"见","t":5500}]}""",
        ).joinToString("\n")
        val converted = LxLyricDecoders.convertYrc(yrc)!!
        assertThat(converted).contains("[00:01.200]<00:01.200>你<00:01.500>好<00:01.800>世界")
        assertThat(converted).contains("[00:05.000]<00:05.000>再<00:05.500>见")
    }

    @Test
    fun `convertYrc keeps bracket header lines`() {
        val yrc = "[offset:0]\n" + """{"t":100,"c":[{"tx":"a","t":100}]}"""
        val converted = LxLyricDecoders.convertYrc(yrc)!!
        assertThat(converted).startsWith("[offset:0]")
        assertThat(converted).contains("[00:00.100]<00:00.100>a")
    }

    @Test
    fun `convertYrc returns null for garbage`() {
        assertThat(LxLyricDecoders.convertYrc("not json at all")).isNull()
    }

    @Test
    fun `convertYrc handles legacy tuple lines without leaking ms tags into text`() {
        // Regression: legacy netease YRC is [absStartMs,lineDurMs](absWordMs,wordDurMs,flag)word
        val legacy = "[71060,170](71060,170,0)啊\n[71230,180](71230,180,0)哦\n"
        val converted = LxLyricDecoders.convertYrc(legacy)!!
        assertThat(converted).contains("[01:11.060]<01:11.060>啊")
        assertThat(converted).contains("[01:11.230]<01:11.230>哦")
        assertThat(converted).doesNotContain("(71060")
        assertThat(converted).doesNotContain("71060,170,0")
    }

    @Test
    fun `convertYrc keeps voice marker from legacy tuple lines`() {
        val legacy = "[71060,170](男)(71060,170,0)啊(71230,100,0)耶\n"
        val converted = LxLyricDecoders.convertYrc(legacy)!!
        assertThat(converted).startsWith("[01:11.060](男)")
        assertThat(converted).contains("<01:11.060>啊<01:11.230>耶")
        assertThat(converted).doesNotContain(",170,0)")
    }

    // ------------------------------------------------------------------
    // KRC decode + conversion
    // ------------------------------------------------------------------

    /** Builds a KRC payload: zlib-compress → XOR key → prepend 4 junk bytes → base64. */
    private fun buildKrcPayload(krcText: String): String {
        val compressed = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(krcText.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val key = byteArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
        )
        val body = ByteArray(compressed.size)
        for (i in compressed.indices) body[i] = (compressed[i].toInt() xor key[i % 16].toInt()).toByte()
        val full = ByteArray(4 + body.size)
        full[0] = 0x40; full[1] = 0x41; full[2] = 0x42; full[3] = 0x43
        System.arraycopy(body, 0, full, 4, body.size)
        return Base64.getEncoder().encodeToString(full)
    }

    @Test
    fun `decodeKrc and convertKrc round-trip`() {
        val krc = "[id:\$krc]\n[1000,2000]<0,300,0>你<300,300,0>好<600,800,0>世界\n[4000,1000]<0,500,0>再<500,500,0>见\n"
        val payload = buildKrcPayload(krc)
        val decoded = LxLyricDecoders.decodeKrc(payload)!!
        assertThat(decoded).contains("[1000,2000]")
        val conversion = LxLyricDecoders.convertKrc(decoded)!!
        assertThat(conversion.lrc).contains("[00:01.000]你好世界")
        assertThat(conversion.lrc).contains("[00:04.000]再见")
        assertThat(conversion.wordLrc).contains("[00:01.000]<00:01.000>你<00:01.300>好<00:01.600>世界")
        assertThat(conversion.wordLrc).contains("[00:04.000]<00:04.000>再<00:04.500>见")
    }

    @Test
    fun `convertKrc parses language header for translation`() {
        val langJson = """{"content":[{"type":1,"lyricContent":[["你好世界"],["再见"]]}],"type":1}"""
        val langB64 = Base64.getEncoder().encodeToString(langJson.toByteArray(Charsets.UTF_8))
        val krc = "[language:$langB64]\n[1000,2000]<0,300,0>你好世界\n[4000,1000]<0,500,0>再见\n"
        val payload = buildKrcPayload(krc)
        val conversion = LxLyricDecoders.convertKrc(LxLyricDecoders.decodeKrc(payload)!!)!!
        assertThat(conversion.translationLrc).isNotNull()
        assertThat(conversion.translationLrc!!).contains("[00:01.000]你好世界")
        assertThat(conversion.translationLrc!!).contains("[00:04.000]再见")
    }

    @Test
    fun `convertYrc keeps untagged leading text of each line`() {
        val legacy = "[71060,300]第一(71360,180,0)句\n[72000,300]第二(72300,180,0)句\n"
        val converted = LxLyricDecoders.convertYrc(legacy)!!
        assertThat(converted).contains("[01:11.060]<01:11.060>第一<01:11.360>句")
        assertThat(converted).contains("[01:12.000]<01:12.000>第二<01:12.300>句")
    }

    @Test
    fun `convertKrc strips non numeric tags and dollar separators`() {
        val krc = "[1000,2000]<0,300,0>你<R:abc>$<300,300,0>好\n"
        val conversion = LxLyricDecoders.convertKrc(krc)!!
        assertThat(conversion.lrc).contains("[00:01.000]你好")
        assertThat(conversion.lrc).doesNotContain("<R:")
        assertThat(conversion.lrc).doesNotContain("$")
    }

    @Test
    fun `convertKuwoLrcx splits three field word tags`() {
        val lrcx = "[kuwo:17]\n[00:01.00]你好<10,4,0>世界<14,4,0>啊\n"
        val converted = LxLyricDecoders.convertKuwoLrcx(lrcx)!!
        assertThat(converted).doesNotContain("<10,4,0>")
        assertThat(converted).contains("<00:01.007>世界")
    }

    // ------------------------------------------------------------------
    // Kuwo LRCx decode + conversion
    // ------------------------------------------------------------------

    /** Builds a kuwo mobi.s response: headers + CRLFCRLF + zlib(base64(xor(lrcx)))  */
    private fun buildKuwoResponse(lrcxText: String): ByteArray {
        val xored = ByteArray(lrcxText.toByteArray(Charsets.UTF_8).size)
        val bytes = lrcxText.toByteArray(Charsets.UTF_8)
        val key = "yeelion".toByteArray(Charsets.UTF_8)
        var i = 0
        while (i < bytes.size) {
            var j = 0
            while (j < key.size && i < bytes.size) {
                xored[i] = (bytes[i].toInt() xor key[j].toInt()).toByte()
                i++; j++
            }
        }
        val b64 = Base64.getEncoder().encodeToString(xored).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(b64) }
        }.toByteArray()
        val header = "tp=content\r\nCS=qwertyuiop\r\n\r\n".toByteArray(Charsets.UTF_8)
        return header + compressed
    }

    @Test
    fun `decodeKuwoLrcx and convertKuwoLrcx round-trip`() {
        // [kuwo:17] → octal 17 = decimal 15 → offset1=1, offset2=5 (non-zero pair)
        val lrcx = "[kuwo:17]\n[ti:test]\n[00:01.00]你好<10,4>世界\n"
        val response = buildKuwoResponse(lrcx)
        val decoded = LxLyricDecoders.decodeKuwoLrcx(response, isLrcx = true)!!
        assertThat(decoded).contains("[kuwo:17]")
        val converted = LxLyricDecoders.convertKuwoLrcx(decoded)!!
        // start=(10+4)/2=7ms relative to line start 1000ms → word at <00:01.007>
        assertThat(converted).contains("[ti:test]")
        assertThat(converted).contains("[00:01.00]你好<00:01.007>世界")
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @Test
    fun `fixTimeTag aligns translation timestamps to source`() {
        val source = "[00:01.000]你好\n[00:04.000]再见"
        val target = "[00:01.050]hello\n[00:04.020]bye"
        val fixed = LxLyricDecoders.fixTimeTag(source, target)
        assertThat(fixed).isEqualTo("[00:01.000]hello\n[00:04.000]bye")
    }

    @Test
    fun `fixColonFraction rewrites colon fractions`() {
        assertThat(LxLyricDecoders.fixColonFraction("[00:12:34]词"))
            .isEqualTo("[00:12.34]词")
    }

    @Test
    fun `convertKuwoLrcx produces word tags for all lines`() {
        val lrcx = "[kuwo:17]\n[00:01.00]你好<10,4>世界<14,4>啊\n[00:03.00]测试<10,4>二<14,4>行\n"
        val converted = LxLyricDecoders.convertKuwoLrcx(lrcx)!!
        assertThat(converted).contains("[00:01.00]你好<00:01.007>世界")
        assertThat(converted).contains("[00:03.00]测试<00:03.007>二")
    }

    @Test
    fun `convertKuwoLrcx adds line start to relative word times`() {
        // real kuwo payload shape: [kuwo:023] → octal 19 → offset1=1, offset2=9
        val lrcx = "[kuwo:023]\n[ver:v1.0]\n[00:01.784]<1305,-1305>曲：<1522,-1232>熊<1911,-1321>磊\n"
        val converted = LxLyricDecoders.convertKuwoLrcx(lrcx)!!
        assertThat(converted).contains("[00:01.784]<00:01.784>曲：<00:01.929>熊<00:02.079>磊")
    }

    @Test
    fun `splitKuwoTranslationLines pairs bilingual lines with their preceding main line`() {
        val lrc = listOf(
            "[ti:Lemon]",
            "[00:00.000]<00:00.000>Lemon <00:00.000>- <00:00.000>米<00:00.000>津<00:00.000>玄<00:00.000>師",
            "[00:01.547]<00:01.547>夢<00:01.713>な<00:01.969>ら<00:02.559>ば",
            "[00:02.880]<00:02.880>如<00:02.880>果<00:02.880>只<00:02.880>是<00:02.880>一<00:02.880>场<00:02.880>梦",
            "[00:02.880]<00:02.880>ど<00:03.440>れ<00:03.576>ほ",
            "[00:06.882]<00:06.882>那<00:06.882>该<00:06.882>有<00:06.882>多<00:06.882>好",
            "[00:06.882]<00:06.882>未<00:08.234>だ<00:08.936>に",
        ).joinToString("\n")
        val split = LxLyricDecoders.splitKuwoTranslationLines(lrc)
        assertThat(split.wordLrc).contains("[00:01.547]<00:01.547>夢")
        assertThat(split.wordLrc).contains("[00:02.880]<00:02.880>ど")
        assertThat(split.wordLrc).contains("[00:06.882]<00:06.882>未")
        assertThat(split.wordLrc).doesNotContain("[00:00.000]")
        assertThat(split.wordLrc).contains("[ti:Lemon]")
        assertThat(LxLyricDecoders.stripWordTags(split.wordLrc)).doesNotContain("如果")
        assertThat(LxLyricDecoders.stripWordTags(split.wordLrc)).doesNotContain("那该")
        assertThat(split.plainLrc).contains("[00:01.547]夢")
        assertThat(split.plainLrc).doesNotContain("<")
        assertThat(split.translationLrc).contains("[00:01.547]如果只是一场梦")
        assertThat(split.translationLrc).contains("[00:02.880]那该有多好")
        assertThat(split.translationLrc).doesNotContain("[00:06.882]")
    }

    @Test
    fun `splitKuwoTranslationLines falls back to all main when no line has real word timing`() {
        val lrc = listOf(
            "[00:01.000]<00:01.000>一<00:01.000>句",
            "[00:02.000]<00:02.000>两<00:02.000>句",
        ).joinToString("\n")
        val split = LxLyricDecoders.splitKuwoTranslationLines(lrc)
        assertThat(split.wordLrc).isEqualTo(lrc)
        assertThat(split.translationLrc).isEmpty()
    }

    @Test
    fun `splitKuwoTranslationLines drops blank spacer lines from both outputs`() {
        val lrc = listOf(
            "[00:01.547]<00:01.547>夢<00:01.713>な<00:01.969>ら",
            "[00:02.880]<00:02.880> <00:02.880> <00:02.880> ",
            "[00:02.880]<00:02.880>ど<00:03.440>れ",
        ).joinToString("\n")
        val split = LxLyricDecoders.splitKuwoTranslationLines(lrc)
        assertThat(split.wordLrc).contains("[00:02.880]<00:02.880>ど")
        assertThat(split.wordLrc.lines()).hasSize(2)
        assertThat(split.translationLrc).isEmpty()
    }

    // ------------------------------------------------------------------
    // Real server payloads (src/test/resources/realpayloads) — regression
    // tests for the on-device symptom fixes. Expectations are derived from
    // the payloads themselves, not hand-copied content.
    // ------------------------------------------------------------------

    private fun realPayload(name: String): String {
        val resource = javaClass.classLoader.getResource("realpayloads/$name")
        org.junit.jupiter.api.Assumptions.assumeTrue(
            resource != null,
            "real payload fixture not present (kept local, not committed): $name"
        )
        return resource!!.readText()
    }

    @Test
    fun `convertQrc on real QQ payload yields lines parseLyrics restores as word-timed`() {
        val qrc = realPayload("qq_qrc_decoded.txt")
        val converted = LxLyricDecoders.convertQrc(qrc)!!
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(converted)
        assertThat(parsed.synced.isNullOrEmpty()).isFalse()
        val synced = parsed.synced!!
        val withWords = synced.filter { !it.words.isNullOrEmpty() }
        assertThat(withWords).isNotEmpty()
        // every converted QRC line's first word starts exactly at the line time
        withWords.forEach { line ->
            assertThat(line.words!!.first().time).isEqualTo(line.time)
            assertThat(line.line.isNotBlank()).isTrue()
        }
        // the XML envelope must be gone
        assertThat(converted).doesNotContain("LyricContent")
    }

    @Test
    fun `extractQrcLyricContent keeps plain LRC and strips the XML envelope`() {
        val plain = "[00:00.000]hello\n[00:02.000]world\n"
        assertThat(LxLyricDecoders.extractQrcLyricContent(plain)).isEqualTo(plain.trim())
        val enveloped = "<QrcInfos>\"LyricContent=\"[00:00.000]hi\"/>tail"
        val extracted = LxLyricDecoders.extractQrcLyricContent(enveloped)
        assertThat(extracted).doesNotContain("LyricContent")
        assertThat(extracted).contains("[00:00.000]hi")
    }

    @Test
    fun `decodeKrcBytes and convertKrc on real kg payload yield word-timed lyrics`() {
        val krcBase64 = realPayload("kg_krc_content.txt").trim()
        val decoded = LxLyricDecoders.decodeKrc(krcBase64)
        assertThat(decoded).isNotNull()
        val conversion = LxLyricDecoders.convertKrc(decoded!!)!!
        assertThat(conversion.wordLrc).isNotEmpty()
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(conversion.wordLrc)
        assertThat(parsed.synced.isNullOrEmpty()).isFalse()
        val synced = parsed.synced!!
        val withWords = synced.filter { !it.words.isNullOrEmpty() }
        assertThat(withWords).isNotEmpty()
        withWords.forEach { line ->
            assertThat(line.words!!.first().time).isEqualTo(line.time)
        }
    }

    @Test
    fun `splitKuwoTranslationLines on real kuwo Lemon payload pairs translation with its own main line`() {
        val lrcx = realPayload("kuwo_lemon_lrcx.txt")
        val converted = LxLyricDecoders.convertKuwoLrcx(lrcx)!!
        val split = LxLyricDecoders.splitKuwoTranslationLines(converted)
        assertThat(split.translationLrc).contains("[00:01.547]如果只是一场梦")
        assertThat(split.translationLrc).contains("[00:02.880]那该有多好")
        assertThat(LxLyricDecoders.stripWordTags(split.wordLrc)).doesNotContain("如果只是一场梦")
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(split.wordLrc)
        val synced = parsed.synced!!
        val withWords = synced.filter { !it.words.isNullOrEmpty() }
        assertThat(withWords.size).isGreaterThan(10)
        assertThat(split.translationLrc.lines().size).isGreaterThan(10)
    }

    @Test
    fun `splitKuwoTranslationLines on real kuwo Something Just Like This payload keeps minute two word times`() {
        val lrcx = realPayload("kuwo_sjlt_lrcx.txt")
        val converted = LxLyricDecoders.convertKuwoLrcx(lrcx)!!
        assertThat(converted).doesNotContain("<120:")
        assertThat(converted).doesNotContain("<180:")
        val split = LxLyricDecoders.splitKuwoTranslationLines(converted)
        assertThat(split.translationLrc).contains("[01:59.708]我想要的不过是这些")
        assertThat(split.translationLrc).contains("[02:37.694]你想去往何方")
        assertThat(LxLyricDecoders.stripWordTags(split.wordLrc)).doesNotContain("我想要的不过是这些")
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(split.wordLrc)
        val synced = parsed.synced!!
        val withWords = synced.filter { !it.words.isNullOrEmpty() }
        assertThat(withWords.size).isGreaterThan(60)
        assertThat(split.translationLrc.lines().size).isGreaterThan(50)
        val minuteTwo = synced.filter { it.time in 120_000..180_000 }
        assertThat(minuteTwo).isNotEmpty()
        minuteTwo.forEach { line ->
            assertThat(line.line).doesNotContain("<")
            if (!line.words.isNullOrEmpty()) {
                assertThat(line.words!!.first().time).isEqualTo(line.time)
            }
        }
    }

    @Test
    fun `normalizeNeteaseLrcField converts mixed JSON+LRC payload to plain LRC`() {
        val mixed = realPayload("wy_mixed_json_lrc.txt")
        assertThat(mixed.lineSequence().any { it.trim().startsWith("{") }).isTrue()
        val normalized = LxLyricDecoders.normalizeNeteaseLrcField(mixed)!!
        assertThat(normalized.lineSequence().none { it.trim().startsWith("{") }).isTrue()
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(normalized)
        assertThat(parsed.synced.isNullOrEmpty()).isFalse()
        assertThat(parsed.synced).isNotNull()
    }

    @Test
    fun `normalizeNeteaseLrcField keeps plain LRC unchanged`() {
        val plain = "[00:00.000]a\n[00:01.000]b\n"
        assertThat(LxLyricDecoders.normalizeNeteaseLrcField(plain)).isEqualTo(plain)
        assertThat(LxLyricDecoders.normalizeNeteaseLrcField("")).isEqualTo("")
        assertThat(LxLyricDecoders.normalizeNeteaseLrcField(null)).isNull()
    }

    @Test
    fun `convertYrc on real WY tuple payload yields word-timed lines`() {
        val yrc = realPayload("wy_yrc_tuple.txt")
        val converted = LxLyricDecoders.convertYrc(yrc)!!
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(converted)
        assertThat(parsed.synced.isNullOrEmpty()).isFalse()
        val withWords = parsed.synced!!.filter { !it.words.isNullOrEmpty() }
        assertThat(withWords).isNotEmpty()
        withWords.forEach { line ->
            assertThat(line.words!!.first().time).isEqualTo(line.time)
        }
    }

    @Test
    fun `convertQrc keeps standalone space tokens as word boundaries`() {
        val qrc = "[0,2250]晴(0,160)天(160,160) (320,160)周(480,160) (640,160)杰(800,160)"
        val converted = LxLyricDecoders.convertQrc(qrc)!!
        assertThat(converted).contains("<00:00.160>天 ")
        assertThat(converted).contains("<00:00.480>周 ")
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(converted)
        val line = parsed.synced!!.first()
        assertThat(line.words!!.map { it.word }).containsExactly("晴", "天", "周", "杰").inOrder()
        assertThat(line.line).isEqualTo("晴天 周 杰")
    }

    @Test
    fun `convertKrc keeps standalone space tokens as word boundaries`() {
        val krc = "[0,2000]<0,400,0>Look<400,80,0> <480,400,0>at<880,80,0> <960,400,0>the"
        val conversion = LxLyricDecoders.convertKrc(krc)!!
        assertThat(conversion.wordLrc).contains("<00:00.000>Look ")
        assertThat(conversion.wordLrc).contains("<00:00.480>at ")
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(conversion.wordLrc)
        val line = parsed.synced!!.first()
        assertThat(line.words!!.map { it.word }).containsExactly("Look", "at", "the").inOrder()
        assertThat(line.line).isEqualTo("Look at the")
    }

    @Test
    fun `convertYrc keeps spaces between legacy tuple words`() {
        val legacy = "[71060,1700](71060,300,0)Look(71360,80,0) (71440,300,0)at(71740,80,0) (71820,300,0)the"
        val converted = LxLyricDecoders.convertYrc(legacy)!!
        assertThat(converted).contains("<01:11.060>Look ")
        assertThat(converted).contains("<01:11.440>at ")
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(converted)
        val line = parsed.synced!!.first()
        assertThat(line.words!!.map { it.word }).containsExactly("Look", "at", "the").inOrder()
        assertThat(line.line).isEqualTo("Look at the")
    }

    @Test
    fun `parseLyrics restores the wordByWord cache format losslessly`() {
        // mirrors LyricsRepositoryImpl.toWordByWordLrc: `[mm:ss.xx]` line tag,
        // `<mm:ss.xx>` word tags, a single space separating words that start new words
        fun stamp(timeMs: Int): String = "%02d:%02d.%02d".format(timeMs / 60000, timeMs / 1000 % 60, timeMs % 1000 / 10)
        val lines = listOf(
            10700 to listOf("hello " to 10700, "world" to 11230),
            23450 to listOf("你好" to 23450),
        )
        val serialized = lines.joinToString("\n") { (lineTime, words) ->
            val body = words.joinToString("") { (text, wordTime) -> "<${stamp(wordTime)}>$text" }
            "[${stamp(lineTime)}]$body"
        }
        val parsed = com.minoppol.music.utils.LyricsUtils.parseLyrics(serialized)
        assertThat(parsed.synced.isNullOrEmpty()).isFalse()
        val synced = parsed.synced!!
        assertThat(synced).hasSize(lines.size)
        lines.zip(synced).forEach { (lineWords, line) ->
            val (lineTime, words) = lineWords
            assertThat(line.time).isEqualTo(lineTime)
            assertThat(line.words!!).hasSize(words.size)
            words.map { it.first.trim() }.zip(line.words!!).forEach { (expected, restored) ->
                assertThat(restored.word).isEqualTo(expected)
            }
            words.map { it.second }.zip(line.words!!).forEach { (expectedTime, restored) ->
                assertThat(restored.time).isEqualTo(expectedTime)
            }
        }
    }

    @Test
    fun `convertKuwoLrcx keeps minute-two word times on the song timeline`() {
        val lrcx = listOf(
            "[kuwo:052]",
            "[02:09.117]<0,0>我<0,0>想<0,0>要",
            "[02:09.117]<370,-370>I <1182,298>want <2796,452>something <4648,3288>just <6494,4162>like <8758,6562>this",
        ).joinToString("\n")
        val converted = LxLyricDecoders.convertKuwoLrcx(lrcx)!!
        assertThat(converted).contains("[02:09.117]<02:09.117>我<02:09.117>想<02:09.117>要")
        assertThat(converted).contains(
            "[02:09.117]<02:09.117>I <02:09.302>want <02:09.523>something " +
                "<02:10.109>just <02:10.449>like <02:11.032>this"
        )
        assertThat(converted).doesNotContain("120:")
    }

    @Test
    fun `splitKuwoTranslationLines pairs minute-two chinese with its preceding main`() {
        val lrc = listOf(
            "[02:09.117]<02:09.117>I <02:09.302>want <02:09.523>something",
            "[02:18.168]<02:18.168>我<02:18.168>想<02:18.168>要",
            "[02:18.168]<02:18.168>Oh <02:18.340>I <02:18.554>want",
            "[02:21.189]<02:21.189>的<02:21.189>不<02:21.189>过",
            "[02:21.189]<02:21.189>Doo <02:21.558>doo",
        ).joinToString("\n")
        val split = LxLyricDecoders.splitKuwoTranslationLines(lrc)
        assertThat(split.wordLrc).contains("[02:09.117]<02:09.117>I ")
        assertThat(split.wordLrc).contains("[02:18.168]<02:18.168>Oh ")
        assertThat(LxLyricDecoders.stripWordTags(split.wordLrc)).doesNotContain("我")
        assertThat(split.translationLrc).contains("[02:09.117]我想要")
        assertThat(split.translationLrc).contains("[02:18.168]的不过")
        assertThat(split.translationLrc).doesNotContain("120:")
    }

    @Test
    fun `fixTimeTag aligns minute-two target to source`() {
        val source = "[02:09.117]<02:09.117>I <02:09.302>want"
        val target = "[02:09.2]我想要的不过是这些"
        val fixed = LxLyricDecoders.fixTimeTag(source, target)
        assertThat(fixed).isEqualTo("[02:09.117]我想要的不过是这些")
    }
}
