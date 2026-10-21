package com.myrizz.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Live permission state for the screens that enable the floating bubble. Re-checked on every resume. */
class PermissionUi(
    val overlayGranted: State<Boolean>,
    val notificationsGranted: State<Boolean>,
    val requestOverlay: () -> Unit,
    val requestNotifications: () -> Unit,
)

@Composable
fun rememberPermissionUi(): PermissionUi {
    val context = LocalContext.current
    val overlay = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val notifications = remember { mutableStateOf(hasNotificationPermission(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlay.value = Settings.canDrawOverlays(context)
        notifications.value = hasNotificationPermission(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications.value = granted
    }

    return PermissionUi(
        overlayGranted = overlay,
        notificationsGranted = notifications,
        requestOverlay = {
            // Opens the app-specific "Display over other apps" page in system settings.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
    )
}

fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
