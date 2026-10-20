package cz.talkative.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cz.talkative.app.ui.about.AboutRoute
import cz.talkative.app.ui.admin.AdminRoute
import cz.talkative.app.ui.arasaac.ARASAAC_QUERY_ARG
import cz.talkative.app.ui.arasaac.ArasaacRoute
import cz.talkative.app.ui.client.ClientRoute
import cz.talkative.app.ui.config.ConfigRoute
import cz.talkative.app.ui.exchange.ExchangeRoute

object Routes {
    const val CLIENT = "client"
    const val ADMIN = "admin"
    const val CONFIG = "config"
    const val EXCHANGE = "exchange"
    const val ABOUT = "about"
    const val ARASAAC = "arasaac?$ARASAAC_QUERY_ARG={$ARASAAC_QUERY_ARG}"

    /** Trasa výběru piktogramu ARASAAC s předvyplněným hledaným slovem. */
    fun arasaac(query: String) = "arasaac?$ARASAAC_QUERY_ARG=${Uri.encode(query)}"

    /** Klíč v `savedStateHandle` obrazovky, na kterou se výběr vrací: URI vybraného obrázku (soubor v cache). */
    const val PICKED_IMAGE_KEY = "pickedImageUri"
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
    arasaac: @Composable (onPicked: (String) -> Unit, openAbout: () -> Unit, back: () -> Unit) -> Unit =
        { onPicked, openAbout, back -> ArasaacRoute(onPicked = onPicked, onOpenAbout = openAbout, onBack = back) },
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
        composable(
            route = Routes.ARASAAC,
            arguments = listOf(navArgument(ARASAAC_QUERY_ARG) { type = NavType.StringType; defaultValue = "" }),
        ) {
            arasaac(
                { uri ->
                    // Výsledek se vrací předchozí obrazovce (editaci slovíčka v administraci) a výběr se zavře.
                    navController.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED_IMAGE_KEY, uri)
                    navController.popBackStack()
                },
                { navController.navigate(Routes.ABOUT) },
                { navController.popBackStack() },
            )
        }
    }
}
