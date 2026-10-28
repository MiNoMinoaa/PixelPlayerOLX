package com.minoppol.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {

    @Test
    fun parseLyrics_assignsVoicesFromBracketLabels() {
        // Any bracket pair at line start counts as a voice label, content flexible
        val lrc = "[00:01.00](男)第一句\n[00:02.00](女)第二句\n[00:03.00][合唱]第三句\n[00:04.00]{和声}第四句"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertEquals(1, synced[0].voice)               // male label → v1
        assertEquals(2, synced[1].voice)               // female label → v2
        assertEquals(1_000, synced[2].voice)           // chorus label → group (centered)
        assertEquals(3, synced[3].voice)               // harmony label → assigned voice…
        assertNotNull(synced[3].background)            // …plus harmony marker
        assertEquals("男：第一句", synced[0].line)   // label kept as name prefix, brackets stripped
        assertEquals("和声：第四句", synced[3].line)
    }

    @Test
    fun parseLyrics_assignsStableVoicesToUnknownBracketLabels() {
        val lrc = "[00:01.00](张三)一句\n[00:02.00](李四)二句\n[00:03.00](张三)三句"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertEquals(3, synced[0].voice)
        assertEquals(4, synced[1].voice)
        assertEquals(synced[0].voice, synced[2].voice)  // same label → same voice
    }

    @Test
    fun parseLyrics_assignsVoicesFromFullwidthAndCjkBracketLabels() {
        val lrc = "[00:01.00]（男）第一句\n[00:02.00]（女）第二句\n[00:03.00]【合】第三句\n[00:04.00]【甲】第四句\n[00:05.00]【乙】第五句\n[00:06.00]【甲】第六句"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertEquals(1, synced[0].voice)
        assertEquals(2, synced[1].voice)
        assertEquals(1_000, synced[2].voice)
        assertEquals(3, synced[3].voice)
        assertEquals(4, synced[4].voice)
        assertEquals(synced[3].voice, synced[5].voice)
        assertEquals("甲：第四句", synced[3].line)
    }

    @Test
    fun parseLyrics_doesNotTreatCjkTitleBracketsAsVoiceLabel() {
        val lrc = "[00:01.00]《歌名》"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertNull(synced[0].voice)
        assertEquals("《歌名》", synced[0].line)
    }

    @Test
    fun parseLyrics_wordTimedLineKeepsVoiceFromLeadingBracketLabel() {
        val lrc = "[00:01.00](男)<00:01.00>第<00:01.50>一句"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertEquals(1, synced[0].voice)
        assertEquals(2, synced[0].words?.size)
        assertEquals("男：第一句", synced[0].line)
    }

    @Test
    fun parseLyrics_carriesVoiceForwardThroughUnlabeledLines() {
        val lrc = "[00:01.00]【甲】第一句\n[00:02.00]第二句\n[00:03.00]第三句\n[00:04.00]【乙】第四句\n[00:05.00]第五句"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertEquals(3, synced[0].voice)
        assertEquals(3, synced[1].voice)
        assertEquals(3, synced[2].voice)
        assertEquals(4, synced[3].voice)
        assertEquals(4, synced[4].voice)
        assertEquals("第二句", synced[1].line)
    }

    @Test
    fun parseLyrics_stripsKnownMidLineVoiceLabelsFromText() {
        val lrc = "[00:01.00]【甲】第一部分中间【合】结束\n[00:02.00]【合】内容\n[00:03.00]普通（live版）"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertEquals("甲：第一部分中间结束", synced[0].line)
        assertEquals("合：内容", synced[1].line)
        assertEquals("普通（live版）", synced[2].line)
    }

    @Test
    fun parseLyrics_doesNotTreatMidLineParenthesesAsVoiceLabel() {
        val lrc = "[00:01.00]普通歌词 (live版)"
        val synced = requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

        assertNull(synced[0].voice)
        assertEquals("普通歌词 (live版)", synced[0].line)
    }

    @Test
    fun parseLyrics_kugouPathStripsParenWordTagsAndKeepsVoiceLabel() {
        // Real KRC / legacy tuple line: [startMs,lineDurMs](absWordMs,wordDurMs,0)word —
        // timestamps must be absolute or the line reads as [mm:ss]; (71060,170,0)
        // leaking into the text would mean paren word tags were not stripped.
        val krc = "[71060,170](男)(71060,170,0)啊\n[71230,180](71230,180,0)哦\n"
        val synced = requireNotNull(LyricsUtils.parseLyrics(krc).synced)

        assertEquals(71_060, synced[0].time)
        assertEquals("男：啊", synced[0].line)
        assertEquals(1, synced[0].voice)           // voice label → v1, label kept as text prefix
        assertEquals(71_230, synced[1].time)
        assertEquals("哦", synced[1].line)
        assertNull(synced[1].voice)
    }

    @Test
    fun parseLyrics_kugouKeepsUntaggedLeadingWordAndStripsResidueTags() {
        val krc = "[1000,2000]第一<0,300,0>句<R:x>$<300,300,0>啊\n"
        val synced = requireNotNull(LyricsUtils.parseLyrics(krc).synced)

        assertEquals("第一句啊", synced[0].line)
        assertEquals(3, synced[0].words?.size)
        assertEquals("第一", synced[0].words!![0].word)
        assertEquals(1_000, synced[0].words!![0].time)
        assertEquals("句", synced[0].words!![1].word)
    }

    @Test
    fun parseLyrics_handlesBomAtStartOfSyncedLine() {
        val lrc = "\uFEFF[00:03.80]Test line one\n[00:09.86]Test line two"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = lyrics.synced

        assertNotNull(synced)
        val syncedLines = requireNotNull(synced)
        assertEquals(2, syncedLines.size)
        assertEquals(3_800, syncedLines[0].time)
        assertEquals("Test line one", syncedLines[0].line)
        assertEquals(9_860, syncedLines[1].time)
        assertEquals("Test line two", syncedLines[1].line)
    }

    @Test
    fun parseLyrics_handlesWhitespacesBeforeTimestamp() {
        val lrc = "\uFEFF   [00:03.80]Test line one\r\n\t[00:09.86]Test line two"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals(3_800, synced[0].time)
        assertEquals("Test line one", synced[0].line)
        assertEquals(9_860, synced[1].time)
        assertEquals("Test line two", synced[1].line)
    }

    @Test
    fun parseLyrics_parsesFullSampleWithBom() {
        val lrc = "\uFEFF[00:03.80]Test line 1\n" +
            "[00:09.86]Test line 2\n" +
            "[00:16.53]Test line 3\n" +
            "[00:22.76]Test line 4\n" +
            "[00:28.32]Test line 5\n" +
            "[00:31.65]Test line 6\n" +
            "[00:34.75]Test line 7\n" +
            "[00:38.08]Test line 8\n" +
            "[00:41.67]Test line 9\n" +
            "[00:46.76]Test line 10\n" +
            "[00:49.97]Test line 11\n" +
            "[00:54.36]Test line 12\n" +
            "[00:59.52]Test line 13\n" +
            "[01:02.71]Test line 14\n" +
            "[01:09.05]Test line 15\n" +
            "[01:13.94]Test line 16\n" +
            "[01:19.88]Test line 17\n" +
            "[01:26.05]Test line 18\n" +
            "[01:29.09]Test line 19\n" +
            "[01:32.32]Test line 20\n" +
            "[01:35.48]Test line 21\n" +
            "[01:39.23]Test line 22\n" +
            "[01:44.48]Test line 23\n" +
            "[01:47.55]Test line 24\n" +
            "[01:52.13]Test line 25\n" +
            "[01:57.17]Test line 26\n" +
            "[02:00.27]Test line 27\n" +
            "[02:06.25]Test line 28\n" +
            "[02:10.76]Test line 29\n" +
            "[02:13.86]Test line 30\n" +
            "[02:17.25]Test line 31\n" +
            "[02:20.26]Test line 32\n" +
            "[02:24.13]Test line 33\n" +
            "[02:29.09]Test line 34\n" +
            "[02:32.13]Test line 35\n" +
            "[02:36.95]Test line 36\n" +
            "[02:41.75]Test line 37\n" +
            "[02:45.08]Test line 38\n" +
            "[02:54.13]Test line 39\n" +
            "[02:58.92]"

        val lyrics = LyricsUtils.parseLyrics(lrc)

        val synced = requireNotNull(lyrics.synced)
        assertEquals(40, synced.size)
        assertEquals(3_800, synced.first().time)
        assertEquals("Test line 1", synced.first().line)
        assertEquals(178_920, synced.last().time)
        assertEquals("", synced.last().line)
    }

    @Test
    fun parseLyrics_ignoresFormatCharactersInsideTimestamp() {
        val lrc = "\u202a[00:03.80\u202c]Test line one\n[00:09.86]Test line two"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals(3_800, synced[0].time)
        assertEquals("Test line one", synced[0].line)
        assertEquals(9_860, synced[1].time)
        assertEquals("Test line two", synced[1].line)
    }

    @Test
    fun parseLyrics_parsesSampleWrappedInQuotes() {
        val lrc = buildString {
            append('"')
            appendLine("[00:03.80]Test line 1")
            appendLine("[00:09.86]Test line 2")
            appendLine("[00:16.53]Test line 3")
            appendLine("[00:22.76]Test line 4")
            appendLine("[00:28.32]Test line 5")
            appendLine("[00:31.65]Test line 6")
            appendLine("[00:34.75]Test line 7")
            appendLine("[00:38.08]Test line 8")
            appendLine("[00:41.67]Test line 9")
            appendLine("[00:46.76]Test line 10")
            appendLine("[00:49.97]Test line 11")
            appendLine("[00:54.36]Test line 12")
            appendLine("[00:59.52]Test line 13")
            appendLine("[01:02.71]Test line 14")
            appendLine("[01:09.05]Test line 15")
            appendLine("[01:13.94]Test line 16")
            appendLine("[01:19.88]Test line 17")
            appendLine("[01:26.05]Test line 18")
            appendLine("[01:29.09]Test line 19")
            appendLine("[01:32.32]Test line 20")
            appendLine("[01:35.48]Test line 21")
            appendLine("[01:39.23]Test line 22")
            appendLine("[01:44.48]Test line 23")
            appendLine("[01:47.55]Test line 24")
            appendLine("[01:52.13]Test line 25")
            appendLine("[01:57.17]Test line 26")
            appendLine("[02:00.27]Test line 27")
            appendLine("[02:06.25]Test line 28")
            appendLine("[02:10.76]Test line 29")
            appendLine("[02:13.86]Test line 30")
            appendLine("[02:17.25]Test line 31")
            appendLine("[02:20.26]Test line 32")
            appendLine("[02:24.13]Test line 33")
            appendLine("[02:29.09]Test line 34")
            appendLine("[02:32.13]Test line 35")
            appendLine("[02:36.95]Test line 36")
            appendLine("[02:41.75]Test line 37")
            appendLine("[02:45.08]Test line 38")
            appendLine("[02:54.13]Test line 39")
            append("[02:58.92]")
            append('"')
        }

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(40, synced.size)
        assertEquals(3_800, synced.first().time)
        assertEquals("Test line 1", synced.first().line)
        assertEquals(178_920, synced.last().time)
        assertEquals("", synced.last().line)
    }

    @Test
    fun parseLyrics_convertsAppleTtmlWithXmlDeclarationAndNamespaces() {
        val ttml = """
            <?xml version='1.0' encoding='utf-8'?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="en">
              <head>
                <metadata>
                  <ttm:agent type="person" xml:id="v1">
                    <ttm:name type="full">Test Agent</ttm:name>
                  </ttm:agent>
                </metadata>
              </head>
              <body dur="0:15.000">
                <div begin="7.531" end="12.005" itunes:songPart="Verse">
                  <p begin="7.531" end="12.005" itunes:key="L1" ttm:agent="v1"><span begin="7.531" end="7.782">Test,</span> <span begin="9.208" end="9.443">one</span> <span begin="9.443" end="9.675">two</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = LyricsUtils.parseLyrics(ttml)
        val synced = requireNotNull(lyrics.synced)
        val first = synced.first()

        assertEquals(1, synced.size)
        assertEquals(7_530, first.time)
        assertEquals("Test, one two", first.line)
        assertEquals(listOf("Test,", "one", "two"), requireNotNull(first.words).map { it.word })
    }

    @Test
    fun parseLyrics_doesNotExposeBrokenTtmlAsPlainText() {
        val malformedTtml = """
            <?xml version='1.0' encoding='utf-8'?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:01.000">Hello
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = LyricsUtils.parseLyrics(malformedTtml)

        assertTrue(lyrics.synced.isNullOrEmpty())
        assertTrue(lyrics.plain.isNullOrEmpty())
    }

    @Test
    fun parseLyrics_stripsAdditionalLrcTimestampsFromLines() {
        val lrc = """
            [00:12.57] Test line one
            [00:26.42][01:12.34] Test line two
            [00:41.71][00:52.96][01:27.42] Test line three
        """.trimIndent()

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(
            listOf(
                "Test line one",
                "Test line two",
                "Test line three"
            ),
            synced.map { it.line }
        )
    }

    @Test
    fun parseLyrics_wordByWord_ignoresTrailingUntaggedTranslationInWordTiming() {
        val lrc = "[00:10.00]<00:10.00>Test <00:10.30>line <00:10.60>one <00:10.90>two\\n哈机密密集哈"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)
        val first = synced.first()
        val words = requireNotNull(first.words)

        assertEquals("Test line one two\\n哈机密密集哈", first.line)
        assertEquals(listOf("Test", "line", "one", "two"), words.map { it.word })
        assertEquals(listOf(true, true, true, true), words.map { it.startsNewWord })
        assertTrue(words.none { it.word.contains("哈机密密集哈") })
    }

    @Test
    fun parseLyrics_pairsSameTimestampLinesAsTranslation() {
        val lrc = "[00:10.00]Hello world\n[00:10.00]你好世界\n[00:20.00]Goodbye"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals("Hello world", synced[0].line)
        assertEquals("你好世界", synced[0].translation)
        assertEquals("Goodbye", synced[1].line)
        assertNull(synced[1].translation)
    }

    @Test
    fun parseLyrics_singleLineWithoutDuplicate_translationIsNull() {
        val lrc = "[00:10.00]Hello world\n[00:20.00]Goodbye\n[00:30.00]See you"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(3, synced.size)
        synced.forEach { assertNull(it.translation) }
    }

    @Test
    fun parseLyrics_threeLinesAtSameTimestamp_pairsFirstTwoOnly() {
        val lrc = "[00:10.00]Hello world\n[00:10.00]你好世界\n[00:10.00]Hola mundo"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals("Hello world", synced[0].line)
        assertEquals("你好世界", synced[0].translation)
        assertEquals("Hola mundo", synced[1].line)
        assertNull(synced[1].translation)
    }

    @Test
    fun parseLyrics_translationCreditAtSameTimestamp_isGroupedIntoTranslation() {
        val lrc = "[00:10.00]Hello world\n[00:10.00]你好世界\n[00:10.00]by: translator\n[00:20.00]Goodbye"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals("Hello world", synced[0].line)
        assertEquals("你好世界\nby: translator", synced[0].translation)
        assertEquals("Goodbye", synced[1].line)
        assertNull(synced[1].translation)
    }

    @Test
    fun parseLyrics_nonTimestampedLinePreservedAsMerge_notTranslation() {
        val lrc = "[00:10.00]Hello world\ncontinuation line\n[00:20.00]Next"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals("Hello world\ncontinuation line", synced[0].line)
        assertNull(synced[0].translation)
    }

    @Test
    fun parseLyrics_colonSubSecondSeparator_parsedAndPairedWithTranslation() {
        val lrc = "[00:00.000]作词: テスト\n" +
            "[00:01.000]作曲: テスト\n" +
            "[00:22:43]あいうえおかき\n" +
            "[00:25:39]くけこさしすせ\n" +
            "[00:22.43]哈机密密集哈\n" +
            "[00:25.39]密集哈机密集哈"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(4, synced.size)

        val line22 = synced.first { it.line == "あいうえおかき" }
        assertEquals(22_430, line22.time)
        assertEquals("哈机密密集哈", line22.translation)

        val line25 = synced.first { it.line == "くけこさしすせ" }
        assertEquals(25_390, line25.time)
        assertEquals("密集哈机密集哈", line25.translation)
    }

    @Test
    fun parseLyrics_wordByWord_colonSubSecondSeparator_supported() {
        val lrc = "[00:10:00]<00:10:00>Hello <00:10:50>world"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)
        val words = requireNotNull(synced.single().words)

        assertEquals("Hello world", synced.single().line)
        assertEquals(listOf("Hello", "world"), words.map { it.word })
        assertEquals(listOf(10_000, 10_500), words.map { it.time })
        assertEquals(listOf(true, true), words.map { it.startsNewWord })
    }

    @Test
    fun parseLyrics_wordByWord_marksSyllablesAsSameVisualWord() {
        val lrc = "[00:10.00]<00:10.00>to<00:10.20>geth<00:10.40>er <00:10.60>now"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)
        val words = requireNotNull(synced.single().words)

        assertEquals("together now", synced.single().line)
        assertEquals(listOf("to", "geth", "er", "now"), words.map { it.word })
        assertEquals(listOf(true, false, false, true), words.map { it.startsNewWord })
    }

    @Test
    fun parseLyrics_wordByWord_standaloneWhitespaceStillStartsNextWord() {
        val lrc = "[00:10.00]<00:10.00>To<00:10.20> <00:10.40>fall"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)
        val words = requireNotNull(synced.single().words)

        assertEquals("To fall", synced.single().line)
        assertEquals(listOf("To", "fall"), words.map { it.word })
        assertEquals(listOf(true, true), words.map { it.startsNewWord })
    }

    @Test
    fun syncedToLrcString_preservesPairedTranslations() {
        val lrc = LyricsUtils.syncedToLrcString(
            listOf(
                com.minoppol.music.data.model.SyncedLine(
                    time = 10_000,
                    line = "Hello world",
                    translation = "你好世界"
                ),
                com.minoppol.music.data.model.SyncedLine(
                    time = 20_000,
                    line = "Goodbye"
                )
            )
        )

        assertEquals(
            "[00:10.00]Hello world\n[00:10.00]你好世界\n[00:20.00]Goodbye",
            lrc
        )
    }

    @Test
    fun syncedToLrcString_expandsMultilineTranslationWithTimestampPerLine() {
        val lrc = LyricsUtils.syncedToLrcString(
            listOf(
                com.minoppol.music.data.model.SyncedLine(
                    time = 10_000,
                    line = "Hello world",
                    translation = "你好世界\nby: translator"
                )
            )
        )

        assertEquals(
            "[00:10.00]Hello world\n[00:10.00]你好世界\n[00:10.00]by: translator",
            lrc
        )
    }

    @Test
    fun parseLyrics_acceptsSingleDigitMinuteSecondAndFractionFields() {
        val parsed = LyricsUtils.parseLyrics("[1:2.3]<1:2.3>你<1:2.9>好")
        val synced = parsed.synced!!
        assertEquals(1, synced.size)
        assertEquals(62_300, synced[0].time)
        assertEquals(2, synced[0].words!!.size)
        assertEquals(62_300, synced[0].words!![0].time)
        assertEquals(62_900, synced[0].words!![1].time)
    }

    @Test
    fun parseLyrics_trimsStandaloneSpaceTokensFromWordTimedLineText() {
        val parsed = LyricsUtils.parseLyrics("[00:10.00]<00:10.00> <00:10.05>I<00:10.15> want")
        val synced = parsed.synced!!

        assertEquals("I want", synced[0].line)
        assertEquals(listOf("I", "want"), synced[0].words!!.map { it.word })
        assertTrue(synced[0].words!!.all { it.startsNewWord })
    }
    @Test
    fun parseLyrics_treatsStandaloneVoiceCueLinesAsSectionSwitches() {
        val lrc = listOf(
            "[00:10.63]男：",
            "[00:12.08]哈机密密集哈",
            "[00:16.48]密集哈机密集哈",
            "[00:30.87]女：",
            "[00:32.10]哈机密密集哈机密",
            "[01:36.51]合：",
            "[01:40.00]哈机密 密集哈机密",
        ).joinToString("\n")
        val synced = LyricsUtils.parseLyrics(lrc).synced!!

        assertEquals(4, synced.size)
        assertEquals(1, synced[0].voice)
        assertEquals("哈机密密集哈", synced[0].line)
        assertEquals(1, synced[1].voice)
        assertEquals(2, synced[2].voice)
        assertEquals("哈机密密集哈机密", synced[2].line)
        assertEquals(1_000, synced[3].voice)
        assertEquals("哈机密 密集哈机密", synced[3].line)
    }

    @Test
    fun parseLyrics_dropsWordTimedStandaloneVoiceCueLines() {
        val lrc = "[00:10.63]<00:10.63>男<00:10.87>：\n[00:12.08]<00:12.08>哈<00:12.60>机密密集哈"
        val synced = LyricsUtils.parseLyrics(lrc).synced!!

        assertEquals(1, synced.size)
        assertEquals(1, synced[0].voice)
        assertEquals("哈机密密集哈", synced[0].line)
        assertEquals(2, synced[0].words!!.size)
    }

    @Test
    fun parseLyrics_mapsInlineBareKeywordPrefixToVoice() {
        val lrc = "[00:10.00]男：哈机密密集哈\n[00:20.00]女：密集哈机密集哈"
        val synced = LyricsUtils.parseLyrics(lrc).synced!!

        assertEquals(2, synced.size)
        assertEquals(1, synced[0].voice)
        assertEquals("男：哈机密密集哈", synced[0].line)
        assertEquals(2, synced[1].voice)
        assertEquals("女：密集哈机密集哈", synced[1].line)
    }

    @Test
    fun parseLyrics_assignsStableVoicesToNovelStandaloneSingerCues() {
        val lrc = listOf(
            "[00:01.00]张三：",
            "[00:02.00]哈机密密集哈",
            "[00:03.00]李四：",
            "[00:04.00]密集哈机密集哈",
        ).joinToString("\n")
        val synced = LyricsUtils.parseLyrics(lrc).synced!!

        assertEquals(2, synced.size)
        assertEquals("哈机密密集哈", synced[0].line)
        assertEquals("密集哈机密集哈", synced[1].line)
        assertNotNull(synced[0].voice)
        assertNotNull(synced[1].voice)
        assertTrue(synced[0].voice != synced[1].voice)
        assertEquals(synced[0].voice, synced[0].voice)
    }

    @Test
    fun parseLyrics_keepsPlainSingleWordLinesWithoutColon() {
        val synced = LyricsUtils.parseLyrics("[00:01.00]嗯\n[00:02.00]哦").synced!!

        assertEquals(2, synced.size)
        assertEquals("嗯", synced[0].line)
        assertEquals("哦", synced[1].line)
        assertNull(synced[0].voice)
        assertNull(synced[1].voice)
    }

    @Test
    fun parseLyrics_kugouPathDropsStandaloneVoiceCueLines() {
        val synced = LyricsUtils.parseLyrics("[1000,5000]男：\n[7000,3000]哈机密密集哈").synced!!

        assertEquals(1, synced.size)
        assertEquals(1, synced[0].voice)
        assertEquals("哈机密密集哈", synced[0].line)
    }
}
