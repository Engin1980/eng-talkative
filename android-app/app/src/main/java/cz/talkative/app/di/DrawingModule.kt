package cz.talkative.app.di

import cz.talkative.app.data.drawing.CacheDrawingStorage
import cz.talkative.app.domain.drawing.DrawingStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DrawingModule {

    @Binds
    abstract fun bindDrawingStorage(impl: CacheDrawingStorage): DrawingStorage
}
