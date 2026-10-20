package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.repository.DictionaryRepository
import cz.talkative.app.domain.repository.ImportedFolder
import cz.talkative.app.domain.repository.ImportedWord
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeConfigRepository
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.ui.admin.FakeDictionaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportArchiveUseCaseTest {

    private class BytesSource(private val bytes: ByteArray) : ArchiveSource {
        override fun open(): InputStream = ByteArrayInputStream(bytes)
    }

    private val repository = FakeDictionaryRepository()
    private val images = FakeImageStore()
    private val audio = FakeAudioStore()
    private val configRepository = FakeConfigRepository()
    private val import = ImportArchiveUseCase(ArchiveReader(), repository, images, audio, configRepository)

    private fun zip(manifestText: String?, vararg entries: Pair<String, ByteArray>): ArchiveSource {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (manifestText != null) {
                zip.putNextEntry(ZipEntry(ArchiveLayout.MANIFEST))
                zip.write(manifestText.toByteArray())
                zip.closeEntry()
            }
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return BytesSource(out.toByteArray())
    }

    private fun archive(manifest: ArchiveManifest, vararg entries: Pair<String, ByteArray>) =
        zip(ManifestCodec.encode(manifest), *entries)

    private fun manifest(
        folders: List<ArchiveFolder> = emptyList(),
        words: List<ArchiveWord> = emptyList(),
        config: ArchiveConfig? = null,
    ) = ArchiveManifest(ARCHIVE_SCHEMA_VERSION, folders, words, config)

    private fun run(source: ArchiveSource, request: ImportRequest = ImportRequest()) = runBlocking { import(source, request) }

    private fun failure(source: ArchiveSource, request: ImportRequest = ImportRequest()) =
        (run(source, request) as ImportResult.Failed).error

    private val sample = manifest(
        folders = listOf(ArchiveFolder("food", "Jídlo", null, 1), ArchiveFolder("fruit", "Ovoce", "food", 1)),
        words = listOf(
            ArchiveWord("chci", "chci", null, 0),
            ArchiveWord("bread", "chleba", "food", 0, imageFile = "chleba.jpg"),
            ArchiveWord("apple", "jablko", "fruit", 0, imageFile = "jablko.png", audioFile = "jablko.mp3", alternateText = "jabko"),
        ),
    )
    private val sampleArchive get() = archive(
        sample,
        "images/chleba.jpg" to byteArrayOf(1),
        "images/jablko.png" to byteArrayOf(2),
        "audio/jablko.mp3" to byteArrayOf(3),
    )

    private fun items(parentId: String?): List<DictionaryItem> = runBlocking { repository.observeItems(parentId).first() }
    private fun folderNamed(title: String) = repository.allFolders.single { it.title == title }
    private fun wordNamed(title: String) = repository.allWords.single { it.title == title }

    @Test
    fun wholeArchive_isAddedToTheRoot_withStructureOrderAndMedia() {
        val result = run(sampleArchive)

        assertEquals(ImportResult.Success(folders = 2, words = 3, configImported = false), result)
        assertEquals(listOf("chci", "Jídlo"), items(null).map { it.title })
        val food = folderNamed("Jídlo")
        assertEquals(listOf("chleba", "Ovoce"), items(food.id).map { it.title })
        val apple = wordNamed("jablko")
        assertEquals(folderNamed("Ovoce").id, apple.parentId)
        assertEquals("jabko", apple.alternateText)
        assertTrue(apple.imageFile != null && apple.audioFile != null)
        assertTrue(wordNamed("chleba").imageFile != null)
        assertNull(wordNamed("chci").imageFile)
    }

    @Test
    fun import_usesNewIds_andNeverTouchesExistingItems() {
        val existing = runBlocking {
            val folder = repository.createFolder(null, "Moje")
            repository.createWord(folder.id, "staré")
            folder
        }

        run(sampleArchive)

        assertEquals("Moje", repository.allFolders.first().title)
        assertEquals(existing.id, repository.allFolders.first().id)
        assertEquals(listOf("Moje", "chci", "Jídlo"), items(null).map { it.title }) // přidává se na konec
        val ids = repository.allFolders.map { it.id } + repository.allWords.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it in setOf("food", "fruit", "chci", "bread", "apple") })
    }

    @Test
    fun importingTheSameArchiveTwice_createsDuplicates_withSeparateFiles() {
        run(sampleArchive)
        val firstImages = repository.allWords.mapNotNull { it.imageFile }.toSet()

        run(sampleArchive)

        assertEquals(2, repository.allFolders.count { it.title == "Jídlo" })
        assertEquals(2, repository.allWords.count { it.title == "jablko" })
        assertEquals(4, items(null).size)
        val allImages = repository.allWords.mapNotNull { it.imageFile }
        assertEquals(allImages.size, allImages.toSet().size) // žádné sdílené soubory
        assertTrue(firstImages.all { it in allImages })
    }

    @Test
    fun targetFolder_receivesTheItems() {
        val target = runBlocking { repository.createFolder(null, "Cíl") }

        run(sampleArchive, ImportRequest(targetFolderId = target.id))

        assertEquals(listOf("Cíl"), items(null).map { it.title })
        assertEquals(listOf("chci", "Jídlo"), items(target.id).map { it.title })
    }

    @Test
    fun missingTarget_failsWithoutChangingAnything() {
        val error = failure(sampleArchive, ImportRequest(targetFolderId = "neni"))

        assertEquals(ImportError.TARGET_MISSING, error)
        assertTrue(repository.allFolders.isEmpty() && repository.allWords.isEmpty())
        assertTrue(images.files.isEmpty() && audio.files.isEmpty())
    }

    @Test
    fun partialImport_bringsOnlySelectedFolderWithItsMedia_intoTheTarget() {
        val target = runBlocking { repository.createFolder(null, "Cíl") }

        val result = run(sampleArchive, ImportRequest(ImportSelection.Folders(setOf("fruit")), targetFolderId = target.id))

        assertEquals(ImportResult.Success(folders = 1, words = 1, configImported = false), result)
        assertEquals(listOf("Ovoce"), items(target.id).map { it.title })
        val apple = wordNamed("jablko")
        assertEquals(folderNamed("Ovoce").id, apple.parentId)
        assertTrue(apple.imageFile != null && apple.audioFile != null)
        assertEquals(setOf(apple.imageFile), images.files) // obrázek chleba se nekopíroval
        assertEquals(setOf(apple.audioFile), audio.files)
        assertTrue(repository.allWords.none { it.title == "chleba" || it.title == "chci" })
    }

    @Test
    fun replaceAll_removesTheOldDictionaryAndItsFiles_andIgnoresTheTarget() {
        val old = runBlocking {
            val folder = repository.createFolder(null, "Staré")
            repository.createWord(folder.id, "staré slovo")
            folder
        }

        val result = run(sampleArchive, ImportRequest(targetFolderId = old.id, replaceAll = true))

        assertEquals(ImportResult.Success(2, 3, false), result)
        assertEquals(listOf("chci", "Jídlo"), items(null).map { it.title })
        assertTrue(repository.allFolders.none { it.title == "Staré" })
        assertTrue(repository.allWords.none { it.title == "staré slovo" })
    }

    @Test
    fun replaceAll_withBrokenArchive_keepsTheOldDictionary() {
        runBlocking { repository.createWord(null, "stará") }

        val error = failure(zip("nejsou to data"), ImportRequest(replaceAll = true))

        assertEquals(ImportError.NOT_AN_ARCHIVE, error)
        assertEquals(listOf("stará"), repository.allWords.map { it.title })
    }

    @Test
    fun config_isImportedOnlyWhenRequestedAndPresent() {
        val withConfig = archive(manifest(config = ArchiveConfig(addWordMode = AddWordMode.DRAG, collationLanguageTag = "pl")))

        assertEquals(ImportResult.Success(0, 0, configImported = false), run(withConfig))
        assertEquals(AppConfig(), configRepository.current)

        assertEquals(ImportResult.Success(0, 0, configImported = true), run(withConfig, ImportRequest(importConfig = true)))
        assertEquals(AddWordMode.DRAG, configRepository.current.addWordMode)
        assertEquals("pl", configRepository.current.collationLanguageTag)

        val noConfig = archive(manifest())
        assertEquals(ImportResult.Success(0, 0, configImported = false), run(noConfig, ImportRequest(importConfig = true)))
    }

    @Test
    fun configOutsideLimits_isClamped() {
        val source = archive(manifest(config = ArchiveConfig(removeHoldMillis = 900_000L)))

        run(source, ImportRequest(importConfig = true))

        assertTrue(configRepository.current.removeHoldMillis < 900_000L)
    }

    @Test
    fun configIsNotImported_whenTheDictionaryImportFails() {
        val broken = archive(
            manifest(words = listOf(ArchiveWord("w", "x", imageFile = "chybi.jpg")), config = ArchiveConfig(addWordMode = AddWordMode.DRAG)),
        )

        failure(broken, ImportRequest(importConfig = true))

        assertEquals(AppConfig(), configRepository.current)
    }

    @Test
    fun foreignFiles_areRejected() {
        assertEquals(ImportError.NOT_AN_ARCHIVE, failure(BytesSource("PDF-1.4 tohle není zip".toByteArray())))
        assertEquals(ImportError.NOT_AN_ARCHIVE, failure(BytesSource(ByteArray(0))))
        assertEquals(ImportError.NOT_AN_ARCHIVE, failure(zip(null, "neco.txt" to byteArrayOf(1)))) // zip bez manifestu
        assertEquals(ImportError.NOT_AN_ARCHIVE, failure(zip("""{"neco":1}""")))
        assertEquals(ImportError.NOT_AN_ARCHIVE, failure(zip("tohle není json")))
    }

    @Test
    fun truncatedZip_isRejectedWithoutChanges() {
        val full = (sampleArchive as BytesSource).let { source -> source.open().readBytes() }
        val truncated = BytesSource(full.copyOf(full.size / 2))

        val result = run(truncated)

        assertTrue(result is ImportResult.Failed)
        assertTrue(repository.allFolders.isEmpty() && repository.allWords.isEmpty())
        assertTrue(images.files.isEmpty() && audio.files.isEmpty())
    }

    @Test
    fun newerSchemaVersion_isRejectedWithItsOwnError() {
        assertEquals(ImportError.NEWER_VERSION, failure(zip("""{"schemaVersion":${ARCHIVE_SCHEMA_VERSION + 1},"folders":[]}""")))
        assertTrue(repository.allFolders.isEmpty())
    }

    @Test
    fun inconsistentManifest_isRejected() {
        val cycle = manifest(folders = listOf(ArchiveFolder("a", "A", "b"), ArchiveFolder("b", "B", "a")))
        assertEquals(ImportError.INVALID_ARCHIVE, failure(archive(cycle)))
        assertEquals(
            ImportError.INVALID_ARCHIVE,
            failure(archive(manifest(words = listOf(ArchiveWord("w", "x", imageFile = "../../evil.jpg"))), "images/../../evil.jpg" to byteArrayOf(1))),
        )
    }

    @Test
    fun referencedMediaMissingFromTheArchive_isRejected_withoutAnyChange() {
        val source = archive(sample, "images/chleba.jpg" to byteArrayOf(1)) // chybí jablko.png a jablko.mp3

        assertEquals(ImportError.INVALID_ARCHIVE, failure(source))
        assertTrue(repository.allFolders.isEmpty() && repository.allWords.isEmpty())
        assertTrue(images.files.isEmpty() && audio.files.isEmpty())
    }

    @Test
    fun oversizedMedia_isRejected() {
        val tooBig = ByteArray((ArchiveLimits.MAX_AUDIO_BYTES + 1).toInt())
        val source = archive(manifest(words = listOf(ArchiveWord("w", "x", audioFile = "a.mp3"))), "audio/a.mp3" to tooBig)

        assertEquals(ImportError.INVALID_ARCHIVE, failure(source))
        assertTrue(repository.allWords.isEmpty())
    }

    @Test
    fun unreadableMedia_stopsTheImport_andRemovesAlreadyCopiedFiles() {
        audio.failWith = AudioError.UNSUPPORTED_FORMAT // MP3 přijde až po obrázcích

        assertEquals(ImportError.INVALID_ARCHIVE, failure(sampleArchive))

        assertTrue(repository.allFolders.isEmpty() && repository.allWords.isEmpty())
        assertTrue("zkopírované obrázky se mají smazat: ${images.files}", images.files.isEmpty())
        assertTrue(audio.files.isEmpty())
    }

    @Test
    fun storageFullWhileCopying_isReportedSeparately_andCleansUp() {
        images.failWith = ImageError.STORAGE_FULL

        assertEquals(ImportError.STORAGE_FULL, failure(sampleArchive))
        assertTrue(repository.allWords.isEmpty() && images.files.isEmpty() && audio.files.isEmpty())
    }

    @Test
    fun databaseFailure_removesTheCopiedMedia_andChangesNothing() {
        val failing = object : DictionaryRepository by repository {
            override suspend fun importItems(
                targetParentId: String?,
                folders: List<ImportedFolder>,
                words: List<ImportedWord>,
                replaceAll: Boolean,
            ) = throw IllegalStateException("databáze se zavřela")
        }
        val useCase = ImportArchiveUseCase(ArchiveReader(), failing, images, audio, configRepository)

        val result = runBlocking { useCase(sampleArchive, ImportRequest()) }

        assertEquals(ImportResult.Failed(ImportError.FAILED), result)
        assertTrue(images.files.isEmpty() && audio.files.isEmpty())
        assertTrue(repository.allFolders.isEmpty())
    }

    @Test
    fun sameMediaFileUsedByTwoWords_getsOneCopyPerWord() {
        val source = archive(
            manifest(words = listOf(ArchiveWord("a", "a", imageFile = "x.jpg"), ArchiveWord("b", "b", imageFile = "x.jpg"))),
            "images/x.jpg" to byteArrayOf(1),
        )

        run(source)

        val files = repository.allWords.map { it.imageFile }
        assertEquals(2, files.toSet().size)
        assertEquals(2, images.files.size)
    }

    @Test
    fun roundTrip_exportThenImport_givesTheSameDictionary() {
        val sourceRepository = FakeDictionaryRepository()
        val sourceImages = FakeImageStore()
        val dir = java.nio.file.Files.createTempDirectory("rt").toFile()
        val imageFile = java.io.File(dir, "i1.jpg").also { it.writeBytes(byteArrayOf(9, 9)) }
        val audioFile = java.io.File(dir, "a1.mp3").also { it.writeBytes(byteArrayOf(7)) }
        val realImages = object : cz.talkative.app.domain.image.ImageStore by sourceImages {
            override fun resolve(fileName: String) = imageFile.takeIf { fileName == "i1.jpg" }
        }
        val realAudio = object : cz.talkative.app.domain.audio.AudioStore by FakeAudioStore() {
            override fun resolve(fileName: String) = audioFile.takeIf { fileName == "a1.mp3" }
        }
        runBlocking {
            val food = sourceRepository.createFolder(null, "Jídlo")
            sourceRepository.createFolder(food.id, "Ovoce")
            sourceRepository.createWord(null, "chci")
            sourceRepository.createWord(food.id, "chleba", imageFile = "i1.jpg", audioFile = "a1.mp3", alternateText = "chleba!")
            sourceRepository.reorderItems(null, listOf(sourceRepository.allWords.first { it.title == "chci" }.id, food.id))
        }
        val bytes = ByteArrayOutputStream()
        val destination = object : ExportDestination {
            override fun open() = bytes
            override suspend fun discard() = Unit
        }
        runBlocking { ExportDictionaryUseCase(sourceRepository, realImages, realAudio, configRepository)(null, false, destination) }

        val result = run(BytesSource(bytes.toByteArray()))

        assertEquals(ImportResult.Success(folders = 2, words = 2, configImported = false), result)
        assertEquals(listOf("chci", "Jídlo"), items(null).map { it.title })
        assertEquals(listOf("Ovoce", "chleba"), items(folderNamed("Jídlo").id).map { it.title })
        val bread = wordNamed("chleba")
        assertEquals("chleba!", bread.alternateText)
        assertNotEquals(null, bread.imageFile)
        assertNotEquals(null, bread.audioFile)
        dir.deleteRecursively()
    }
}
