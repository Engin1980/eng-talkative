package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.GridSize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCodecTest {

    private val folder = ArchiveFolder("f1", "Jídlo", parentId = null, sortOrder = 0)
    private val word = ArchiveWord(
        "w1", "chci", parentId = "f1", sortOrder = 1,
        imageFile = "a.jpg", alternateText = "já chci", audioFile = "b.mp3",
    )

    private fun roundTrip(manifest: ArchiveManifest): ArchiveManifest {
        val result = ManifestCodec.decode(ManifestCodec.encode(manifest))
        return (result as ManifestResult.Success).manifest
    }

    private fun errorOf(text: String) = (ManifestCodec.decode(text) as ManifestResult.Failure).error

    private fun errorOf(manifest: ArchiveManifest) = errorOf(ManifestCodec.encode(manifest))

    @Test
    fun emptyDictionary_roundTrips() {
        val manifest = ArchiveManifest(ARCHIVE_SCHEMA_VERSION)
        assertEquals(manifest, roundTrip(manifest))
    }

    @Test
    fun dictionaryWithMedia_roundTripsWithEveryField() {
        val manifest = ArchiveManifest(ARCHIVE_SCHEMA_VERSION, listOf(folder), listOf(word, ArchiveWord("w2", "ahoj")))
        assertEquals(manifest, roundTrip(manifest))
    }

    @Test
    fun configIsOptional_andRoundTripsWhenPresent() {
        val config = AppConfig(
            addWordMode = AddWordMode.DRAG,
            allowReorderInSentence = false,
            addHoldMillis = 1_500L,
            removeHoldMillis = 2_500L,
            adminHoldMillis = 12_000L,
            speakWordOnTap = true,
            gridPortrait = GridSize(2, 4),
            gridLandscape = GridSize(5, 2),
            collationLanguageTag = "sk",
            hideCaptionsInSentence = true,
            autoClearAfterPlayback = true,
            textScalePercent = 150,
        )
        val without = roundTrip(ArchiveManifest(ARCHIVE_SCHEMA_VERSION))
        val with = roundTrip(ArchiveManifest(ARCHIVE_SCHEMA_VERSION, config = ArchiveConfig.from(config)))

        assertEquals(null, without.config)
        assertEquals(config, with.config!!.toAppConfig())
    }

    @Test
    fun configOutsideTheLimits_isClampedOnImport() {
        val config = ArchiveConfig(removeHoldMillis = 999_000L, gridPortrait = ArchiveGrid(columns = 50, rows = 0))
        val app = config.toAppConfig()
        assertEquals(AppConfig().copy(removeHoldMillis = app.removeHoldMillis, gridPortrait = app.gridPortrait), app)
        assertTrue(app.removeHoldMillis < 999_000L)
        assertTrue(app.gridPortrait.columns in 1..6 && app.gridPortrait.rows in 1..6)
    }

    @Test
    fun archive_containsOnlyAllowedKeys_noProfileTimestampsOrCredentials() {
        val manifest = ArchiveManifest(
            ARCHIVE_SCHEMA_VERSION, listOf(folder, ArchiveFolder("f2", "Ovoce", parentId = "f1")), listOf(word),
            ArchiveConfig.from(AppConfig()),
        )
        val root = Json.parseToJsonElement(ManifestCodec.encode(manifest)).jsonObject

        assertEquals(setOf("schemaVersion", "folders", "words", "config"), root.keys)
        assertEquals(setOf("id", "title", "parentId", "sortOrder"), keysOf(root, "folders"))
        assertEquals(
            setOf("id", "title", "parentId", "sortOrder", "imageFile", "alternateText", "audioFile"),
            keysOf(root, "words"),
        )
        assertEquals(
            setOf(
                "addWordMode", "allowReorderInSentence", "addHoldMillis", "removeHoldMillis", "adminHoldMillis", "speakWordOnTap",
                "gridPortrait", "gridLandscape", "collationLanguageTag", "hideCaptionsInSentence", "autoClearAfterPlayback",
                "textScalePercent",
            ),
            root.getValue("config").jsonObject.keys,
        )
    }

    private fun keysOf(root: JsonObject, name: String): Set<String> =
        root.getValue(name).jsonArray.last().jsonObject.keys

    @Test
    fun unknownKeys_andUnknownEnumValues_doNotBreakReading() {
        val text = """{"schemaVersion":1,"future":true,"config":{"addWordMode":"HOVER","newOption":1}}"""
        val manifest = (ManifestCodec.decode(text) as ManifestResult.Success).manifest
        assertEquals(AddWordMode.TAP, manifest.config!!.addWordMode)
    }

    @Test
    fun garbage_isNotAnArchive() {
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, errorOf("PK\u0003\u0004 tohle není json"))
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, errorOf(""))
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, errorOf("""{"neco":"jineho"}"""))
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, errorOf("[1,2,3]"))
    }

    @Test
    fun newerSchemaVersion_isRejectedWithItsOwnError() {
        assertEquals(ArchiveError.NEWER_VERSION, errorOf("""{"schemaVersion":${ARCHIVE_SCHEMA_VERSION + 1}}"""))
        assertEquals(ArchiveError.INVALID_VERSION, errorOf("""{"schemaVersion":0}"""))
    }

    @Test
    fun inconsistentReferences_areRejected() {
        fun manifest(folders: List<ArchiveFolder> = emptyList(), words: List<ArchiveWord> = emptyList()) =
            ArchiveManifest(ARCHIVE_SCHEMA_VERSION, folders, words)

        val cases = mapOf(
            "duplicitní ID složek" to manifest(listOf(folder, folder)),
            "stejné ID složky a slovíčka" to manifest(listOf(folder), listOf(ArchiveWord("f1", "x"))),
            "neexistující rodič slovíčka" to manifest(words = listOf(ArchiveWord("w", "x", parentId = "nic"))),
            "neexistující rodič složky" to manifest(listOf(ArchiveFolder("f", "x", parentId = "nic"))),
            "složka sama v sobě" to manifest(listOf(ArchiveFolder("f", "x", parentId = "f"))),
            "cyklus dvou složek" to manifest(listOf(ArchiveFolder("a", "A", "b"), ArchiveFolder("b", "B", "a"))),
            "prázdný titulek" to manifest(listOf(ArchiveFolder("f", "  "))),
            "prázdné ID" to manifest(words = listOf(ArchiveWord("", "x"))),
            "soubor s cestou ven" to manifest(words = listOf(ArchiveWord("w", "x", imageFile = "../../x.jpg"))),
            "soubor s lomítkem" to manifest(words = listOf(ArchiveWord("w", "x", audioFile = "dir/x.mp3"))),
            "soubor se zpětným lomítkem" to manifest(words = listOf(ArchiveWord("w", "x", audioFile = "dir\\x.mp3"))),
        )
        for ((name, case) in cases) assertEquals(name, ArchiveError.INCONSISTENT, errorOf(case))
    }
}
