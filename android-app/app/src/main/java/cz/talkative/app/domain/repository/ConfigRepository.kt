package cz.talkative.app.domain.repository

import cz.talkative.app.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

/** Lokální konfigurace zařízení. Chybějící hodnoty se nahradí výchozími. */
interface ConfigRepository {
    val config: Flow<AppConfig>

    suspend fun update(transform: (AppConfig) -> AppConfig)
}
