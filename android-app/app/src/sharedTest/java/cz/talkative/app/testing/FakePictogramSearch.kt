package cz.talkative.app.testing

import cz.talkative.app.domain.pictogram.PictogramSearch
import cz.talkative.app.domain.pictogram.PictogramSearchResult
import kotlinx.coroutines.CompletableDeferred

/** Hledání piktogramů pro testy: vrací nastavený výsledek a zapamatuje si dotazy. */
class FakePictogramSearch(var result: PictogramSearchResult = PictogramSearchResult.Found(emptyList())) : PictogramSearch {
    val queries = mutableListOf<String>()

    /** Když je nastavená, hledání čeká, až se dokončí (řízení časování v testech). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun search(text: String): PictogramSearchResult {
        queries += text
        gate?.await()
        return result
    }
}
