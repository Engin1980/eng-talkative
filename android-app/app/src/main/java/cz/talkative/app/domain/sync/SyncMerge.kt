package cz.talkative.app.domain.sync

/** Výsledek sloučení místních a vzdálených dat. */
data class MergeResult(
    /** Výsledný stav (bez prošlých tombstonů), seřazený podle ID; stejný na obou stranách po dokončení synchronizace. */
    val merged: List<SyncItem>,
    /** Položky, které je třeba zapsat do místní databáze (nové nebo změněné oproti místní). */
    val changedLocally: List<SyncItem>,
    /** Místní položky, které se smažou úplně (prošlé tombstony). */
    val purgeIds: Set<String>,
    /** Vzdálená kopie se liší od [merged], je třeba ji přepsat. */
    val remoteOutdated: Boolean,
)

/** Co dělat s médii: nahrát, stáhnout, a co chybí na obou stranách. */
data class MediaPlan(val toUpload: Set<MediaRef>, val toDownload: Set<MediaRef>, val missing: Set<MediaRef>)

/** Soubor média na vzdáleném úložišti a kdy byl naposledy změněn. */
data class RemoteMedia(val ref: MediaRef, val modifiedMillis: Long)

/**
 * Čistá logika slučování (last write wins podle `updatedAt`). Je deterministická, komutativní (A+B = B+A)
 * a idempotentní (sloučení výsledku s některou stranou nic nemění).
 */
object SyncMerge {
    /** Tombstony starší než tato doba se po sloučení zahazují. Zařízení offline déle by smazanou položku vrátilo. */
    const val TOMBSTONE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** Nepoužité soubory na vzdáleném úložišti se mažou až po této době (soubor mohl nahrát jiné zařízení, které ještě nezapsalo odkaz). */
    const val REMOTE_MEDIA_MIN_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    /**
     * Vítěz dvou verzí téže položky: novější `updatedAt`. Při shodě vyhrává smazání (delete-vs-edit ve stejný okamžik),
     * pak menší obsahový klíč, aby oba konce vždy vybraly totéž.
     */
    fun winner(a: SyncItem, b: SyncItem): SyncItem = when {
        a.updatedAt != b.updatedAt -> if (a.updatedAt > b.updatedAt) a else b
        a.deleted != b.deleted -> if (a.deleted) a else b
        else -> if (tieKey(a) <= tieKey(b)) a else b
    }

    private fun tieKey(item: SyncItem) = listOf(
        item.kind, item.profileId, item.title, item.parentId, item.sortOrder, item.imageFile, item.alternateText, item.audioFile,
    ).joinToString("\u0000")

    fun merge(local: Collection<SyncItem>, remote: Collection<SyncItem>, nowMillis: Long): MergeResult {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val all = (localById.keys + remoteById.keys).sorted()
        val expiredBefore = nowMillis - TOMBSTONE_TTL_MILLIS

        val merged = mutableListOf<SyncItem>()
        val purgeIds = mutableSetOf<String>()
        for (id in all) {
            val l = localById[id]
            val r = remoteById[id]
            val chosen = when {
                l == null -> r!!
                r == null -> l
                else -> winner(l, r)
            }
            if (chosen.deleted && chosen.updatedAt < expiredBefore) {
                if (l != null) purgeIds += id
            } else {
                merged += chosen
            }
        }
        val mergedById = merged.associateBy { it.id }
        return MergeResult(
            merged = merged,
            changedLocally = merged.filter { localById[it.id] != it },
            purgeIds = purgeIds,
            remoteOutdated = mergedById != remoteById,
        )
    }

    /**
     * Plán médií: z [referenced] se nahrají ta, která jsou jen místně, a stáhnou ta, která jsou jen na vzdáleném
     * úložišti. Co chybí na obou stranách, nejde obnovit (nekonzistence, řeší krok 10).
     */
    fun planMedia(referenced: Set<MediaRef>, local: Set<MediaRef>, remote: Set<MediaRef>): MediaPlan = MediaPlan(
        toUpload = referenced.filter { it in local && it !in remote }.toSet(),
        toDownload = referenced.filter { it !in local && it in remote }.toSet(),
        missing = referenced.filter { it !in local && it !in remote }.toSet(),
    )

    /** Soubory na vzdáleném úložišti, které nikdo nepoužívá a jsou dost staré na smazání. */
    fun remoteGarbage(remote: Collection<RemoteMedia>, referenced: Set<MediaRef>, nowMillis: Long): List<MediaRef> =
        remote.filter { it.ref !in referenced && it.modifiedMillis < nowMillis - REMOTE_MEDIA_MIN_AGE_MILLIS }.map { it.ref }
}
