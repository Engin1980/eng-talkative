package cz.talkative.app.di

import android.content.Context
import cz.talkative.app.data.sync.FileSyncMediaStore
import cz.talkative.app.data.sync.RoomSyncLocalStore
import cz.talkative.app.domain.sync.SyncLocalStore
import cz.talkative.app.domain.sync.SyncMediaStore
import cz.talkative.app.domain.sync.SyncStagingDir
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

    companion object {
        @Provides
        fun provideSyncStagingDir(@ApplicationContext context: Context) = SyncStagingDir(File(context.cacheDir, "sync"))
    }
}
