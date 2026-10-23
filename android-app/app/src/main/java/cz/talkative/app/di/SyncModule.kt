package cz.talkative.app.di

import android.content.Context
import cz.talkative.app.data.repository.DataStoreSyncStateRepository
import cz.talkative.app.data.sync.DriveRemoteStore
import cz.talkative.app.data.sync.FileSyncMediaStore
import cz.talkative.app.data.sync.GoogleAuthorizer
import cz.talkative.app.data.sync.RoomSyncLocalStore
import cz.talkative.app.domain.sync.AccessTokenProvider
import cz.talkative.app.domain.sync.RemoteStore
import cz.talkative.app.domain.sync.SyncAuthorizer
import cz.talkative.app.domain.sync.SyncLocalStore
import cz.talkative.app.domain.sync.SyncMediaStore
import cz.talkative.app.domain.sync.SyncStagingDir
import cz.talkative.app.domain.sync.SyncStateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun bindSyncLocalStore(impl: RoomSyncLocalStore): SyncLocalStore

    @Binds
    abstract fun bindSyncMediaStore(impl: FileSyncMediaStore): SyncMediaStore

    @Binds
    abstract fun bindSyncStateRepository(impl: DataStoreSyncStateRepository): SyncStateRepository

    @Binds
    abstract fun bindSyncAuthorizer(impl: GoogleAuthorizer): SyncAuthorizer

    @Binds
    abstract fun bindAccessTokenProvider(impl: GoogleAuthorizer): AccessTokenProvider

    companion object {
        @Provides
        fun provideRemoteStore(tokens: AccessTokenProvider): RemoteStore = DriveRemoteStore(DriveRemoteStore.defaultClient(), tokens)

        @Provides
        fun provideSyncStagingDir(@ApplicationContext context: Context) = SyncStagingDir(File(context.cacheDir, "sync"))
    }
}
