package cz.talkative.app.domain.sync

import java.io.File

/** Místní databáze slovníku z pohledu synchronizace. Zapisuje se vedle běžného repozitáře, takže zápis nic nespouští a nemění `updatedAt`. */
interface SyncLocalStore {
    /** Všechny položky profilu včetně smazaných (tombstonů). */
    suspend fun readAll(): List<SyncItem>

    /** Zapíše [changed] přesně tak, jak jsou, a úplně smaže [purgeIds]; vše v jedné transakci. */
    suspend fun apply(changed: List<SyncItem>, purgeIds: Set<String>)

    /** Smaže soubory médií, na které už žádné slovíčko neodkazuje. */
    suspend fun cleanUpOrphanFiles()
}

/** Soubory médií v místních úložištích obrázků a zvuků. */
interface SyncMediaStore {
    /** Soubor média, nebo `null`, pokud neexistuje. */
    fun file(ref: MediaRef): File?

    /** Uloží [source] pod přesným názvem [ref] (název se nemění, na něj odkazuje databáze). Vrací `false`, když zápis selže. */
    suspend fun restore(ref: MediaRef, source: File): Boolean

    suspend fun delete(ref: MediaRef)
}
