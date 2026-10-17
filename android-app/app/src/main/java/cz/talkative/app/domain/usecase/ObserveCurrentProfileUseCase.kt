package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.Profile
import cz.talkative.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentProfileUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    operator fun invoke(): Flow<Profile> = repository.observeCurrentProfile()
}
