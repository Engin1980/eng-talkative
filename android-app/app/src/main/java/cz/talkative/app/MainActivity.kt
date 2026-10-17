package cz.talkative.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import cz.talkative.app.ui.navigation.TalkativeNavHost
import cz.talkative.app.ui.theme.TalkativeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Po každém startu (i po obnovení procesu) se otevře klientský režim, ne uložená navigace do administrace.
        super.onCreate(null)
        setContent {
            TalkativeTheme {
                // Aplikace vždy startuje v klientském režimu (startDestination v navigaci).
                TalkativeNavHost(navController = rememberNavController())
            }
        }
    }
}
