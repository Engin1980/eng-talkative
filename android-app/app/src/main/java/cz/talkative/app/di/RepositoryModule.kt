package cz.talkative.app.di

import cz.talkative.app.data.repository.DataStoreConfigRepository
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.data.repository.RoomProfileRepository
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.repository.DictionaryRepository
import cz.talkative.app.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: RoomProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindDictionaryRepository(impl: RoomDictionaryRepository): DictionaryRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: DataStoreConfigRepository): ConfigRepository
}
