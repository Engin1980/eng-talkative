package cz.talkative.app.domain.sync

/**
 * Přístupový token k Drive (`drive.appdata`) pro vybraný účet. Tokeny jsou krátkodobé, proto se před každou
 * synchronizací žádá nový a při odmítnutí (401) se zažádá ještě jednou s [forceRefresh].
 */
interface AccessTokenProvider {
    /**
     * @throws SyncException `NOT_SIGNED_IN` (není účet), `NEEDS_CONSENT` (chybí souhlas, nutná obrazovka),
     *   `NO_NETWORK` (služby Google nejsou dosažitelné)
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String
}
