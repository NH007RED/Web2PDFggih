package com.nhdeveloper.web2pdf.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nhdeveloper.web2pdf.ui.screens.HistoryScreen
import com.nhdeveloper.web2pdf.ui.screens.HomeScreen
import com.nhdeveloper.web2pdf.ui.screens.PdfViewerScreen
import com.nhdeveloper.web2pdf.ui.screens.PreviewScreen
import com.nhdeveloper.web2pdf.ui.screens.SettingsScreen
import com.nhdeveloper.web2pdf.ui.screens.SuccessScreen
import com.nhdeveloper.web2pdf.ui.screens.UrlInputScreen
import com.nhdeveloper.web2pdf.viewmodel.ConvertViewModel
import com.nhdeveloper.web2pdf.viewmodel.HistoryViewModel
import com.nhdeveloper.web2pdf.viewmodel.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val INPUT = "input"
    const val PREVIEW = "preview"
    const val SUCCESS = "success/{id}"
    const val VIEWER = "viewer/{id}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun success(id: Long) = "success/$id"
    fun viewer(id: Long) = "viewer/$id"
}

@Composable
fun AppNavigation(
    convertVm: ConvertViewModel,
    historyVm: HistoryViewModel,
    settingsVm: SettingsViewModel
) {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(280)) { it / 6 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(240)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = convertVm,
                onConvert = { nav.navigate(Routes.PREVIEW) },
                onOpenInput = { nav.navigate(Routes.INPUT) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenRecord = { nav.navigate(Routes.viewer(it)) }
            )
        }
        composable(Routes.INPUT) {
            UrlInputScreen(
                vm = convertVm,
                onBack = { nav.popBackStack() },
                onContinue = { nav.navigate(Routes.PREVIEW) { popUpTo(Routes.INPUT) { inclusive = true } } }
            )
        }
        composable(Routes.PREVIEW) {
            PreviewScreen(
                vm = convertVm,
                onBack = { nav.popBackStack() },
                onSuccess = { id ->
                    nav.navigate(Routes.success(id)) { popUpTo(Routes.HOME) }
                }
            )
        }
        composable(Routes.SUCCESS, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            SuccessScreen(
                id = id,
                vm = historyVm,
                onPreview = { nav.navigate(Routes.viewer(id)) },
                onHome = { nav.popBackStack(Routes.HOME, false) }
            )
        }
        composable(Routes.VIEWER, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            PdfViewerScreen(id = id, vm = historyVm, onBack = { nav.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                vm = historyVm,
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate(Routes.viewer(it)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm = settingsVm, onBack = { nav.popBackStack() })
        }
    }
}
