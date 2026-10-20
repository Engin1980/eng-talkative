package cz.talkative.app.testing

import cz.talkative.app.domain.pictogram.PictogramDownloadResult
import cz.talkative.app.domain.pictogram.PictogramDownloader
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** Stahování piktogramů pro testy: vrací nastavený výsledek (výchozí je soubor `pictogram-<id>.png`) a zapamatuje si ID. */
class FakePictogramDownloader : PictogramDownloader {
    val downloaded = mutableListOf<Int>()

    /** Když je nastavený, stahování skončí tímto výsledkem místo souboru. */
    var failure: PictogramDownloadResult.Failed? = null

    /** Když je nastavená, stahování čeká, až se dokončí (řízení časování v testech). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun download(id: Int): PictogramDownloadResult {
        downloaded += id
        gate?.await()
        return failure ?: PictogramDownloadResult.Downloaded(File("/cache/pictograms/pictogram-$id.png"))
    }
}
