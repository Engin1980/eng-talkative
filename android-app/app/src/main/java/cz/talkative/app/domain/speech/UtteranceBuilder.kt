package cz.talkative.app.domain.speech

import cz.talkative.app.domain.model.SentenceEntry

/** Skládá z věty text pro řeč. Čistá logika bez závislosti na Androidu. */
object UtteranceBuilder {

    /** Titulky slovíček oddělené mezerou, aby je TTS přečetl jako jednu souvislou větu; prázdné titulky se vynechají. */
    fun build(sentence: List<SentenceEntry>): String =
        sentence.map { it.word.title.trim() }.filter { it.isNotEmpty() }.joinToString(separator = " ")
}
