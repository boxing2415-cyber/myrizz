package com.myrizz.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.myrizz.assistant.domain.ReplyStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

object SecureStore {
    /**
     * Encrypted preferences (AES-256, key in Android Keystore). If the keystore is unavailable on a
     * device we fall back to private app storage rather than crash.
     */
    fun create(context: Context, name: String): SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            name,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences("${name}_fallback", Context.MODE_PRIVATE)
    }
}

class SettingsRepository(private val prefs: SharedPreferences, private val defaultBackendUrl: String) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val current: Settings get() = _state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit().putString(KEY, AppJson.encodeToString(Settings.serializer(), next)).apply()
    }

    /** Wipes every stored setting, including the access token and AI consent. */
    fun reset() {
        prefs.edit().clear().apply()
        _state.value = Settings(backendUrl = defaultBackendUrl, onboardingDone = true)
        update { it }
    }

    private fun load(): Settings {
        val stored = prefs.getString(KEY, null)?.let {
            runCatching { AppJson.decodeFromString(Settings.serializer(), it) }.getOrNull()
        }
        return stored ?: Settings(backendUrl = defaultBackendUrl)
    }

    private companion object {
        const val KEY = "settings_json"
    }
}

/** A small persisted list (favorites, history) in encrypted preferences. */
open class ListStore<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val maxItems: Int,
) {
    private val listSerializer = ListSerializer(serializer)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    protected fun mutate(transform: (List<T>) -> List<T>) {
        _items.update { transform(it).take(maxItems) }
        prefs.edit().putString(key, AppJson.encodeToString(listSerializer, _items.value)).apply()
    }

    fun clear() = mutate { emptyList() }

    private fun load(): List<T> =
        prefs.getString(key, null)?.let {
            runCatching { AppJson.decodeFromString(listSerializer, it) }.getOrNull()
        } ?: emptyList()
}

class FavoritesStore(prefs: SharedPreferences) :
    ListStore<Favorite>(prefs, "favorites", Favorite.serializer(), maxItems = 200) {

    fun isFavorite(text: String): Boolean = items.value.any { it.text == text }

    /** Adds the text, or removes it if it is already saved. Returns true when it is now a favorite. */
    fun toggle(text: String): Boolean {
        val exists = isFavorite(text)
        mutate { list ->
            if (exists) list.filterNot { it.text == text }
            else listOf(Favorite(UUID.randomUUID().toString(), text, System.currentTimeMillis())) + list
        }
        return !exists
    }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }
}

class HistoryStore(prefs: SharedPreferences, private val enabled: () -> Boolean) :
    ListStore<HistoryEntry>(prefs, "history", HistoryEntry.serializer(), maxItems = 100) {

    /** Records an entry ONLY if the user has opted in to history. The input is trimmed to 300 chars. */
    fun record(kind: String, style: ReplyStyle?, input: String, outputs: List<String>) {
        if (!enabled()) return
        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            kind = kind,
            style = style?.apiId,
            input = input.take(300),
            outputs = outputs,
        )
        mutate { listOf(entry) + it }
    }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }
}
