package cz.talkative.app.domain.repository

import cz.talkative.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** Aktuálně používaný profil; při prvním čtení se vytvoří výchozí. */
    fun observeCurrentProfile(): Flow<Profile>
}
