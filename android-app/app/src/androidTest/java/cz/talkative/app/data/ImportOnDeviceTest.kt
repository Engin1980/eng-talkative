package cz.talkative.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.data.exchange.ContentUriArchiveSource
import cz.talkative.app.data.exchange.ContentUriExportDestination
import cz.talkative.app.data.exchange.ResourceArchiveNotice
import cz.talkative.app.data.image.FileImageStore
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.exchange.ArchiveLayout
import cz.talkative.app.domain.exchange.ArchiveReader
import cz.talkative.app.domain.exchange.ExportDictionaryUseCase
import cz.talkative.app.domain.exchange.ExportResult
import cz.talkative.app.domain.exchange.ImportArchiveUseCase
import cz.talkative.app.domain.exchange.ImportError
import cz.talkative.app.domain.exchange.ImportRequest
import cz.talkative.app.domain.exchange.ImportResult
import cz.talkative.app.domain.exchange.ImportSelection
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.testing.AudioFixtures
import cz.talkative.app.testing.FakeConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export a import se skutečnou databází, skutečnými soubory obrázků a MP3 a zápisem přes `ContentResolver`. */
@RunWith(AndroidJUnit4::class)
class ImportOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val imageDir get() = File(context.filesDir, "images")
    private val audioDir get() = File(context.filesDir, "audio")
    private lateinit var database: TalkativeDatabase
    private lateinit var repository: RoomDictionaryRepository
    private lateinit var imageStore: FileImageStore
    private lateinit var audioStore: FileAudioStore
    private lateinit var configRepository: FakeConfigRepository
    private lateinit var export: ExportDictionaryUseCase
    private lateinit var import: ImportArchiveUseCase
    private val archiveFile = File(context.cacheDir, "import-test.zip")
    private val scratch = mutableListOf<File>()

    @Before
    fun setUp() {
        imageDir.deleteRecursively()
        audioDir.deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        imageStore = FileImageStore(context)
        audioStore = FileAudioStore(context)
        configRepository = FakeConfigRepository()
        repository = RoomDictionaryRepository(database.dictionaryDao(), { 1_000L }, imageStore, audioStore)
        export = ExportDictionaryUseCase(repository, imageStore, audioStore, configRepository, ResourceArchiveNotice(context))
        import = ImportArchiveUseCase(ArchiveReader(), repository, imageStore, audioStore, configRepository)
        archiveFile.delete()
    }

    @After
    fun tearDown() {
        database.close()
        archiveFile.delete()
        scratch.forEach { it.delete() }
        imageDir.deleteRecursively()
        audioDir.deleteRecursively()
    }

    private suspend fun savedImage(width: Int = 40, height: Int = 30): String {
        val source = File(context.cacheDir, "src-${System.nanoTime()}.png").also { scratch += it }
        source.outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return (imageStore.saveFromUri(Uri.fromFile(source).toString()) as ImageSaveResult.Saved).fileName
    }

    private suspend fun savedAudio(seconds: Double = 2.0): String {
        val (file, uri) = AudioFixtures.writeToCache(context, "src-${System.nanoTime()}.mp3", AudioFixtures.silentMp3(seconds))
        scratch += file
        return (audioStore.saveFromUri(uri) as AudioSaveResult.Saved).fileName
    }

    private suspend fun exportToArchive(rootFolderId: String? = null, includeConfig: Boolean = false) {
        val result = export(rootFolderId, includeConfig, ContentUriExportDestination(context, Uri.fromFile(archiveFile)))
        assertTrue("export: $result", result is ExportResult.Success)
    }

    private fun source() = ContentUriArchiveSource(context, Uri.fromFile(archiveFile))

    private suspend fun doImport(request: ImportRequest = ImportRequest()) = import(source(), request)

    private suspend fun items(parentId: String?): List<DictionaryItem> = repository.observeItems(parentId).first()

    private suspend fun titles(parentId: String?) = items(parentId).map { it.title }

    private fun durationMillis(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally {
            retriever.release()
        }
    }

    private suspend fun buildSample() {
        val food = repository.createFolder(null, "Jídlo")
        val fruit = repository.createFolder(food.id, "Ovoce")
        repository.createWord(null, "chci")
        repository.createWord(food.id, "chleba", imageFile = savedImage())
        repository.createWord(fruit.id, "jablko", imageFile = savedImage(60, 20), audioFile = savedAudio(), alternateText = "jabko")
        repository.createWord(fruit.id, "hruška")
        repository.reorderItems(null, listOf(food.id, repository.snapshot().words.first { it.title == "chci" }.id))
    }

    @Test
    fun exportDeleteImport_returnsTheSameDictionary() = runTest {
        buildSample()
        exportToArchive()
        val food = repository.snapshot().folders.first { it.title == "Jídlo" }
        val chci = repository.snapshot().words.first { it.title == "chci" }
        repository.deleteItems(listOf(food.id, chci.id))
        assertTrue(repository.snapshot().let { it.folders.isEmpty() && it.words.isEmpty() })
        assertTrue(imageDir.listFiles().isNullOrEmpty() && audioDir.listFiles().isNullOrEmpty()) // smazání uklidilo soubory

        val result = doImport()

        assertEquals(ImportResult.Success(folders = 2, words = 4, configImported = false), result)
        assertEquals(listOf("Jídlo", "chci"), titles(null)) // ruční pořadí
        val importedFood = repository.snapshot().folders.first { it.title == "Jídlo" }
        assertEquals(listOf("Ovoce", "chleba"), titles(importedFood.id))
        val fruit = repository.snapshot().folders.first { it.title == "Ovoce" }
        assertEquals(listOf("jablko", "hruška"), titles(fruit.id))

        val apple = repository.snapshot().words.first { it.title == "jablko" }
        assertEquals("jabko", apple.alternateText)
        val image = imageStore.resolve(apple.imageFile!!)!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(image.path, it) }
        assertEquals(60, bounds.outWidth)
        assertEquals(20, bounds.outHeight)
        val audio = audioStore.resolve(apple.audioFile!!)!!
        assertTrue(durationMillis(audio) in 1_500..2_500)
        assertNotNull(imageStore.resolve(repository.snapshot().words.first { it.title == "chleba" }.imageFile!!))
    }

    @Test
    fun importingTwice_createsDuplicates_withOwnFiles_andKeepsTheOriginals() = runTest {
        buildSample()
        exportToArchive()
        val originalFiles = imageDir.listFiles()!!.map { it.name }.toSet() + audioDir.listFiles()!!.map { it.name }.toSet()

        doImport()

        assertEquals(2, repository.snapshot().folders.count { it.title == "Jídlo" })
        val apples = repository.snapshot().words.filter { it.title == "jablko" }
        assertEquals(2, apples.size)
        assertNotEquals(apples[0].imageFile, apples[1].imageFile)
        assertNotEquals(apples[0].audioFile, apples[1].audioFile)
        assertTrue(originalFiles.all { name -> File(imageDir, name).exists() || File(audioDir, name).exists() })
        assertEquals(2, apples.mapNotNull { it.audioFile }.toSet().size)
    }

    @Test
    fun partialImport_intoTarget_copiesOnlyTheSelectedFoldersMedia() = runTest {
        buildSample()
        exportToArchive()
        val target = repository.createFolder(null, "Cíl")
        val fruitId = repository.snapshot().folders.first { it.title == "Ovoce" }.id
        // v archivu mají složky nová ID; vybírá se podle ID z manifestu
        val manifestFruitId = (ArchiveReader().read(source()) as cz.talkative.app.domain.exchange.ReadResult.Success)
            .manifest.folders.first { it.title == "Ovoce" }.id
        val imagesBefore = imageDir.listFiles()!!.size
        val audioBefore = audioDir.listFiles()!!.size

        val result = doImport(ImportRequest(ImportSelection.Folders(setOf(manifestFruitId)), targetFolderId = target.id))

        assertEquals(ImportResult.Success(folders = 1, words = 2, configImported = false), result)
        assertEquals(listOf("Ovoce"), titles(target.id))
        assertEquals(imagesBefore + 1, imageDir.listFiles()!!.size) // jen obrázek jablka
        assertEquals(audioBefore + 1, audioDir.listFiles()!!.size)
        val fruitFolders = repository.snapshot().folders.filter { it.title == "Ovoce" }
        assertEquals(2, fruitFolders.size)
        assertEquals(2, fruitFolders.map { it.id }.toSet().size)
        assertTrue(fruitId in fruitFolders.map { it.id }) // původní složka zůstala
    }

    @Test
    fun replaceAll_deletesTheOldFilesAndWords() = runTest {
        buildSample()
        val old = repository.snapshot().words.mapNotNull { it.imageFile } + repository.snapshot().words.mapNotNull { it.audioFile }
        exportToArchive()
        repository.createWord(null, "navíc", imageFile = savedImage())

        doImport(ImportRequest(replaceAll = true))

        assertTrue(repository.snapshot().words.none { it.title == "navíc" })
        assertEquals(4, repository.snapshot().words.size)
        assertEquals(2, repository.snapshot().folders.size)
        val remaining = imageDir.listFiles()!!.size + audioDir.listFiles()!!.size
        assertEquals(3, remaining) // dva obrázky a jedno MP3 z archivu; původní soubory (i "navíc") jsou pryč
        assertTrue(old.none { File(imageDir, it).exists() || File(audioDir, it).exists() })
    }

    @Test
    fun corruptImage_inTheArchive_failsWithoutAnyChange() = runTest {
        archiveFile.outputStream().use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(ArchiveLayout.MANIFEST))
                zip.write(
                    """{"schemaVersion":1,"words":[{"id":"w","title":"slovo","imageFile":"x.png"},{"id":"v","title":"jiné"}]}""".toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(ArchiveLayout.IMAGES_DIR + "x.png"))
                zip.write("tohle není obrázek".toByteArray())
                zip.closeEntry()
            }
        }
        repository.createWord(null, "stávající")

        val result = doImport()

        assertEquals(ImportResult.Failed(ImportError.INVALID_ARCHIVE), result)
        assertEquals(listOf("stávající"), titles(null))
        assertTrue(imageDir.listFiles().isNullOrEmpty())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("import-") && it.name.endsWith(".tmp") })
    }

    @Test
    fun notAnArchive_andTruncatedArchive_areRejected() = runTest {
        archiveFile.writeText("tohle je obyčejný text, ne ZIP")
        assertEquals(ImportResult.Failed(ImportError.NOT_AN_ARCHIVE), doImport())

        buildSample()
        exportToArchive()
        val bytes = archiveFile.readBytes()
        archiveFile.writeBytes(bytes.copyOf(bytes.size / 2))
        val before = repository.snapshot().words.size

        val result = doImport()

        assertTrue(result is ImportResult.Failed)
        assertEquals(before, repository.snapshot().words.size)
    }

    @Test
    fun config_travelsWithTheArchive_whenAskedFor() = runTest {
        configRepository.update { AppConfig(addWordMode = AddWordMode.DRAG, collationLanguageTag = "pl") }
        exportToArchive(includeConfig = true)
        configRepository.update { AppConfig() }

        assertEquals(ImportResult.Success(0, 0, configImported = false), doImport())
        assertEquals(AppConfig(), configRepository.current)

        assertEquals(ImportResult.Success(0, 0, configImported = true), doImport(ImportRequest(importConfig = true)))
        assertEquals(AddWordMode.DRAG, configRepository.current.addWordMode)
        assertEquals("pl", configRepository.current.collationLanguageTag)
    }

    @Test
    fun importedTombstones_doNotResurface_andOrderIsStable() = runTest {
        val a = repository.createWord(null, "a")
        repository.createWord(null, "b")
        repository.deleteWord(a.id)
        exportToArchive()

        doImport()

        val roots: List<DictionaryItem> = items(null)
        assertEquals(listOf("b", "b"), roots.map { it.title })
        assertTrue(roots.all { it is Word } && roots.none { it is Folder })
        assertEquals(listOf(1, 2), roots.map { it.sortOrder }) // původní b má 1, nové jde za něj
    }
}
