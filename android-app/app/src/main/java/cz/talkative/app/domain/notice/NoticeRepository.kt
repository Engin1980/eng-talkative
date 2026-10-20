package cz.talkative.app.domain.notice

import kotlinx.coroutines.flow.Flow

/**
 * Verze úvodního oznámení („O aplikaci“ s uvedením ARASAAC), které uživatel musí při prvním spuštění vzít na vědomí.
 * Při změně znění uvedení nebo licence se číslo zvýší a oznámení se ukáže znovu.
 */
const val NOTICE_VERSION = 1

/** Ukládá, kterou verzi oznámení uživatel vzal na vědomí. Je to lokální údaj zařízení, do exportu ani synchronizace nepatří. */
interface NoticeRepository {
    /** Poslední odsouhlasená verze, nebo `null`, když uživatel zatím nic neodsouhlasil. */
    val acknowledgedVersion: Flow<Int?>

    suspend fun acknowledge(version: Int)
}
