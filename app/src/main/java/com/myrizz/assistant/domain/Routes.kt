package com.myrizz.assistant.domain

/** Navigation destinations of the main app (also used by the overlay to deep-link into Settings). */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SETUP = "setup"
    const val HOME = "home"
    const val ASSISTANT = "assistant"
    const val SCREENSHOT = "screenshot"
    const val TONES = "tones"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val LANGUAGE = "language"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"

    /** Screens another app/component may open through MainActivity.EXTRA_ROUTE. Anything else is ignored. */
    val external: Set<String> = setOf(HOME, SETUP, ASSISTANT, SCREENSHOT, SETTINGS)
}
