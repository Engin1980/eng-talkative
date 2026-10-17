package cz.talkative.app.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.speech.AndroidTtsSpeechProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kontrola chování nad skutečným TTS enginem zařízení (životní cyklus, žádný zásek, žádný pád).
 * Zda se věta opravdu ozve, test ověřit neumí – to je ruční kontrola na zařízení.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTtsSpeechProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun speakIsReportedImmediately_stopReturnsToIdle_andShutdownIsFinal() {
        val provider = AndroidTtsSpeechProvider(context)

        provider.speak("Ahoj")
        assertTrue(provider.state.value.isSpeaking)

        provider.stop()
        assertFalse(provider.state.value.isSpeaking)

        provider.shutdown()
        provider.speak("po uvolnění")
        assertFalse(provider.state.value.isSpeaking)
        provider.shutdown() // opakované uvolnění nevadí
    }

    @Test
    fun speech_alwaysFinishes_withCleanEndOrAKnownError(): Unit = runBlocking {
        val provider = AndroidTtsSpeechProvider(context)
        try {
            provider.speak("Ahoj, toto je zkouška.")

            // Selhání testu = timeout, tj. řeč zůstala viset. Výsledek (dohrálo / chybí engine či český hlas) se jen loguje.
            val finished = withTimeout(30_000) { provider.state.first { !it.isSpeaking } }

            Log.i("TtsTest", "TTS skončilo, chyba: ${finished.error}")
            Unit
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun errorCanBeCleared(): Unit = runBlocking {
        val provider = AndroidTtsSpeechProvider(context)
        try {
            provider.speak("Zkouška.")
            withTimeout(30_000) { provider.state.first { !it.isSpeaking } }

            provider.clearError()

            assertEquals(null, provider.state.value.error)
        } finally {
            provider.shutdown()
        }
    }
}
