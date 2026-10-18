package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConfigUseCase @Inject constructor(
    private val repository: ConfigRepository,
) {
    operator fun invoke(): Flow<AppConfig> = repository.config
}
