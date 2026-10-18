package cz.talkative.app.di

import cz.talkative.app.data.speech.AndroidTtsSpeechProvider
import cz.talkative.app.domain.speech.SpeechProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {

    /**
     * Záměrně bez scope: každý odběratel dostane vlastní instanci, kterou sám uvolní
     * (ViewModel v `onCleared`). Singleton by TTS engine nikdy neuvolnil.
     */
    @Binds
    abstract fun bindSpeechProvider(impl: AndroidTtsSpeechProvider): SpeechProvider
}
