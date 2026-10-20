package cz.talkative.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.data.exchange.ContentUriExportDestination
import cz.talkative.app.data.image.FileImageStore
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.exchange.ArchiveLayout
import cz.talkative.app.domain.exchange.ExportDictionaryUseCase
import cz.talkative.app.domain.exchange.ExportResult
import cz.talkative.app.domain.exchange.ManifestCodec
import cz.talkative.app.domain.exchange.ManifestResult
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.testing.FakeConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** Export se skutečnou databází, soubory v úložišti aplikace a zápisem přes `ContentResolver`. */
@RunWith(AndroidJUnit4::class)
class ExportOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: TalkativeDatabase
    private lateinit var repository: RoomDictionaryRepository
    private lateinit var imageStore: FileImageStore
    private lateinit var audioStore: FileAudioStore
    private lateinit var export: ExportDictionaryUseCase
    private val target = File(context.cacheDir, "export-test.zip")
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        imageStore = FileImageStore(context)
        audioStore = FileAudioStore(context)
        repository = RoomDictionaryRepository(database.dictionaryDao(), { 1_000L }, imageStore, audioStore)
        export = ExportDictionaryUseCase(repository, imageStore, audioStore, FakeConfigRepository())
        target.delete()
    }

    @After
    fun tearDown() {
        database.close()
        target.delete()
        createdFiles.forEach { it.delete() }
    }

    private suspend fun savedImage(): String {
        val source = File(context.cacheDir, "source-${System.nanoTime()}.png").also { createdFiles += it }
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val saved = imageStore.saveFromUri(Uri.fromFile(source).toString()) as ImageSaveResult.Saved
        createdFiles += File(File(context.filesDir, "images"), saved.fileName)
        return saved.fileName
    }

    private fun placedAudio(content: ByteArray): String {
        val directory = File(context.filesDir, "audio").also { it.mkdirs() }
        val file = File(directory, "export-test-${System.nanoTime()}.mp3").also { createdFiles += it }
        file.writeBytes(content)
        return file.name
    }

    @Test
    fun exportedZip_opensAndContainsManifestAndAllUsedMedia() = runTest {
        val image = savedImage()
        val audioBytes = byteArrayOf(1, 2, 3, 4)
        val audio = placedAudio(audioBytes)
        val food = repository.createFolder(null, "Jídlo")
        repository.createWord(food.id, "jablko", imageFile = image, audioFile = audio, alternateText = "jabko")
        repository.createWord(null, "chci")

        val result = export(null, includeConfig = true, ContentUriExportDestination(context, Uri.fromFile(target)))

        assertEquals(ExportResult.Success(folders = 1, words = 2, missingMedia = 0), result)
        ZipFile(target).use { zip ->
            assertEquals(
                setOf(ArchiveLayout.MANIFEST, ArchiveLayout.IMAGES_DIR + image, ArchiveLayout.AUDIO_DIR + audio),
                zip.entries().asSequence().map { it.name }.toSet(),
            )
            assertArrayEquals(audioBytes, zip.getInputStream(zip.getEntry(ArchiveLayout.AUDIO_DIR + audio)).readBytes())
            assertTrue(zip.getInputStream(zip.getEntry(ArchiveLayout.IMAGES_DIR + image)).readBytes().isNotEmpty())

            val manifest = (ManifestCodec.decode(zip.getInputStream(zip.getEntry(ArchiveLayout.MANIFEST)).readBytes().toString(Charsets.UTF_8))
                as ManifestResult.Success).manifest
            assertEquals(listOf("Jídlo"), manifest.folders.map { it.title })
            assertEquals(setOf("jablko", "chci"), manifest.words.map { it.title }.toSet())
            assertNotNull(manifest.config)
            assertEquals(image, manifest.words.single { it.title == "jablko" }.imageFile)
        }
    }

    @Test
    fun deletedItems_areNotExported() = runTest {
        val food = repository.createFolder(null, "Jídlo")
        val word = repository.createWord(food.id, "chleba")
        repository.createWord(null, "chci")
        repository.deleteWord(word.id)
        repository.deleteFolder(food.id)

        val result = export(null, includeConfig = false, ContentUriExportDestination(context, Uri.fromFile(target)))

        assertEquals(ExportResult.Success(folders = 0, words = 1, missingMedia = 0), result)
    }

    @Test
    fun exportToFolder_containsOnlyThatSubtree() = runTest {
        val food = repository.createFolder(null, "Jídlo")
        val fruit = repository.createFolder(food.id, "Ovoce")
        repository.createWord(fruit.id, "jablko")
        repository.createWord(food.id, "chleba")

        val result = export(fruit.id, includeConfig = false, ContentUriExportDestination(context, Uri.fromFile(target)))

        assertEquals(ExportResult.Success(folders = 1, words = 1, missingMedia = 0), result)
    }
}
