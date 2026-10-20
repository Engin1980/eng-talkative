package cz.talkative.app.data.pictogram

import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.domain.pictogram.PictogramSearch
import cz.talkative.app.domain.pictogram.PictogramSearchResult
import cz.talkative.app.domain.pictogram.PictogramUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Adresy serveru ARASAAC; v testech se nahrazují adresou `MockWebServer`. */
data class ArasaacEndpoints(
    val apiBase: String = "https://api.arasaac.org",
    val staticBase: String = PictogramUrls.DEFAULT_STATIC_BASE,
)

/** Hledání v API ARASAAC (česky). Klíč ani přihlášení se nepoužívá; na server odchází jen hledaný text. */
class ArasaacPictogramSearch(
    private val client: OkHttpClient,
    private val endpoints: ArasaacEndpoints = ArasaacEndpoints(),
) : PictogramSearch {

    override suspend fun search(text: String): PictogramSearchResult {
        val query = text.trim()
        if (query.isEmpty()) return PictogramSearchResult.Found(emptyList())
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(searchUrl(query)).build()).execute().use { response ->
                    when {
                        // Server odpovídá 404 s prázdným polem, když se nic nenašlo.
                        response.code == 404 -> PictogramSearchResult.Found(emptyList())
                        !response.isSuccessful -> PictogramSearchResult.Failed(PictogramError.SERVER_ERROR)
                        else -> parse(response.peekBody(MAX_BODY_BYTES).string())
                    }
                }
            } catch (e: SocketTimeoutException) {
                PictogramSearchResult.Failed(PictogramError.TIMEOUT)
            } catch (e: IOException) {
                PictogramSearchResult.Failed(if (e is InterruptedIOException) PictogramError.TIMEOUT else PictogramError.NO_NETWORK)
            }
        }
    }

    /** Hledaný text jde jako jeden segment cesty, takže se správně zakóduje včetně diakritiky a mezer. */
    private fun searchUrl(query: String): HttpUrl =
        endpoints.apiBase.toHttpUrl().newBuilder()
            .addPathSegments("v1/pictograms/cs/search")
            .addPathSegment(query)
            .build()

    private fun parse(body: String): PictogramSearchResult {
        val array = try {
            Json.parseToJsonElement(body) as? JsonArray
        } catch (e: SerializationException) {
            null
        } ?: return PictogramSearchResult.Failed(PictogramError.BAD_RESPONSE)
        val pictograms = array.mapNotNull { toPictogram(it) }.take(MAX_RESULTS)
        return PictogramSearchResult.Found(pictograms)
    }

    /** Položka bez platného ID nebo bez klíčového slova se přeskočí. */
    private fun toPictogram(element: JsonElement): Pictogram? {
        val item = element as? JsonObject ?: return null
        val id = item["_id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return null
        val title = (item["keywords"] as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("keyword")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            ?: return null
        return Pictogram(id, title, PictogramUrls.image(id, PictogramUrls.PREVIEW_SIZE, endpoints.staticBase))
    }

    companion object {
        const val MAX_RESULTS = 100

        /** Delší odpověď se useknutím stane nečitelnou, tedy chybou odpovědi. */
        const val MAX_BODY_BYTES = 5L * 1024 * 1024
        const val TIMEOUT_SECONDS = 10L

        /** HTTP klient s časovým limitem 10 s pro hledání i stahování. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
