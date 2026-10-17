package cz.talkative.app.data.repository

import cz.talkative.app.data.local.MetaDao
import cz.talkative.app.data.local.MetaEntity
import cz.talkative.app.domain.model.Profile
import cz.talkative.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class RoomProfileRepository @Inject constructor(
    private val metaDao: MetaDao,
) : ProfileRepository {

    override fun observeCurrentProfile(): Flow<Profile> =
        metaDao.observeValue(MetaEntity.KEY_CURRENT_PROFILE)
            .onStart {
                metaDao.insertIfAbsent(MetaEntity(MetaEntity.KEY_CURRENT_PROFILE, Profile.DEFAULT_ID))
            }
            .map { value -> Profile(value ?: Profile.DEFAULT_ID) }
}
