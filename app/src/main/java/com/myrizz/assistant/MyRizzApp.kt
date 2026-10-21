package com.myrizz.assistant

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myrizz.assistant.domain.UiLanguage
import java.util.Locale

class MyRizzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Screenshot copies are temporary: remove anything left from a previous run.
        container.purgeScreenshots()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as MyRizzApp).container

/** Holds one [AssistantController] for an Activity so it survives rotation. */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    val container: AppContainer = (app as MyRizzApp).container
    val controller = AssistantController(viewModelScope, container)

    override fun onCleared() {
        controller.release()
    }
}

object LocaleHelper {
    /** Applies the in-app language to Activities (AppCompat handles persistence and recreation). */
    fun apply(language: UiLanguage) {
        val list = if (language == UiLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }

    /** For non-Activity UI (the floating overlay service) that must follow the in-app language. */
    fun wrap(base: Context, language: UiLanguage): Context {
        if (language == UiLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        val locale = Locale.forLanguageTag(language.tag)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
