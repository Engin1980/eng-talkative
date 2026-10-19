package cz.talkative.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cz.talkative.app.ui.admin.AdminRoute
import cz.talkative.app.ui.client.ClientRoute
import cz.talkative.app.ui.config.ConfigRoute

object Routes {
    const val CLIENT = "client"
    const val ADMIN = "admin"
    const val CONFIG = "config"
}

/**
 * Navigace mezi klientem, administrací a konfigurací (ta se otevírá z administrace). Klient je vždy startovní obrazovka.
 * Obrazovky jsou parametry, aby šly v testech nahradit bezstavovými verzemi (bez Hilt).
 */
@Composable
fun TalkativeNavHost(
    navController: NavHostController,
    client: @Composable (openAdmin: () -> Unit) -> Unit = { openAdmin -> ClientRoute(onOpenAdmin = openAdmin) },
    admin: @Composable (backToClient: () -> Unit, openConfig: () -> Unit) -> Unit = { backToClient, openConfig ->
        AdminRoute(onBackToClient = backToClient, onOpenConfig = openConfig)
    },
    config: @Composable (back: () -> Unit) -> Unit = { back -> ConfigRoute(onBack = back) },
) {
    NavHost(navController = navController, startDestination = Routes.CLIENT) {
        composable(Routes.CLIENT) {
            client { navController.navigate(Routes.ADMIN) }
        }
        composable(Routes.ADMIN) {
            admin(
                { navController.popBackStack(Routes.CLIENT, inclusive = false) },
                { navController.navigate(Routes.CONFIG) },
            )
        }
        composable(Routes.CONFIG) {
            config { navController.popBackStack() }
        }
    }
}
