package cz.talkative.app.domain.speech

import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.speech.PlaybackSegment.Audio
import cz.talkative.app.domain.speech.PlaybackSegment.Tts
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPlannerTest {

    private fun text(title: String, alternate: String? = null) = Word("w-$title", "default", title, null, 0, 0, alternateText = alternate)

    private fun mp3(title: String, file: String) = Word("w-$title", "default", title, null, 0, 0, audioFile = file)

    private fun sentenceOf(vararg words: Word) = words.mapIndexed { index, word -> SentenceEntry(index, word) }

    private fun plan(vararg words: Word) = PlaybackPlanner.plan(sentenceOf(*words))

    @Test
    fun emptySentence_hasNoSegments() {
        assertEquals(emptyList<PlaybackSegment>(), plan())
    }

    @Test
    fun adjacentTtsWords_areMergedIntoOneSegment() {
        assertEquals(listOf<PlaybackSegment>(Tts("chci jablko prosím")), plan(text("chci"), text("jablko"), text("prosím")))
    }

    @Test
    fun mp3BetweenWords_splitsIntoTtsMp3Tts() {
        // „A, [MP3], B, C“ → TTS(A), MP3, TTS(B C)
        assertEquals(
            listOf(Tts("A"), Audio("m.mp3"), Tts("B C")),
            plan(text("A"), mp3("M", "m.mp3"), text("B"), text("C")),
        )
    }

    @Test
    fun sentenceOfOnlyMp3_hasOnlyAudioSegments() {
        assertEquals(listOf<PlaybackSegment>(Audio("a.mp3"), Audio("b.mp3")), plan(mp3("A", "a.mp3"), mp3("B", "b.mp3")))
    }

    @Test
    fun mp3AtTheStart_isFollowedByTts() {
        assertEquals(listOf(Audio("m.mp3"), Tts("A B")), plan(mp3("M", "m.mp3"), text("A"), text("B")))
    }

    @Test
    fun mp3AtTheEnd_followsTts() {
        assertEquals(listOf(Tts("A B"), Audio("m.mp3")), plan(text("A"), text("B"), mp3("M", "m.mp3")))
    }

    @Test
    fun alternateText_replacesTitle() {
        assertEquals(listOf<PlaybackSegment>(Tts("chci ká dé")), plan(text("chci"), text("KD", alternate = "ká dé")))
    }

    @Test
    fun blankAlternateText_fallsBackToTitle() {
        assertEquals(listOf<PlaybackSegment>(Tts("KD")), plan(text("KD", alternate = "   ")))
    }

    @Test
    fun mp3WinsOverAlternateText() {
        val both = Word("w", "default", "KD", null, 0, 0, alternateText = "ká dé", audioFile = "kd.mp3")
        assertEquals(listOf<PlaybackSegment>(Audio("kd.mp3")), PlaybackPlanner.plan(sentenceOf(both)))
    }

    @Test
    fun blankWords_areSkipped_andTextTrimmed() {
        assertEquals(listOf<PlaybackSegment>(Tts("a b")), plan(text(" a "), text("  "), text("b")))
    }

    @Test
    fun blankWordBetweenTwoMp3_addsNothing() {
        assertEquals(listOf<PlaybackSegment>(Audio("a.mp3"), Audio("b.mp3")), plan(mp3("A", "a.mp3"), text(" "), mp3("B", "b.mp3")))
    }

    @Test
    fun repeatedWords_areKept() {
        assertEquals(listOf<PlaybackSegment>(Tts("ano ano ne")), plan(text("ano"), text("ano"), text("ne")))
    }
}
