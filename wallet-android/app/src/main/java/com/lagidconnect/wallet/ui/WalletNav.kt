package com.lagidconnect.wallet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val WELCOME = "welcome"
    const val CODE = "code"
    const val FACE = "face"
    const val HOME = "home"
    const val REQUEST = "request/{id}"
    const val ACTIVITY = "activity"
    const val GRANTS = "grants"
    const val PASS = "pass"
    fun request(id: String) = "request/$id"
}

@Composable
fun WalletNav(vm: WalletViewModel = viewModel()) {
    val nav = rememberNavController()
    val signedOut by vm.signedOut.collectAsStateWithLifecycle()

    // Server no longer knows this phone: start over
    LaunchedEffect(signedOut) {
        if (signedOut) nav.goFresh(Routes.WELCOME)
    }

    NavHost(
        navController = nav,
        startDestination = if (vm.isLinked) Routes.HOME else Routes.WELCOME,
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(vm, onCodeSent = { nav.navigate(Routes.CODE) })
        }
        composable(Routes.CODE) {
            CodeScreen(
                vm,
                onBack = { nav.popBackStack() },
                onNext = { nav.navigate(Routes.FACE) },
            )
        }
        composable(Routes.FACE) {
            FaceScreen(
                vm,
                onBack = { nav.popBackStack() },
                onLinked = { nav.goFresh(Routes.HOME) },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                vm,
                onOpenRequest = { nav.navigate(Routes.request(it)) },
                onOpenActivity = { nav.navigate(Routes.ACTIVITY) },
                onOpenGrants = { nav.navigate(Routes.GRANTS) },
                onOpenPass = { nav.navigate(Routes.PASS) },
            )
        }
        composable(
            Routes.REQUEST,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            RequestScreen(
                vm,
                requestId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.ACTIVITY) {
            ActivityScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.GRANTS) {
            GrantsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.PASS) {
            PassScreen(
                vm,
                onBack = { nav.popBackStack() },
                // Relinking enrols the face: start over
                onRelink = { vm.signOut() },
            )
        }
    }
}

/** Navigate and clear everything behind. */
private fun NavHostController.goFresh(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
