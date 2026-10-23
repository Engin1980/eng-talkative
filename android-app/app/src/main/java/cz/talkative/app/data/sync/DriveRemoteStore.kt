package cz.talkative.app.data.sync

import cz.talkative.app.domain.sync.AccessTokenProvider
import cz.talkative.app.domain.sync.MediaKind
import cz.talkative.app.domain.sync.MediaRef
import cz.talkative.app.domain.sync.RemoteMedia
import cz.talkative.app.domain.sync.RemoteStore
import cz.talkative.app.domain.sync.SyncCodec
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncException
import cz.talkative.app.domain.sync.SyncMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Adresy Google Drive REST API v3; v testech se nahrazují adresou `MockWebServer`. */
data class DriveEndpoints(
    val apiBase: String = "https://www.googleapis.com/drive/v3",
    val uploadBase: String = "https://www.googleapis.com/upload/drive/v3",
)

/**
 * [RemoteStore] nad Google Drive REST API v3 ve skryté složce aplikace (`appDataFolder`). Metadata jsou soubor
 * `dictionary.json`, média soubory `img_<název>` a `aud_<název>` (viz `format-synchronizace.md`). Drive dovoluje v této
 * složce víc souborů se stejným názvem, proto se při čtení kopie metadat sloučí a při zápisu přebytečné smažou.
 * Při odpovědi 401 se zažádá o nový token a požadavek se zopakuje jednou.
 */
