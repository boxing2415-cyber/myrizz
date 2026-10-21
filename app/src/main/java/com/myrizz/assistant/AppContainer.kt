package com.myrizz.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.myrizz.assistant.data.AiClient
import com.myrizz.assistant.data.FavoritesStore
import com.myrizz.assistant.data.HistoryStore
import com.myrizz.assistant.data.SecureStore
import com.myrizz.assistant.data.SettingsRepository
import com.myrizz.assistant.ml.OcrEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Something the user explicitly handed to the app (share sheet), waiting to be shown. */
data class IncomingShare(val text: String? = null, val imagePath: String? = null)

/** Tiny manual dependency container (no DI framework needed for an app this size). */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    private val prefs by lazy { SecureStore.create(app, "myrizz_secure_prefs") }

    val settings by lazy { SettingsRepository(prefs, BuildConfig.DEFAULT_BACKEND_URL) }
    val favorites by lazy { FavoritesStore(prefs) }
    val history by lazy { HistoryStore(prefs) { settings.current.historyEnabled } }
    val ocr by lazy { OcrEngine() }

    val ai by lazy {
        AiClient(
            settings = { settings.current },
            isOnline = { isOnline() },
        )
    }

    private val _incoming = MutableStateFlow<IncomingShare?>(null)
    val incoming: StateFlow<IncomingShare?> = _incoming.asStateFlow()

    fun deliver(share: IncomingShare) {
        _incoming.value = share
    }

    fun consumeIncoming() {
        _incoming.value = null
    }

    val screenshotDir: File get() = File(app.cacheDir, "shots").apply { mkdirs() }

    /** Deletes any screenshot copies left in the cache (they are temporary working files). */
    fun purgeScreenshots() {
        screenshotDir.listFiles()?.forEach { it.delete() }
    }

    /** "Delete all local data": settings, token, consent, favorites, history, cached screenshots. */
    fun wipeAll() {
        history.clear()
        favorites.clear()
        purgeScreenshots()
        settings.reset()
    }

    private fun isOnline(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
