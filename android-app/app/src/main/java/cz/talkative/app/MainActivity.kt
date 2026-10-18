package cz.talkative.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import cz.talkative.app.domain.image.ImageStore
import cz.talkative.app.ui.common.LocalImageResolver
import cz.talkative.app.ui.navigation.TalkativeNavHost
import cz.talkative.app.ui.theme.TalkativeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageStore: ImageStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Po každém startu (i po obnovení procesu) se otevře klientský režim, ne uložená navigace do administrace.
        super.onCreate(null)
        setContent {
            TalkativeTheme {
                CompositionLocalProvider(LocalImageResolver provides { imageStore.resolve(it) }) {
                    // Aplikace vždy startuje v klientském režimu (startDestination v navigaci).
                    TalkativeNavHost(navController = rememberNavController())
                }
            }
        }
    }
}
