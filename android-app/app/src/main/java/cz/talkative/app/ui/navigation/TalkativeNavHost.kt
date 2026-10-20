package cz.talkative.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import cz.talkative.app.ui.draw.DrawRoute
import cz.talkative.app.ui.exchange.ExchangeRoute

object Routes {
    const val CLIENT = "client"
    const val ADMIN = "admin"
    const val CONFIG = "config"
    const val EXCHANGE = "exchange"
    const val ABOUT = "about"
    const val DRAW = "draw"
    const val ARASAAC = "arasaac?$ARASAAC_QUERY_ARG={$ARASAAC_QUERY_ARG}"

    /** Trasa výběru piktogramu ARASAAC s předvyplněným hledaným slovem. */
    fun arasaac(query: String) = "arasaac?$ARASAAC_QUERY_ARG=${Uri.encode(query)}"

    /** Klíč v `savedStateHandle` obrazovky, na kterou se výběr vrací: URI vybraného obrázku (soubor v cache). */
    const val PICKED_IMAGE_KEY = "pickedImageUri"
}

/** Přechody z administrace na další obrazovky a obrázek, který se z nich vrací (viz [Routes.PICKED_IMAGE_KEY]). */
class AdminNavigation(
    val backToClient: () -> Unit,
    val openConfig: () -> Unit,
    val openExchange: () -> Unit,
    val openAbout: () -> Unit,
    val openArasaac: (query: String) -> Unit,
    val openDraw: () -> Unit,
    val pickedImageUri: String?,
    val onPickedImageConsumed: () -> Unit,
)

/**
 * Navigace mezi klientem, administrací a konfigurací, exportem a importem i „O aplikaci“ (ty se otevírají z administrace). Klient je vždy startovní obrazovka.
 * Obrazovky jsou parametry, aby šly v testech nahradit bezstavovými verzemi (bez Hilt).
 */
@Composable
fun TalkativeNavHost(
    navController: NavHostController,
    client: @Composable (openAdmin: () -> Unit) -> Unit = { openAdmin -> ClientRoute(onOpenAdmin = openAdmin) },
    admin: @Composable (AdminNavigation) -> Unit = { navigation ->
        AdminRoute(
            onBackToClient = navigation.backToClient,
            onOpenConfig = navigation.openConfig,
            onOpenExchange = navigation.openExchange,
            onOpenAbout = navigation.openAbout,
            onOpenArasaac = navigation.openArasaac,
            onOpenDraw = navigation.openDraw,
            pickedImageUri = navigation.pickedImageUri,
            onPickedImageConsumed = navigation.onPickedImageConsumed,
        )
    },
    config: @Composable (back: () -> Unit) -> Unit = { back -> ConfigRoute(onBack = back) },
    exchange: @Composable (back: () -> Unit) -> Unit = { back -> ExchangeRoute(onBack = back) },
    about: @Composable (back: () -> Unit) -> Unit = { back -> AboutRoute(onBack = back) },
    draw: @Composable (onSaved: (String) -> Unit, close: () -> Unit) -> Unit =
        { onSaved, close -> DrawRoute(onSaved = onSaved, onClose = close) },
    arasaac: @Composable (onPicked: (String) -> Unit, openAbout: () -> Unit, back: () -> Unit) -> Unit =
        { onPicked, openAbout, back -> ArasaacRoute(onPicked = onPicked, onOpenAbout = openAbout, onBack = back) },
) {
    NavHost(navController = navController, startDestination = Routes.CLIENT) {
        composable(Routes.CLIENT) {
            client { navController.navigate(Routes.ADMIN) }
        }
        composable(Routes.ADMIN) { entry ->
            // Obrázek vybraný na jiné obrazovce se vrací přes savedStateHandle této obrazovky.
            val picked by entry.savedStateHandle.getStateFlow<String?>(Routes.PICKED_IMAGE_KEY, null).collectAsState()
            admin(
                AdminNavigation(
                    backToClient = { navController.popBackStack(Routes.CLIENT, inclusive = false) },
                    openConfig = { navController.navigate(Routes.CONFIG) },
                    openExchange = { navController.navigate(Routes.EXCHANGE) },
                    openAbout = { navController.navigate(Routes.ABOUT) },
                    openArasaac = { query -> navController.navigate(Routes.arasaac(query)) },
                    openDraw = { navController.navigate(Routes.DRAW) },
                    pickedImageUri = picked,
                    onPickedImageConsumed = { entry.savedStateHandle[Routes.PICKED_IMAGE_KEY] = null },
                ),
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
        composable(Routes.DRAW) {
            draw(
                { uri ->
                    // Hotová kresba se vrací předchozí obrazovce (editaci slovíčka v administraci) a kreslení se zavře.
                    navController.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED_IMAGE_KEY, uri)
                    navController.popBackStack()
                },
                { navController.popBackStack() },
            )
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
