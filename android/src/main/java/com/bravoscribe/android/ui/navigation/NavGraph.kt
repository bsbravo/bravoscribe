package com.bravoscribe.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.ui.auth.forgotpassword.ForgotPasswordScreen
import com.bravoscribe.android.ui.auth.login.LoginScreen
import com.bravoscribe.android.ui.auth.register.RegisterScreen
import com.bravoscribe.android.ui.auth.resetpassword.ResetPasswordScreen
import com.bravoscribe.android.ui.editor.EditorScreen
import com.bravoscribe.android.ui.entries.detail.EntryDetailScreen
import com.bravoscribe.android.ui.entries.list.EntriesListScreen
import com.bravoscribe.android.ui.home.HomeScreen
import com.bravoscribe.android.ui.profile.ProfileScreen

@Composable
fun BravoscribeNavHost(
    navController: NavHostController,
    startDestination: String,
    sessionExpired: Boolean,
    onAuthenticated: (User) -> Unit,
    onLogout: () -> Unit,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                sessionExpired = sessionExpired,
                onLoginSuccess = onAuthenticated,
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = onAuthenticated,
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onNavigateToLogin = { navController.popBackStack() })
        }
        composable(
            route = Routes.RESET_PASSWORD,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "bravoscribe://reset-password/{token}" }),
        ) {
            ResetPasswordScreen(
                onResetSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen()
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) {
            EditorScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDate = { date -> navController.navigate(Routes.editor(date)) },
            )
        }

        composable(Routes.ENTRIES) {
            EntriesListScreen(onEntryClick = { id -> navController.navigate(Routes.entryDetail(id)) })
        }
        composable(
            route = Routes.ENTRY_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            EntryDetailScreen(
                entryId = entryId,
                onEdit = { date -> navController.navigate(Routes.editor(date)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(onLogout = onLogout)
        }
    }
}
