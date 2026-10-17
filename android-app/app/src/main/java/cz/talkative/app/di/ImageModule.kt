package cz.talkative.app.di

import cz.talkative.app.data.image.FileImageStore
import cz.talkative.app.domain.image.ImageStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {

    @Binds
    @Singleton
    abstract fun bindImageStore(impl: FileImageStore): ImageStore
}
