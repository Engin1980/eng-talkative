package cz.talkative.app.testing

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Konfigurace v paměti; test ji může měnit přes [update] nebo číst z [current]. */
class FakeConfigRepository(initial: AppConfig = AppConfig()) : ConfigRepository {
    private val state = MutableStateFlow(initial)

    val current: AppConfig get() = state.value

    override val config: Flow<AppConfig> = state

    override suspend fun update(transform: (AppConfig) -> AppConfig) {
        state.value = transform(state.value)
    }
}