class DriveRemoteStore(
    private val client: OkHttpClient,
    private val tokens: AccessTokenProvider,
    private val endpoints: DriveEndpoints = DriveEndpoints(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : RemoteStore {

    private data class DriveFile(val id: String, val name: String, val modifiedMillis: Long)

    // ---- metadata

    override suspend fun readMetadata(): String? {
        val copies = listFiles(nameEquals = METADATA_NAME).sortedByDescending { it.modifiedMillis }
        if (copies.isEmpty()) return null
        val texts = copies.map { downloadText(it.id) }
        if (texts.size == 1) return texts.single()
        // Dvě zařízení si při první synchronizaci vytvořila každé vlastní soubor: sloučí se do jednoho.
        val merged = texts.map { SyncCodec.decode(it) }.reduce { acc, items -> SyncMerge.merge(acc, items, nowMillis()).merged }
        return SyncCodec.encode(merged)
    }

    override suspend fun writeMetadata(json: String) {
        val copies = listFiles(nameEquals = METADATA_NAME).sortedByDescending { it.modifiedMillis }
        if (copies.isEmpty()) {
            create(METADATA_NAME, "application/json", json.toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
        } else {
            updateContent(copies.first().id, json)
            copies.drop(1).forEach { delete(it.id) }
        }
    }

    // ---- média

    override suspend fun listMedia(): List<RemoteMedia> =
        listFiles(nameEquals = null).mapNotNull { file ->
            parseMediaName(file.name)?.let { RemoteMedia(it, file.modifiedMillis) }
        }

    override suspend fun uploadMedia(ref: MediaRef, file: File) {
        create(driveName(ref), mimeType(ref), file.asRequestBody(mimeType(ref).toMediaType()))
    }

    override suspend fun downloadMedia(ref: MediaRef, target: File) {
        val found = listFiles(nameEquals = driveName(ref)).firstOrNull()
            ?: throw SyncException(SyncError.BAD_RESPONSE, "Soubor ${driveName(ref)} na Drive není")
        withContext(Dispatchers.IO) {
            try {
                send({ get(filesUrl(found.id) { addQueryParameter("alt", "media") }, it) }) { response ->
                    val body = response.body ?: throw SyncException(SyncError.BAD_RESPONSE, "Prázdná odpověď")
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
            } catch (e: Throwable) {
                target.delete() // po chybě nesmí zůstat rozpracovaný soubor
                throw e
            }
        }
    }

    override suspend fun deleteMedia(ref: MediaRef) {
        listFiles(nameEquals = driveName(ref)).forEach { delete(it.id) }
    }

    // ---- volání Drive API

    private suspend fun listFiles(nameEquals: String?): List<DriveFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = endpoints.apiBase.toHttpUrl().newBuilder()
                .addPathSegment("files")
                .addQueryParameter("spaces", SPACE)
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                .apply {
                    if (nameEquals != null) addQueryParameter("q", "name = '${escape(nameEquals)}' and '$SPACE' in parents and trashed = false")
                    if (pageToken != null) addQueryParameter("pageToken", pageToken)
                }
                .build()
            val parsed = send({ get(url, it) }) { response -> parseJson(response) }
            (parsed["files"] as? JsonArray).orEmpty().forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                result += DriveFile(id, name, parseTime(obj["modifiedTime"]?.jsonPrimitive?.contentOrNull))
            }
            pageToken = parsed["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pageToken != null)
        result
    }

    /** Soubor se obsahem v těle odpovědi jako text (metadata). */
    private suspend fun downloadText(id: String): String = withContext(Dispatchers.IO) {
        send({ get(filesUrl(id) { addQueryParameter("alt", "media") }, it) }) { response ->
            val text = response.body?.source()?.let { source ->
                source.request(MAX_METADATA_BYTES + 1)
                if (source.buffer.size > MAX_METADATA_BYTES) throw SyncException(SyncError.BAD_RESPONSE, "Metadata jsou příliš velká")
                source.readUtf8()
            } ?: throw SyncException(SyncError.BAD_RESPONSE, "Prázdná odpověď")
            text
        }
    }

    /** Vytvoří soubor v `appDataFolder` (multipart: JSON s názvem a rodičem + obsah). */
    private suspend fun create(name: String, mimeType: String, content: RequestBody) = withContext(Dispatchers.IO) {
        val meta = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(JsonPrimitive(SPACE)) })
        }.toString()
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(content)
            .build()
        val url = endpoints.uploadBase.toHttpUrl().newBuilder()
            .addPathSegment("files")
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id")
            .build()
        send({ post(url, body, it) }) { }
    }

    private suspend fun updateContent(id: String, json: String) = withContext(Dispatchers.IO) {
        val url = endpoints.uploadBase.toHttpUrl().newBuilder()
            .addPathSegments("files/$id")
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", "id")
            .build()
        val body = json.toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType())
        send({ Request.Builder().url(url).header("Authorization", "Bearer $it").patch(body).build() }) { }
    }

    private suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        send({ Request.Builder().url(filesUrl(id) { }).header("Authorization", "Bearer $it").delete().build() }, allow404 = true) { }
    }

    private fun filesUrl(id: String, configure: HttpUrl.Builder.() -> Unit): HttpUrl =
        endpoints.apiBase.toHttpUrl().newBuilder().addPathSegments("files/$id").apply(configure).build()

    private fun get(url: HttpUrl, token: String) = Request.Builder().url(url).header("Authorization", "Bearer $token").build()

    private fun post(url: HttpUrl, body: RequestBody, token: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build()

    /**
     * Pošle požadavek s tokenem; při 401 zažádá o nový token a zopakuje ho jednou. Chyby sítě a stavové kódy se
     * převádí na [SyncException]. [allow404] bere chybějící soubor jako úspěch (mazání).
     */
    private suspend fun <T> send(factory: (token: String) -> Request, allow404: Boolean = false, handle: (Response) -> T): T {
        var refreshed = false
        while (true) {
            val token = tokens.accessToken(forceRefresh = refreshed)
            val request = factory(token)
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> return handle(response)
                        response.code == 404 && allow404 -> return handle(response)
                        response.code == 401 && !refreshed -> refreshed = true
                        response.code == 401 -> throw SyncException(SyncError.UNAUTHORIZED, "Drive odmítl přístupový token")
                        response.code == 404 -> throw SyncException(SyncError.BAD_RESPONSE, "Soubor na Drive neexistuje")
                        else -> throw SyncException(SyncError.SERVER_ERROR, "Drive vrátil HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                throw SyncException(SyncError.NO_NETWORK, "Drive není dosažitelný", e)
            }
        }
    }

    private fun parseJson(response: Response): JsonObject {
        val text = response.body?.string().orEmpty()
        return try {
            Json.parseToJsonElement(text) as? JsonObject ?: throw SyncException(SyncError.BAD_RESPONSE, "Odpověď Drive není objekt")
        } catch (e: SerializationException) {
            throw SyncException(SyncError.BAD_RESPONSE, "Odpověď Drive nejde přečíst", e)
        }
    }

    private fun parseTime(text: String?): Long = try {
        if (text == null) 0L else Instant.parse(text).toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        0L
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        const val METADATA_NAME = "dictionary.json"
        private const val SPACE = "appDataFolder"
        private const val IMAGE_PREFIX = "img_"
        private const val AUDIO_PREFIX = "aud_"
        private const val MAX_METADATA_BYTES = 20L * 1024 * 1024

        fun driveName(ref: MediaRef): String = (if (ref.kind == MediaKind.IMAGE) IMAGE_PREFIX else AUDIO_PREFIX) + ref.name

        /** Obrácená [driveName]; soubory s jiným názvem (např. `dictionary.json`) nejsou média. */
        fun parseMediaName(driveName: String): MediaRef? = when {
            driveName.startsWith(IMAGE_PREFIX) && driveName.length > IMAGE_PREFIX.length -> MediaRef(MediaKind.IMAGE, driveName.removePrefix(IMAGE_PREFIX))
            driveName.startsWith(AUDIO_PREFIX) && driveName.length > AUDIO_PREFIX.length -> MediaRef(MediaKind.AUDIO, driveName.removePrefix(AUDIO_PREFIX))
            else -> null
        }

        private fun mimeType(ref: MediaRef): String {
            val extension = ref.name.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                else -> "application/octet-stream"
            }
        }

        /** HTTP klient s delšími limity než u hledání piktogramů: přenášejí se obrázky a zvuky. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
