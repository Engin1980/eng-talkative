package cz.talkative.app.di

import cz.talkative.app.data.pictogram.ArasaacPictogramSearch
import cz.talkative.app.domain.pictogram.PictogramSearch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}
