package cz.talkative.app.testing

import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.image.ImageStore
import java.io.File

/** Úložiště obrázků v paměti pro testy; "soubory" jsou jen názvy. */
class FakeImageStore : ImageStore {
    val files = mutableSetOf<String>()
    private var counter = 0

    /** Zaregistruje existující soubor (jako by ho předtím někdo uložil). */
    fun add(name: String): String {
        files += name
        return name
    }

    override suspend fun saveFromUri(uri: String): ImageSaveResult {
        val name = "fake-${counter++}.jpg"
        files += name
        return ImageSaveResult.Saved(name)
    }

    override fun resolve(fileName: String): File? = if (fileName in files) File(fileName) else null

    override suspend fun delete(fileName: String) {
        files -= fileName
    }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        files.retainAll(keep)
    }
}
