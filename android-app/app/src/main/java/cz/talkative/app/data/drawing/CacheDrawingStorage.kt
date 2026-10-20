package cz.talkative.app.data.drawing

import android.content.Context
import cz.talkative.app.domain.drawing.DrawingStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Kresby v `cache/drawings`; zapisuje se přes dočasný soubor, takže se nikdy nepředá napůl zapsaný. Úklid dělá start aplikace. */
class CacheDrawingStorage(private val cacheDir: File) : DrawingStorage {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.cacheDir)

    private val directory get() = File(cacheDir, DIRECTORY_NAME)

    override suspend fun save(png: ByteArray): File = withContext(Dispatchers.IO) {
        directory.deleteRecursively()
        directory.mkdirs()
        val target = File(directory, "drawing-${System.currentTimeMillis()}.png")
        val temp = File(directory, target.name + ".tmp")
        try {
            temp.writeBytes(png)
            if (!temp.renameTo(target)) throw IOException("Soubor nelze přejmenovat")
            target
        } finally {
            temp.delete() // po úspěšném přejmenování už neexistuje
        }
    }

    companion object {
        const val DIRECTORY_NAME = "drawings"
    }
}
