package cz.talkative.app.domain.speech

import cz.talkative.app.domain.model.SentenceEntry

/** Jeden souvislý úsek přehrávání věty. */
sealed interface PlaybackSegment {
    /** Text, který přečte TTS jedním plynulým výstupem. */
    data class Tts(val text: String) : PlaybackSegment

    /** Vlastní MP3 slovíčka ([fileName] je název souboru v soukromém úložišti). */
    data class Audio(val fileName: String) : PlaybackSegment
}

/** Rozdělí větu na úseky přehrávání. Čistá logika bez závislosti na Androidu. */
object PlaybackPlanner {

    /**
     * Sousední slova čtená TTS se spojí do jednoho úseku (mezera mezi nimi), slovo s vlastním MP3 tvoří samostatný úsek.
     * TTS slovo čte jiný text slovíčka, je-li vyplněný, jinak titulek; prázdná slova se vynechají.
     */
    fun plan(sentence: List<SentenceEntry>): List<PlaybackSegment> {
        val segments = mutableListOf<PlaybackSegment>()
        val pending = mutableListOf<String>()

        fun flushText() {
            if (pending.isNotEmpty()) segments += PlaybackSegment.Tts(pending.joinToString(separator = " "))
            pending.clear()
        }

        for (entry in sentence) {
            val word = entry.word
            if (word.audioFile != null) {
                flushText()
                segments += PlaybackSegment.Audio(word.audioFile)
            } else {
                val text = word.alternateText?.trim().orEmpty().ifEmpty { word.title.trim() }
                if (text.isNotEmpty()) pending += text
            }
        }
        flushText()
        return segments
    }
}
