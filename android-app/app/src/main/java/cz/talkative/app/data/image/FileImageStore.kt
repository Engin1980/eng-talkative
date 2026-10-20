package cz.talkative.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.system.ErrnoException
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.image.ImageScaling
import cz.talkative.app.domain.image.ImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/** Obrázky v `filesDir/images`. Ukládá se přes dočasný soubor, takže přerušený zápis nenechá poškozený obrázek. */
class FileImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageStore {

    private val directory = File(context.filesDir, DIRECTORY_NAME)

    override suspend fun saveFromUri(uri: String): ImageSaveResult = withContext(Dispatchers.IO) {
        val source = Uri.parse(uri)
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val (width, height) = readSize(source) ?: return@withContext ImageSaveResult.Failed(ImageError.UNREADABLE)
            val options = BitmapFactory.Options().apply { inSampleSize = ImageScaling.sampleSize(width, height) }
            decoded = open(source).use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext ImageSaveResult.Failed(ImageError.UNREADABLE)
            oriented = applyOrientation(decoded, readOrientation(source))
            scaled = scaleDown(oriented)
            ImageSaveResult.Saved(write(scaled))
        } catch (e: IOException) {
            ImageSaveResult.Failed(if (isStorageFull(e)) ImageError.STORAGE_FULL else ImageError.UNREADABLE)
        } catch (e: SecurityException) {
            ImageSaveResult.Failed(ImageError.UNREADABLE)
        } catch (e: OutOfMemoryError) {
            ImageSaveResult.Failed(ImageError.UNREADABLE)
        } finally {
            listOfNotNull(decoded, oriented, scaled).distinct().forEach { it.recycle() }
        }
    }

    /** Vstup se nejdřív zkopíruje do dočasného souboru, pak se uloží stejnou cestou jako výběr ze systému. */
    override suspend fun saveFromStream(input: InputStream): ImageSaveResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import-" + UUID.randomUUID() + ".tmp")
        try {
            temp.outputStream().use { input.copyTo(it) }
            saveFromUri(Uri.fromFile(temp).toString())
        } catch (e: IOException) {
            ImageSaveResult.Failed(if (isStorageFull(e)) ImageError.STORAGE_FULL else ImageError.UNREADABLE)
        } finally {
            temp.delete()
        }
    }

    override fun resolve(fileName: String): File? =
        safeFile(fileName)?.takeIf { it.isFile }

    override suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { safeFile(fileName)?.delete() }
    }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    private fun open(uri: Uri) =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Nelze otevřít $uri")

    private fun readSize(uri: Uri): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }

    private fun readOrientation(uri: Uri): Int =
        try {
            open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL // bez EXIF se obrázek nijak neotáčí
        }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val (width, height) = ImageScaling.scaledSize(bitmap.width, bitmap.height)
        return if (width == bitmap.width && height == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** Zapíše bitmapu přes dočasný soubor; průhledné obrázky zůstanou PNG, ostatní jdou do JPEG. */
    private fun write(bitmap: Bitmap): String {
        directory.mkdirs()
        val hasAlpha = bitmap.hasAlpha()
        val name = UUID.randomUUID().toString() + if (hasAlpha) ".png" else ".jpg"
        val target = File(directory, name)
        val temp = File(directory, "$name.tmp")
        try {
            temp.outputStream().use { out ->
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!bitmap.compress(format, JPEG_QUALITY, out)) throw IOException("Komprese obrázku selhala")
            }
            if (!temp.renameTo(target)) throw IOException("Soubor nelze přejmenovat")
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
        return name
    }

    /** Název souboru bez cesty; cokoli s lomítkem nebo `..` se odmítne. */
    private fun safeFile(fileName: String): File? =
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName == "..") null
        else File(directory, fileName)

    private fun isStorageFull(e: IOException): Boolean =
        generateSequence<Throwable>(e) { it.cause }.any {
            (it is ErrnoException && it.errno == OsConstants.ENOSPC) || it.message?.contains("ENOSPC") == true
        }

    private companion object {
        const val DIRECTORY_NAME = "images"
        const val JPEG_QUALITY = 85
    }
}
