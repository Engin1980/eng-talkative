package cz.talkative.app.data.sync

import android.content.Context
import cz.talkative.app.domain.sync.MediaKind
import cz.talkative.app.domain.sync.MediaRef
import cz.talkative.app.domain.sync.SyncMediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Média v `filesDir/images` a `filesDir/audio` (stejné složky jako `FileImageStore` a `FileAudioStore`). Soubor stažený
 * z Drive se uloží pod přesným názvem, na který odkazuje databáze; nejdřív se zkopíruje do dočasného souboru ve stejné
 * složce a přejmenuje, takže přerušený zápis nenechá poškozený soubor.
 */
class FileSyncMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncMediaStore {

    override fun file(ref: MediaRef): File? = target(ref)?.takeIf { it.isFile }

    override suspend fun restore(ref: MediaRef, source: File): Boolean = withContext(Dispatchers.IO) {
        val target = target(ref) ?: return@withContext false
        val temp = File(target.parentFile, "${target.name}.sync.tmp")
        try {
            target.parentFile?.mkdirs()
            source.copyTo(temp, overwrite = true)
            temp.renameTo(target)
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }

    override suspend fun delete(ref: MediaRef) {
        withContext(Dispatchers.IO) { target(ref)?.delete() }
    }

    /** Název souboru bez cesty; cokoli s lomítkem nebo `..` se odmítne (název přichází z Drive). */
    private fun target(ref: MediaRef): File? {
        val name = ref.name
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name == "..") return null
        val directory = File(context.filesDir, if (ref.kind == MediaKind.IMAGE) "images" else "audio")
        return File(directory, name)
    }
}
