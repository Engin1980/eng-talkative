package cz.talkative.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cz.talkative.app.ui.about.AboutRoute
import cz.talkative.app.ui.admin.AdminRoute
import cz.talkative.app.ui.client.ClientRoute
import cz.talkative.app.ui.config.ConfigRoute
import cz.talkative.app.ui.exchange.ExchangeRoute

object Routes {
    const val CLIENT = "client"
    const val ADMIN = "admin"
    const val CONFIG = "config"
    const val EXCHANGE = "exchange"
    const val ABOUT = "about"
}

/**
 * Navigace mezi klientem, administrací a konfigurací, exportem a importem i „O aplikaci“ (ty se otevírají z administrace). Klient je vždy startovní obrazovka.
 * Obrazovky jsou parametry, aby šly v testech nahradit bezstavovými verzemi (bez Hilt).
 */
@Composable
fun TalkativeNavHost(
    navController: NavHostController,
    client: @Composable (openAdmin: () -> Unit) -> Unit = { openAdmin -> ClientRoute(onOpenAdmin = openAdmin) },
    admin: @Composable (backToClient: () -> Unit, openConfig: () -> Unit, openExchange: () -> Unit, openAbout: () -> Unit) -> Unit =
        { backToClient, openConfig, openExchange, openAbout ->
            AdminRoute(
                onBackToClient = backToClient,
                onOpenConfig = openConfig,
                onOpenExchange = openExchange,
                onOpenAbout = openAbout,
            )
        },
    config: @Composable (back: () -> Unit) -> Unit = { back -> ConfigRoute(onBack = back) },
    exchange: @Composable (back: () -> Unit) -> Unit = { back -> ExchangeRoute(onBack = back) },
    about: @Composable (back: () -> Unit) -> Unit = { back -> AboutRoute(onBack = back) },
) {
    NavHost(navController = navController, startDestination = Routes.CLIENT) {
        composable(Routes.CLIENT) {
            client { navController.navigate(Routes.ADMIN) }
        }
        composable(Routes.ADMIN) {
            admin(
                { navController.popBackStack(Routes.CLIENT, inclusive = false) },
                { navController.navigate(Routes.CONFIG) },
                { navController.navigate(Routes.EXCHANGE) },
                { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.CONFIG) {
            config { navController.popBackStack() }
        }
        composable(Routes.EXCHANGE) {
            exchange { navController.popBackStack() }
        }
        composable(Routes.ABOUT) {
            about { navController.popBackStack() }
        }
    }
}
