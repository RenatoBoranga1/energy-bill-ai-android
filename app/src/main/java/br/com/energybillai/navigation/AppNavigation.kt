package br.com.energybillai.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.energybillai.AppEntryViewModel
import br.com.energybillai.BuildConfig
import br.com.energybillai.core.designsystem.EnergyBillTheme
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.feature.analytics.AnalyticsScreen
import br.com.energybillai.feature.auth.LoginScreen
import br.com.energybillai.feature.auth.RegisterScreen
import br.com.energybillai.feature.dashboard.DashboardScreen
import br.com.energybillai.feature.detail.BillDetailScreen
import br.com.energybillai.feature.forecast.ForecastScreen
import br.com.energybillai.feature.history.HistoryScreen
import br.com.energybillai.feature.profile.ProfileScreen
import br.com.energybillai.feature.review.ReviewScreen
import br.com.energybillai.feature.upload.UploadScreen

sealed class AppRoute(val route: String) {
    data object Login : AppRoute("login")
    data object Register : AppRoute("register")
    data object Dashboard : AppRoute("dashboard")
    data object Upload : AppRoute("upload")
    data object History : AppRoute("history")
    data object Profile : AppRoute("profile")
    data object BillDetail : AppRoute("bill/{billId}") {
        fun create(billId: String): String = "bill/$billId"
    }
    data object Review : AppRoute("review/{billId}") {
        fun create(billId: String): String = "review/$billId"
    }
    data object Analytics : AppRoute("analytics/{billId}") {
        fun create(billId: String): String = "analytics/$billId"
    }
    data object Forecast : AppRoute("forecast/{billId}") {
        fun create(billId: String): String = "forecast/$billId"
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(AppRoute.Dashboard.route, "Início", Icons.Outlined.BarChart),
    BottomDestination(AppRoute.Upload.route, "Enviar", Icons.Outlined.AddCircleOutline),
    BottomDestination(AppRoute.History.route, "Histórico", Icons.Outlined.History),
    BottomDestination(AppRoute.Profile.route, "Perfil", Icons.Outlined.AccountCircle),
)

private fun NavHostController.navigateToTopLevel(route: String) {
    if (route == AppRoute.Dashboard.route) {
        val restored = popBackStack(AppRoute.Dashboard.route, inclusive = false)
        if (!restored || currentDestination?.route != AppRoute.Dashboard.route) {
            navigate(AppRoute.Dashboard.route) {
                popUpTo(graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
                restoreState = false
            }
        }
        return
    }

    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun EnergyBillRoot(
    viewModel: AppEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EnergyBillTheme {
        if (state.isLoading) {
            LoadingPane(
                title = "Preparando seu painel",
                message = "Validando sua sessão e conectando os dados com segurança.",
            )
        } else if (state.isAuthenticated) {
            AuthenticatedNavHost()
        } else {
            UnauthenticatedNavHost()
        }
    }
}

@Composable
private fun UnauthenticatedNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoute.Login.route) {
        composable(AppRoute.Login.route) {
            LoginScreen(onNavigateRegister = { navController.navigate(AppRoute.Register.route) })
        }
        composable(AppRoute.Register.route) {
            RegisterScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun AuthenticatedNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigateToTopLevel(item.route)
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Dashboard.route,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(AppRoute.Dashboard.route) {
                DashboardScreen(
                    onOpenHistory = { navController.navigate(AppRoute.History.route) },
                    onOpenUpload = { navController.navigate(AppRoute.Upload.route) },
                    onOpenBillDetail = { navController.navigate(AppRoute.BillDetail.create(it)) },
                    onOpenAnalytics = { navController.navigate(AppRoute.Analytics.create(it)) },
                    onOpenForecast = { navController.navigate(AppRoute.Forecast.create(it)) },
                )
            }
            composable(AppRoute.Upload.route) {
                UploadScreen(onOpenReview = { navController.navigate(AppRoute.Review.create(it)) })
            }
            composable(AppRoute.History.route) {
                HistoryScreen(onOpenBillDetail = { navController.navigate(AppRoute.BillDetail.create(it)) })
            }
            composable(AppRoute.Profile.route) {
                ProfileScreen()
            }
            composable(
                route = AppRoute.BillDetail.route,
                arguments = listOf(navArgument("billId") { type = NavType.StringType }),
            ) {
                BillDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenAnalytics = { navController.navigate(AppRoute.Analytics.create(it)) },
                    onOpenForecast = { navController.navigate(AppRoute.Forecast.create(it)) },
                )
            }
            composable(
                route = AppRoute.Review.route,
                arguments = listOf(navArgument("billId") { type = NavType.StringType }),
            ) {
                ReviewScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenConfirmedBill = { billId ->
                        navController.popBackStack()
                        navController.navigate(AppRoute.BillDetail.create(billId))
                    },
                )
            }
            composable(
                route = AppRoute.Analytics.route,
                arguments = listOf(navArgument("billId") { type = NavType.StringType }),
            ) {
                AnalyticsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenForecast = { navController.navigate(AppRoute.Forecast.create(it)) },
                )
            }
            composable(
                route = AppRoute.Forecast.route,
                arguments = listOf(navArgument("billId") { type = NavType.StringType }),
            ) {
                ForecastScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenAnalytics = { navController.navigate(AppRoute.Analytics.create(it)) },
                )
            }
        }
    }
}
