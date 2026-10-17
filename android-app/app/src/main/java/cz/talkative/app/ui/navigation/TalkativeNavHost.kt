package cz.talkative.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cz.talkative.app.ui.admin.AdminRoute
import cz.talkative.app.ui.client.ClientRoute

object Routes {
    const val CLIENT = "client"
    const val ADMIN = "admin"
}

/**
 * Navigace mezi klientem a administrací. Klient je vždy startovní obrazovka.
 * Obrazovky jsou parametry, aby šly v testech nahradit bezstavovými verzemi (bez Hilt).
 */
@Composable
fun TalkativeNavHost(
    navController: NavHostController,
    client: @Composable (openAdmin: () -> Unit) -> Unit = { openAdmin -> ClientRoute(onOpenAdmin = openAdmin) },
    admin: @Composable (backToClient: () -> Unit) -> Unit = { backToClient -> AdminRoute(onBackToClient = backToClient) },
) {
    NavHost(navController = navController, startDestination = Routes.CLIENT) {
        composable(Routes.CLIENT) {
            client { navController.navigate(Routes.ADMIN) }
        }
        composable(Routes.ADMIN) {
            admin { navController.popBackStack(Routes.CLIENT, inclusive = false) }
        }
    }
}
