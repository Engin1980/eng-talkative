package cz.talkative.app.di

import android.content.Context
import cz.talkative.app.data.pictogram.ArasaacPictogramSearch
import cz.talkative.app.data.pictogram.OkHttpPictogramDownloader
import cz.talkative.app.domain.pictogram.PictogramDownloader
import cz.talkative.app.domain.pictogram.PictogramSearch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PictogramModule {

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = ArasaacPictogramSearch.defaultClient()

    @Provides
    fun providePictogramSearch(client: OkHttpClient): PictogramSearch = ArasaacPictogramSearch(client)

    @Provides
    fun providePictogramDownloader(client: OkHttpClient, @ApplicationContext context: Context): PictogramDownloader =
        OkHttpPictogramDownloader(client, context.cacheDir)
}
