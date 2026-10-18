package cz.talkative.app.di

import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.data.audio.MediaAudioPlayer
import cz.talkative.app.domain.audio.AudioPlayer
import cz.talkative.app.domain.audio.AudioStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioStore(impl: FileAudioStore): AudioStore

    /** Přehrávač nemá stav, každé přehrání je nezávislé. */
    @Binds
    abstract fun bindAudioPlayer(impl: MediaAudioPlayer): AudioPlayer
}
