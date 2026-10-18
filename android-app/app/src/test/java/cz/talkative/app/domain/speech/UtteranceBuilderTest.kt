package cz.talkative.app.domain.speech

import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import cz.talkative.app.testing.FakeSpeechProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class UtteranceBuilderTest {

    private fun sentence(vararg titles: String) = titles.mapIndexed { index, title ->
        SentenceEntry(index, Word("w$index", "default", title, null, index, 0))
    }

    @Test
    fun titlesAreJoinedIntoOneText() {
        assertEquals("chci jablko", UtteranceBuilder.build(sentence("chci", "jablko")))
    }

    @Test
    fun repeatedWordsAreKept() {
        assertEquals("ano ano ne", UtteranceBuilder.build(sentence("ano", "ano", "ne")))
    }

    @Test
    fun emptySentenceGivesEmptyText() {
        assertEquals("", UtteranceBuilder.build(emptyList()))
    }

    @Test
    fun blankTitlesAreSkipped_andOthersTrimmed() {
        assertEquals("a b", UtteranceBuilder.build(sentence(" a ", "  ", "b")))
    }

    @Test
    fun multiWordTitleStaysIntact() {
        assertEquals("bolí mě hlava prosím", UtteranceBuilder.build(sentence("bolí mě hlava", "prosím")))
    }

    @Test
    fun wholeSentenceGoesToProviderAsOneUtterance_notWordByWord() {
        val provider: SpeechProvider = FakeSpeechProvider()

        provider.speak(UtteranceBuilder.build(sentence("chci", "jablko", "prosím")))

        assertEquals(listOf("chci jablko prosím"), (provider as FakeSpeechProvider).spoken)
    }
}
