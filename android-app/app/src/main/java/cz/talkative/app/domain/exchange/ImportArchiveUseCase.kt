package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.AudioStore
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.image.ImageStore
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.repository.DictionaryRepository
import cz.talkative.app.domain.repository.ImportedWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Co a kam se importuje. S [replaceAll] se celý dosavadní slovník nahradí a [targetFolderId] se ignoruje (nové položky
 * jdou do kořene). [importConfig] načte i nastavení zařízení, je-li v archivu.
 */
data class ImportRequest(
    val selection: ImportSelection = ImportSelection.All,
    val targetFolderId: String? = null,
    val replaceAll: Boolean = false,
    val importConfig: Boolean = false,
)

sealed interface ImportResult {
    data class Success(val folders: Int, val words: Int, val configImported: Boolean) : ImportResult

    data class Failed(val error: ImportError) : ImportResult
}

private class ImportAbort(val error: ImportError) : Exception()

/**
 * Importuje archiv. Vždy jen přidává položky s novými ID (nic nepřepisuje), s `replaceAll` nahradí celý slovník.
 * Nejdřív se archiv celý zkontroluje (nic se nezapisuje), pak se zkopírují média do úložišť a nakonec se v jedné
 * transakci zapíšou položky. Při jakékoli chybě se zkopírovaná média smažou, takže se nezmění nic.
 */
class ImportArchiveUseCase @Inject constructor(
    private val reader: ArchiveReader,
    private val repository: DictionaryRepository,
    private val imageStore: ImageStore,
    private val audioStore: AudioStore,
    private val configRepository: ConfigRepository,
) {
    private class CopiedFile(val isImage: Boolean, val name: String)

    suspend operator fun invoke(source: ArchiveSource, request: ImportRequest): ImportResult {
        val manifest = when (val read = reader.read(source)) {
            is ReadResult.Success -> read.manifest
            is ReadResult.Failure -> return ImportResult.Failed(read.error)
        }
        if (!request.replaceAll && request.targetFolderId != null && repository.getPath(request.targetFolderId).isEmpty()) {
            return ImportResult.Failed(ImportError.TARGET_MISSING)
        }
        val plan = planImport(manifest, request.selection)

        val copied = mutableListOf<CopiedFile>()
        var finished = false
        try {
            val names = withContext(Dispatchers.IO) { copyMedia(source, plan, copied) }
            val words = plan.words.map { planned ->
                ImportedWord(
                    key = planned.word.id,
                    parentKey = planned.parentKey,
                    title = planned.word.title,
                    sortOrder = planned.sortOrder,
                    imageFile = names[planned.word.id to true],
                    alternateText = planned.word.alternateText,
                    audioFile = names[planned.word.id to false],
                )
            }
            repository.importItems(request.targetFolderId, plan.folders, words, request.replaceAll)
            finished = true
        } catch (e: ImportAbort) {
            return ImportResult.Failed(e.error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            return ImportResult.Failed(ImportError.FAILED)
        } finally {
            if (!finished) withContext(NonCancellable) { copied.forEach { deleteCopied(it) } }
        }

        var configImported = false
        if (request.importConfig && manifest.config != null) {
            val config = manifest.config.toAppConfig()
            configRepository.update { config }
            configImported = true
        }
        return ImportResult.Success(plan.folders.size, plan.words.size, configImported)
    }

    /**
     * Zkopíruje obrázky a MP3 vybraných slovíček z archivu do úložišť; každé slovíčko dostane vlastní soubor (soubory se
     * při smazání slovíčka mažou). Vrací nové názvy souborů podle (ID slovíčka, je obrázek). Co se zkopírovalo, je v [copied].
     */
    private suspend fun copyMedia(
        source: ArchiveSource,
        plan: ImportPlan,
        copied: MutableList<CopiedFile>,
    ): Map<Pair<String, Boolean>, String> {
        // položka v archivu -> slovíčka, která ji potřebují (a jestli jde o obrázek)
        val needed = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
        for (planned in plan.words) {
            planned.word.imageFile?.let { needed.getOrPut(ArchiveLayout.IMAGES_DIR + it) { mutableListOf() } += planned.word.id to true }
            planned.word.audioFile?.let { needed.getOrPut(ArchiveLayout.AUDIO_DIR + it) { mutableListOf() } += planned.word.id to false }
        }
        val names = mutableMapOf<Pair<String, Boolean>, String>()
        if (needed.isEmpty()) return names

        val found = mutableSetOf<String>()
        try {
            ZipInputStream(source.open()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val users = needed[entry.name] ?: continue
                    if (!found.add(entry.name)) continue
                    val bytes = readLimited(zip, ArchiveLimits.maxBytes(entry.name) ?: 0L) ?: throw ImportAbort(ImportError.INVALID_ARCHIVE)
                    for (user in users) {
                        names[user] = save(user.second, bytes, copied)
                    }
                }
            }
        } catch (e: ZipException) {
            throw ImportAbort(ImportError.INVALID_ARCHIVE)
        } catch (e: IOException) {
            throw ImportAbort(if (isStorageFull(e)) ImportError.STORAGE_FULL else ImportError.UNREADABLE)
        }
        if (found.size != needed.size) throw ImportAbort(ImportError.INVALID_ARCHIVE) // archiv se mezi čteními změnil
        return names
    }

    private suspend fun save(isImage: Boolean, bytes: ByteArray, copied: MutableList<CopiedFile>): String {
        val input = ByteArrayInputStream(bytes)
        return if (isImage) {
            when (val result = imageStore.saveFromStream(input)) {
                is ImageSaveResult.Saved -> result.fileName.also { copied += CopiedFile(true, it) }
                is ImageSaveResult.Failed -> throw ImportAbort(
                    if (result.error == ImageError.STORAGE_FULL) ImportError.STORAGE_FULL else ImportError.INVALID_ARCHIVE,
                )
            }
        } else {
            when (val result = audioStore.saveFromStream(input)) {
                is AudioSaveResult.Saved -> result.fileName.also { copied += CopiedFile(false, it) }
                is AudioSaveResult.Failed -> throw ImportAbort(
                    if (result.error == AudioError.STORAGE_FULL) ImportError.STORAGE_FULL else ImportError.INVALID_ARCHIVE,
                )
            }
        }
    }

    private suspend fun deleteCopied(file: CopiedFile) {
        if (file.isImage) imageStore.delete(file.name) else audioStore.delete(file.name)
    }
}
