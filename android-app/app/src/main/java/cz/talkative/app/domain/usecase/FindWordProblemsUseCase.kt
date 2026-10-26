package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.audio.AudioStore
import cz.talkative.app.domain.image.ImageStore
import cz.talkative.app.domain.integrity.DictionaryIntegrity
import cz.talkative.app.domain.integrity.ItemProblem
import cz.talkative.app.domain.model.Word
import javax.inject.Inject

/** Zjistí, kterým z [words] chybí soubor obrázku nebo zvuku. Vrací jen slovíčka, která mají nějaký problém. */
class FindWordProblemsUseCase @Inject constructor(
    private val imageStore: ImageStore,
    private val audioStore: AudioStore,
) {
    operator fun invoke(words: List<Word>): Map<String, Set<ItemProblem>> =
        words.associate { it.id to DictionaryIntegrity.fileProblems(it, { n -> imageStore.resolve(n) != null }, { n -> audioStore.resolve(n) != null }) }
            .filterValues { it.isNotEmpty() }
}
