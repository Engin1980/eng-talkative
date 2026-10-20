package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.AudioStore
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.image.ImageStore
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.testing.FakeConfigRepository
import cz.talkative.app.ui.admin.FakeDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

/** Úložiště obrázků nad skutečnými soubory v dočasné složce (export z nich čte). */
private class DirectoryImageStore(private val directory: File) : ImageStore {
    fun add(name: String, content: String): String {
        File(directory, name).writeText(content)
        return name
    }

    override fun resolve(fileName: String): File? = File(directory, fileName).takeIf { it.isFile }
    override suspend fun saveFromUri(uri: String): ImageSaveResult = error("není potřeba")
    override suspend fun saveFromStream(input: java.io.InputStream): ImageSaveResult = error("není potřeba")
    override suspend fun delete(fileName: String) = Unit
    override suspend fun deleteAllExcept(keep: Set<String>) = Unit
}

private class DirectoryAudioStore(private val directory: File) : AudioStore {
    fun add(name: String, content: String): String {
        File(directory, name).writeText(content)
        return name
    }

    override fun resolve(fileName: String): File? = File(directory, fileName).takeIf { it.isFile }
    override suspend fun saveFromUri(uri: String): AudioSaveResult = error("není potřeba")
    override suspend fun saveFromStream(input: java.io.InputStream): AudioSaveResult = error("není potřeba")
    override suspend fun delete(fileName: String) = Unit
    override suspend fun deleteAllExcept(keep: Set<String>) = Unit
}

private class MemoryDestination : ExportDestination {
    val bytes = ByteArrayOutputStream()
    var discarded = false
    override fun open(): OutputStream = bytes
    override suspend fun discard() {
        discarded = true
    }
}

class ExportDictionaryUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repository = FakeDictionaryRepository()
    private val configRepository = FakeConfigRepository(AppConfig(addWordMode = AddWordMode.DRAG, collationLanguageTag = "sk"))
    private lateinit var images: DirectoryImageStore
    private lateinit var audio: DirectoryAudioStore
    private lateinit var export: ExportDictionaryUseCase

    @Before
    fun setUp() {
        images = DirectoryImageStore(temp.newFolder("images"))
        audio = DirectoryAudioStore(temp.newFolder("audio"))
        export = ExportDictionaryUseCase(repository, images, audio, configRepository)
    }

    private fun run(rootFolderId: String? = null, includeConfig: Boolean = false, destination: ExportDestination = MemoryDestination()) =
        runBlocking { export(rootFolderId, includeConfig, destination) }

    /** Obsah ZIPu jako mapa název položky -> bajty. */
    private fun unzip(destination: MemoryDestination): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(destination.bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun manifestOf(entries: Map<String, ByteArray>): ArchiveManifest {
        val text = entries.getValue(ArchiveLayout.MANIFEST).toString(Charsets.UTF_8)
        return (ManifestCodec.decode(text) as ManifestResult.Success).manifest
    }

    private fun buildDictionary(): Map<String, String> = runBlocking {
        val food = repository.createFolder(null, "Jídlo")
        val fruit = repository.createFolder(food.id, "Ovoce")
        repository.createWord(null, "chci")
        repository.createWord(food.id, "chleba", imageFile = images.add("chleba.jpg", "JPEG-CHLEBA"))
        repository.createWord(fruit.id, "jablko", imageFile = images.add("jablko.png", "PNG"), audioFile = audio.add("jablko.mp3", "MP3"), alternateText = "jabko")
        mapOf("food" to food.id, "fruit" to fruit.id)
    }

    @Test
    fun wholeDictionary_zipHasManifestAndAllUsedMedia() {
        buildDictionary()
        val destination = MemoryDestination()

        val result = run(destination = destination)

        assertEquals(ExportResult.Success(folders = 2, words = 3, missingMedia = 0), result)
        val entries = unzip(destination)
        assertEquals(
            setOf("manifest.json", "images/chleba.jpg", "images/jablko.png", "audio/jablko.mp3"),
            entries.keys,
        )
        assertEquals("manifest.json", entries.keys.first())
        assertArrayEquals("JPEG-CHLEBA".toByteArray(), entries.getValue("images/chleba.jpg"))
        assertArrayEquals("MP3".toByteArray(), entries.getValue("audio/jablko.mp3"))
        assertFalse(destination.discarded)
    }

    @Test
    fun manifest_describesStructureTitlesOrderAndMediaReferences() {
        buildDictionary()
        val destination = MemoryDestination()
        run(destination = destination)

        val manifest = manifestOf(unzip(destination))

        assertEquals(setOf("Jídlo", "Ovoce"), manifest.folders.map { it.title }.toSet())
        val food = manifest.folders.single { it.title == "Jídlo" }
        assertEquals(food.id, manifest.folders.single { it.title == "Ovoce" }.parentId)
        val apple = manifest.words.single { it.title == "jablko" }
        assertEquals("jablko.png", apple.imageFile)
        assertEquals("jablko.mp3", apple.audioFile)
        assertEquals("jabko", apple.alternateText)
        assertNull(manifest.words.single { it.title == "chci" }.parentId)
    }

    @Test
    fun selectedFolder_exportsOnlyItsSubtree_withItselfAtTheRoot() {
        val ids = buildDictionary()
        runBlocking { repository.createWord(ids.getValue("food"), "voda") }
        val destination = MemoryDestination()

        val result = run(rootFolderId = ids.getValue("fruit"), destination = destination)

        assertEquals(ExportResult.Success(folders = 1, words = 1, missingMedia = 0), result)
        val entries = unzip(destination)
        assertEquals(setOf("manifest.json", "images/jablko.png", "audio/jablko.mp3"), entries.keys)
        val manifest = manifestOf(entries)
        assertEquals("Ovoce", manifest.folders.single().title)
        assertNull(manifest.folders.single().parentId)
        assertEquals(manifest.folders.single().id, manifest.words.single().parentId)
    }

    @Test
    fun folderWithSubfolders_includesWholeSubtree() {
        val ids = buildDictionary()
        val destination = MemoryDestination()

        val result = run(rootFolderId = ids.getValue("food"), destination = destination)

        assertEquals(ExportResult.Success(folders = 2, words = 2, missingMedia = 0), result)
    }

    @Test
    fun config_isIncludedOnlyWhenRequested() {
        val without = MemoryDestination().also { run(includeConfig = false, destination = it) }
        val with = MemoryDestination().also { run(includeConfig = true, destination = it) }

        assertNull(manifestOf(unzip(without)).config)
        val config = manifestOf(unzip(with)).config!!.toAppConfig()
        assertEquals(AddWordMode.DRAG, config.addWordMode)
        assertEquals("sk", config.collationLanguageTag)
    }

    @Test
    fun emptyDictionary_exportsValidArchive() {
        val destination = MemoryDestination()

        val result = run(destination = destination)

        assertEquals(ExportResult.Success(0, 0, 0), result)
        assertEquals(setOf("manifest.json"), unzip(destination).keys)
        assertEquals(0, manifestOf(unzip(destination)).words.size)
    }

    @Test
    fun missingMediaFile_isReported_andReferenceIsLeftOut() {
        runBlocking {
            repository.createWord(null, "chleba", imageFile = "neexistuje.jpg", audioFile = audio.add("ok.mp3", "MP3"))
        }
        val destination = MemoryDestination()

        val result = run(destination = destination)

        assertEquals(ExportResult.Success(folders = 0, words = 1, missingMedia = 1), result)
        val manifest = manifestOf(unzip(destination))
        assertNull(manifest.words.single().imageFile)
        assertEquals("ok.mp3", manifest.words.single().audioFile)
    }

    @Test
    fun sameFileUsedByTwoWords_isStoredOnce() {
        runBlocking {
            val shared = images.add("spolecny.jpg", "X")
            repository.createWord(null, "a", imageFile = shared)
            repository.createWord(null, "b", imageFile = shared)
        }
        val destination = MemoryDestination()

        run(destination = destination)

        assertEquals(setOf("manifest.json", "images/spolecny.jpg"), unzip(destination).keys)
    }

    @Test
    fun unknownFolder_failsWithoutTouchingTheDestination() {
        val destination = MemoryDestination()

        val result = run(rootFolderId = "neni", destination = destination)

        assertEquals(ExportResult.Failed(ExportError.FOLDER_MISSING), result)
        assertEquals(0, destination.bytes.size())
    }

    @Test
    fun writeFailure_discardsThePartialFile_andReportsTheError() {
        buildDictionary()
        val failing = object : ExportDestination {
            var discarded = false
            override fun open(): OutputStream = object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk se odpojil")
            }
            override suspend fun discard() {
                discarded = true
            }
        }

        val result = run(destination = failing)

        assertEquals(ExportResult.Failed(ExportError.WRITE_FAILED), result)
        assertTrue(failing.discarded)
    }

    @Test
    fun fullStorage_isReportedSeparately_andDiscardsTheFile() {
        buildDictionary()
        val full = object : ExportDestination {
            var discarded = false
            override fun open(): OutputStream = object : OutputStream() {
                override fun write(b: Int) = throw IOException("write failed: ENOSPC (No space left on device)")
            }
            override suspend fun discard() {
                discarded = true
            }
        }

        val result = run(destination = full)

        assertEquals(ExportResult.Failed(ExportError.STORAGE_FULL), result)
        assertTrue(full.discarded)
    }

    @Test
    fun destinationThatCannotBeOpened_failsAndDiscards() {
        val closed = object : ExportDestination {
            var discarded = false
            override fun open(): OutputStream = throw SecurityException("bez oprávnění")
            override suspend fun discard() {
                discarded = true
            }
        }

        val result = run(destination = closed)

        assertEquals(ExportResult.Failed(ExportError.WRITE_FAILED), result)
        assertTrue(closed.discarded)
    }

    @Test
    fun progress_countsWrittenMediaFiles() {
        buildDictionary()
        val reports = mutableListOf<Pair<Int, Int>>()

        runBlocking { export(null, false, MemoryDestination()) { done, total -> reports += done to total } }

        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), reports)
    }

    @Test
    fun orphansAndCycles_inBrokenData_areLeftOutSoTheArchiveStaysValid() {
        runBlocking {
            repository.createWord(null, "ok")
            repository.createWord("neexistujici-slozka", "sirotek")
        }
        val destination = MemoryDestination()

        val result = run(destination = destination)

        assertEquals(ExportResult.Success(0, 1, 0), result)
        assertNotNull(manifestOf(unzip(destination)))
    }
}
