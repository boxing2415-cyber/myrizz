package com.myrizz.assistant.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.container
import com.myrizz.assistant.domain.Routes
import com.myrizz.assistant.ui.screens.AboutScreen
import com.myrizz.assistant.ui.screens.AssistantScreen
import com.myrizz.assistant.ui.screens.FavoritesScreen
import com.myrizz.assistant.ui.screens.HistoryScreen
import com.myrizz.assistant.ui.screens.HomeScreen
import com.myrizz.assistant.ui.screens.LanguageScreen
import com.myrizz.assistant.ui.screens.OnboardingScreen
import com.myrizz.assistant.ui.screens.PrivacyScreen
import com.myrizz.assistant.ui.screens.ScreenshotScreen
import com.myrizz.assistant.ui.screens.SettingsScreen
import com.myrizz.assistant.ui.screens.SetupScreen
import com.myrizz.assistant.ui.screens.SplashScreen
import com.myrizz.assistant.ui.screens.TonesScreen
import java.io.File

@Composable
fun AppNav(controller: AssistantController, requestedRoute: MutableState<String?>) {
    val container = LocalContext.current.container
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val incoming by container.incoming.collectAsStateWithLifecycle()
    val ready = current != null && current != Routes.SPLASH && current != Routes.ONBOARDING

    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    // A screen requested by another component (e.g. "open Settings" in the floating panel).
    LaunchedEffect(requestedRoute.value, ready) {
        val route = requestedRoute.value
        if (ready && route != null) {
            requestedRoute.value = null
            if (route in Routes.external && route != current) go(route)
        }
    }

    // Text or a screenshot the user explicitly shared while the main app is the target.
    LaunchedEffect(incoming, ready) {
        val share = incoming
        if (ready && share != null) {
            container.consumeIncoming()
            when {
                share.text != null -> {
                    controller.clearShot()
                    controller.setInput(share.text)
                    go(Routes.ASSISTANT)
                }
                share.imagePath != null -> {
                    controller.onScreenshotFile(File(share.imagePath))
                    go(Routes.SCREENSHOT)
                }
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 12 } },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen {
                val next = if (container.settings.current.onboardingDone) Routes.HOME else Routes.ONBOARDING
                nav.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    container.settings.update { it.copy(onboardingDone = true) }
                    nav.navigate(Routes.SETUP) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                onBack = { backOrHome(nav, Routes.SETUP) },
                onDone = { backOrHome(nav, Routes.SETUP) },
                onOpenSettings = { go(Routes.SETTINGS) },
            )
        }
        composable(Routes.HOME) { HomeScreen(onNavigate = { go(it) }) }
        composable(Routes.ASSISTANT) {
            AssistantScreen(controller, onBack = { nav.popBackStack() }, onOpenSettings = { go(Routes.SETTINGS) })
        }
        composable(Routes.SCREENSHOT) {
            ScreenshotScreen(
                controller = controller,
                onBack = { nav.popBackStack() },
                onOpenSettings = { go(Routes.SETTINGS) },
                onGenerated = {
                    nav.navigate(Routes.ASSISTANT) {
                        popUpTo(Routes.SCREENSHOT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.TONES) { TonesScreen(controller, onBack = { nav.popBackStack() }) }
        composable(Routes.FAVORITES) { FavoritesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onNavigate = { go(it) }) }
        composable(Routes.LANGUAGE) { LanguageScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}

/** Goes back, or to Home when this screen is the first one (first run after onboarding). */
private fun backOrHome(nav: NavHostController, from: String) {
    if (nav.previousBackStackEntry != null) {
        nav.popBackStack()
    } else {
        nav.navigate(Routes.HOME) { popUpTo(from) { inclusive = true } }
    }
}
