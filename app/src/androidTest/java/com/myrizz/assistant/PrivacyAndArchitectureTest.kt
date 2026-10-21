package com.myrizz.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myrizz.assistant.data.FavoritesStore
import com.myrizz.assistant.data.HistoryStore
import com.myrizz.assistant.data.SecureStore
import com.myrizz.assistant.domain.ReplyStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyAndArchitectureTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun appDoesNotRegisterAKeyboard() {
        val imes = ctx.packageManager.queryIntentServices(Intent("android.view.InputMethod"), PackageManager.GET_META_DATA)
        assertTrue(imes.none { it.serviceInfo.packageName == ctx.packageName })
    }

    @Test fun noAccessibilityServiceAndNoStoragePermissions() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        val perms = info.requestedPermissions.orEmpty().toSet()
        assertFalse(perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(perms.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(perms.contains(Manifest.permission.READ_CONTACTS))
        assertTrue(perms.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
    }

    @Test fun historyIsOffByDefaultAndFavoritesToggle() {
        val prefs = SecureStore.create(ctx, "test_prefs_${System.nanoTime()}")
        var enabled = false
        val history = HistoryStore(prefs) { enabled }
        history.record("reply", ReplyStyle.SMOOTH, "hello", listOf("a"))
        assertTrue(history.items.value.isEmpty())
        enabled = true
        history.record("reply", ReplyStyle.SMOOTH, "x".repeat(500), listOf("a"))
        assertEquals(1, history.items.value.size)
        assertEquals(300, history.items.value[0].input.length)
        history.clear()
        assertTrue(history.items.value.isEmpty())

        val fav = FavoritesStore(prefs)
        assertTrue(fav.toggle("hey"))
        assertTrue(fav.isFavorite("hey"))
        assertFalse(fav.toggle("hey"))
    }
}
