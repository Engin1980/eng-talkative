package cz.talkative.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.image.FileImageStore
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.domain.image.ImageSaveResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileImageStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: FileImageStore
    private val imagesDir get() = File(context.filesDir, "images")
    private val sources = mutableListOf<File>()

    @Before
    fun setUp() {
        imagesDir.deleteRecursively()
        store = FileImageStore(context)
    }

    @After
    fun tearDown() {
        imagesDir.deleteRecursively()
        sources.forEach { it.delete() }
    }

    /** Vytvoří zdrojový JPEG [width]×[height] (levá polovina červená, pravá modrá) s EXIF orientací. */
    private fun createSource(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.RED)
        canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), android.graphics.Paint().apply { color = Color.BLUE })
        val file = File(context.cacheDir, "source-${System.nanoTime()}.jpg").also { sources += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file).toString()
    }

    private fun save(uri: String): String {
        val result = runBlocking { store.saveFromUri(uri) }
        assertTrue("očekáváno uložení, ale: $result", result is ImageSaveResult.Saved)
        return (result as ImageSaveResult.Saved).fileName
    }

    private fun sizeOf(fileName: String): Pair<Int, Int> {
        val file = store.resolve(fileName)
        assertNotNull(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file!!.absolutePath, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun largeImage_isDownscaledToLimit_andOnlyTheSmallFileIsStored() {
        val source = createSource(2000, 1000)

        val name = save(source)

        assertEquals(512 to 256, sizeOf(name))
        assertEquals(listOf(name), imagesDir.list()!!.toList()) // žádný originál ani dočasný soubor
        assertTrue(store.resolve(name)!!.length() < File(Uri.parse(source).path!!).length())
    }

    @Test
    fun smallImage_isNotEnlarged() {
        assertEquals(100 to 50, sizeOf(save(createSource(100, 50))))
    }

    @Test
    fun exifRotation_isApplied() {
        // 2000×1000 s orientací 90° musí být po uložení na výšku
        val name = save(createSource(2000, 1000, ExifInterface.ORIENTATION_ROTATE_90))

        assertEquals(256 to 512, sizeOf(name))
    }

    @Test
    fun exifRotation_movesPixelsToTheRightPlace() {
        // levá červená / pravá modrá polovina po otočení o 90° po směru hodin: červená nahoře, modrá dole
        val name = save(createSource(400, 200, ExifInterface.ORIENTATION_ROTATE_90))

        val bitmap = BitmapFactory.decodeFile(store.resolve(name)!!.absolutePath)
        assertEquals(200 to 400, bitmap.width to bitmap.height)
        val top = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
        val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
        assertTrue("nahoře má být červená, je ${Integer.toHexString(top)}", Color.red(top) > 200 && Color.blue(top) < 80)
        assertTrue("dole má být modrá, je ${Integer.toHexString(bottom)}", Color.blue(bottom) > 200 && Color.red(bottom) < 80)
        bitmap.recycle()
    }

    @Test
    fun unreadableAndNonImageSources_failWithoutStoringAnything() {
        val missing = Uri.fromFile(File(context.cacheDir, "neexistuje.jpg")).toString()
        val textFile = File(context.cacheDir, "text-${System.nanoTime()}.txt").also { sources += it; it.writeText("není obrázek") }

        val first = runBlocking { store.saveFromUri(missing) }
        val second = runBlocking { store.saveFromUri(Uri.fromFile(textFile).toString()) }

        assertEquals(ImageSaveResult.Failed(ImageError.UNREADABLE), first)
        assertEquals(ImageSaveResult.Failed(ImageError.UNREADABLE), second)
        assertTrue(imagesDir.list().isNullOrEmpty())
    }

    @Test
    fun delete_removesFile_andUnknownNamesAreHarmless() {
        val name = save(createSource(100, 100))

        runBlocking {
            store.delete(name)
            store.delete(name) // už neexistuje
            store.delete("../../databases/talkative.db") // nesmí uniknout z adresáře obrázků
        }

        assertNull(store.resolve(name))
    }

    @Test
    fun resolve_refusesPathsOutsideImageDirectory() {
        assertNull(store.resolve("../cache/x"))
        assertNull(store.resolve(""))
    }

    @Test
    fun deleteAllExcept_keepsOnlyReferencedFiles() {
        val keep = save(createSource(100, 100))
        val drop = save(createSource(100, 100))

        runBlocking { store.deleteAllExcept(setOf(keep)) }

        assertNotNull(store.resolve(keep))
        assertNull(store.resolve(drop))
    }

    @Test
    fun transparentPng_staysPng_withItsTransparency() {
        // Piktogramy ARASAAC jsou PNG s průhledným pozadím; převod na JPEG by z něj udělal černé.
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888) // celé průhledné
        android.graphics.Canvas(bitmap).drawCircle(250f, 250f, 100f, android.graphics.Paint().apply { color = Color.RED })
        val file = File(context.cacheDir, "alpha-${System.nanoTime()}.png").also { sources += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val name = save(Uri.fromFile(file).toString())

        assertTrue("očekáváno PNG, ale: $name", name.endsWith(".png"))
        val stored = BitmapFactory.decodeFile(store.resolve(name)!!.absolutePath)
        assertEquals(0, Color.alpha(stored.getPixel(5, 5))) // roh zůstal průhledný
        assertEquals(Color.RED, stored.getPixel(250, 250)) // kruh uprostřed zůstal
    }
}
