package com.myrizz.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myrizz.assistant.R
import com.myrizz.assistant.container
import com.myrizz.assistant.domain.UrlValidator
import com.myrizz.assistant.ui.components.GradientButton
import com.myrizz.assistant.ui.components.ScreenScaffold
import com.myrizz.assistant.ui.components.SectionLabel
import com.myrizz.assistant.ui.components.SoftCard
import com.myrizz.assistant.ui.rememberPermissionUi

/** Permissions and how-to. Replaces the old "enable / select keyboard" steps: no keyboard is installed. */
@Composable
fun SetupScreen(onBack: () -> Unit, onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val perms = rememberPermissionUi()
    val overlay by perms.overlayGranted
    val notifications by perms.notificationsGranted
    val settings by LocalContext.current.container.settings.state.collectAsStateWithLifecycle()
    val backendOk = UrlValidator.normalizeHttps(settings.backendUrl) != null

    ScreenScaffold(title = stringResource(R.string.setup_title), onBack = onBack) {
        StepCard(
            title = stringResource(R.string.setup_overlay_title),
            body = stringResource(R.string.setup_overlay_body),
            done = overlay,
            actionLabel = stringResource(R.string.setup_grant),
            doneLabel = stringResource(R.string.setup_granted),
            onAction = perms.requestOverlay,
            tag = "setup_overlay",
        )
        StepCard(
            title = stringResource(R.string.setup_notif_title),
            body = stringResource(R.string.setup_notif_body),
            done = notifications,
            actionLabel = stringResource(R.string.setup_grant),
            doneLabel = stringResource(R.string.setup_granted),
            onAction = perms.requestNotifications,
            tag = "setup_notifications",
        )
        StepCard(
            title = stringResource(R.string.setup_backend_title),
            body = stringResource(R.string.setup_backend_body),
            done = backendOk,
            actionLabel = stringResource(R.string.setup_configure),
            doneLabel = stringResource(R.string.setup_configured),
            onAction = onOpenSettings,
            tag = "setup_backend",
        )

        SectionLabel(stringResource(R.string.setup_howto_title))
        SoftCard(Modifier.fillMaxWidth()) {
            listOf(R.string.howto_1, R.string.howto_2, R.string.howto_3, R.string.howto_4).forEachIndexed { i, res ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Text(
            stringResource(R.string.setup_keyboard_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientButton(
            text = stringResource(R.string.action_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().testTag("setup_done"),
        )
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    doneLabel: String,
    onAction: () -> Unit,
    tag: String,
) {
    SoftCard(Modifier.fillMaxWidth().testTag(tag)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (done) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(doneLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
