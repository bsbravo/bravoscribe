package com.bravoscribe.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bravoscribe.android.ui.navigation.BravoscribeNavHost
import com.bravoscribe.android.ui.navigation.Routes
import com.bravoscribe.android.ui.theme.BravoscribeTheme

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Today", Icons.Filled.Home),
    BottomNavItem(Routes.ENTRIES, "Entries", Icons.Filled.Book),
    BottomNavItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

@Composable
fun BravoscribeApp(
    authViewModel: AuthViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val isDark by themeViewModel.isDark.collectAsState()

    BravoscribeTheme(isDark = isDark) {
        when (val state = authState) {
            is AuthUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AuthUiState.LoggedIn, is AuthUiState.LoggedOut -> {
                val startDestination = if (state is AuthUiState.LoggedIn) Routes.HOME else Routes.LOGIN
                val sessionExpired = (state as? AuthUiState.LoggedOut)?.sessionExpired ?: false
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute in Routes.BOTTOM_NAV_ROUTES

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                bottomNavItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = backStackEntry?.destination?.hierarchy
                                            ?.any { it.route == item.route } == true,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        BravoscribeNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            sessionExpired = sessionExpired,
                            onAuthenticated = { user ->
                                authViewModel.onAuthenticated(user)
                                navController.navigate(Routes.HOME) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onLogout = {
                                authViewModel.logout()
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
