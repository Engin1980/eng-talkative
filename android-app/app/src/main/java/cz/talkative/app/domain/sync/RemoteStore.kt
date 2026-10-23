package cz.talkative.app.domain.sync

import java.io.File

/** Proč synchronizace selhala. */
enum class SyncError {
    /** Není vybraný účet. */
    NOT_SIGNED_IN,

    /** Účet je vybraný, ale chybí souhlas s přístupem (nutné potvrdit na obrazovce). */
    NEEDS_CONSENT,

    /** Telefon není připojený nebo server není dosažitelný. */
    NO_NETWORK,

    /** Server odmítl přístupový token (vypršel nebo byl odvolán). */
    UNAUTHORIZED,

    /** Chyba na straně Drive (HTTP 5xx, překročená kvóta). */
    SERVER_ERROR,

    /** Odpověď nejde přečíst nebo má neplatný tvar. */
    BAD_RESPONSE,

    /** Data na Drive vytvořila novější verze aplikace, kterou tahle neumí číst. */
    NEWER_VERSION,

    /** Selhal zápis do místního úložiště (např. plný disk). */
    LOCAL_STORAGE,

    /** V zařízení nejsou služby Google Play (nebo jsou zastaralé), přihlášení k Drive není možné. */
    NO_PLAY_SERVICES,
}

class SyncException(val error: SyncError, message: String? = null, cause: Throwable? = null) : Exception(message ?: error.name, cause)

/**
 * Vzdálené úložiště synchronizace (Google Drive `appDataFolder`). Metadata jsou jeden JSON, média samostatné soubory
 * podle [MediaRef]. Všechny metody při chybě vyhazují [SyncException].
 */
interface RemoteStore {
    /** Obsah souboru s metadaty, nebo `null`, pokud ještě neexistuje. */
    suspend fun readMetadata(): String?

    /** Přepíše (nebo vytvoří) soubor s metadaty. */
    suspend fun writeMetadata(json: String)

    suspend fun listMedia(): List<RemoteMedia>

    suspend fun uploadMedia(ref: MediaRef, file: File)

    /** Stáhne médium do [target] (přepíše ho). */
    suspend fun downloadMedia(ref: MediaRef, target: File)

    suspend fun deleteMedia(ref: MediaRef)
}
