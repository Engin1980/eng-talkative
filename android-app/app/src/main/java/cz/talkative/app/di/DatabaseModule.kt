package cz.talkative.app.di

import android.content.Context
import androidx.room.Room
import cz.talkative.app.data.local.MetaDao
import cz.talkative.app.data.local.TalkativeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TalkativeDatabase =
        Room.databaseBuilder(context, TalkativeDatabase::class.java, TalkativeDatabase.NAME).build()

    @Provides
    fun provideMetaDao(database: TalkativeDatabase): MetaDao = database.metaDao()
}
