package cz.talkative.app.di

import android.content.Context
import androidx.room.Room
import cz.talkative.app.data.local.DictionaryDao
import cz.talkative.app.data.local.MIGRATION_1_2
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
        Room.databaseBuilder(context, TalkativeDatabase::class.java, TalkativeDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideMetaDao(database: TalkativeDatabase): MetaDao = database.metaDao()

    @Provides
    fun provideDictionaryDao(database: TalkativeDatabase): DictionaryDao = database.dictionaryDao()
}
