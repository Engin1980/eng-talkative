package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.sanitized
import cz.talkative.app.domain.repository.ConfigRepository
import javax.inject.Inject

/** Změní konfiguraci a uloží ji v povolených mezích (viz [cz.talkative.app.domain.model.ConfigLimits]). */
class UpdateConfigUseCase @Inject constructor(
    private val repository: ConfigRepository,
) {
    suspend operator fun invoke(transform: (AppConfig) -> AppConfig) = repository.update { transform(it).sanitized() }
}
