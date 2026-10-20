package cz.talkative.app.di

import cz.talkative.app.data.exchange.ContentUriArchiveSourceFactory
import cz.talkative.app.data.exchange.ContentUriExportDestinationFactory
import cz.talkative.app.data.exchange.ResourceArchiveNotice
import cz.talkative.app.domain.exchange.ArchiveNotice
import cz.talkative.app.domain.exchange.ArchiveSourceFactory
import cz.talkative.app.domain.exchange.ExportDestinationFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExchangeModule {

    @Binds
    abstract fun bindExportDestinationFactory(impl: ContentUriExportDestinationFactory): ExportDestinationFactory

    @Binds
    abstract fun bindArchiveSourceFactory(impl: ContentUriArchiveSourceFactory): ArchiveSourceFactory

    @Binds
    abstract fun bindArchiveNotice(impl: ResourceArchiveNotice): ArchiveNotice
}
