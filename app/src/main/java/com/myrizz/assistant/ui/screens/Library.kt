package com.myrizz.assistant.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myrizz.assistant.ClipboardHelper
import com.myrizz.assistant.R
import com.myrizz.assistant.container
import com.myrizz.assistant.data.HistoryEntry
import com.myrizz.assistant.domain.ReplyStyle
import com.myrizz.assistant.domain.ToolType
import com.myrizz.assistant.ui.components.GradientButton
import com.myrizz.assistant.ui.components.ScreenScaffold
import com.myrizz.assistant.ui.components.SoftCard
import com.myrizz.assistant.ui.components.SwitchRow

@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val favorites by context.container.favorites.items.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.fav_title), onBack = onBack) {
        if (favorites.isEmpty()) {
            SoftCard(Modifier.fillMaxWidth().testTag("fav_empty")) {
                Text(stringResource(R.string.fav_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            favorites.forEach { fav ->
                SoftCard(Modifier.fillMaxWidth().testTag("fav_item")) {
                    Text(
                        fav.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { ClipboardHelper.copy(context, fav.text) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { ClipboardHelper.copy(context, fav.text) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                        }
                        IconButton(onClick = { context.container.favorites.remove(fav.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { context.container.favorites.clear() },
                modifier = Modifier.fillMaxWidth().testTag("fav_clear_all"),
            ) { Text(stringResource(R.string.fav_clear_all)) }
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val entries by container.history.items.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.hist_title), onBack = onBack) {
        if (!settings.historyEnabled) {
            SoftCard(Modifier.fillMaxWidth().testTag("hist_off")) {
                Text(stringResource(R.string.hist_off_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.hist_off_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GradientButton(
                    text = stringResource(R.string.hist_enable),
                    onClick = { container.settings.update { it.copy(historyEnabled = true) } },
                    modifier = Modifier.fillMaxWidth().testTag("hist_enable"),
                )
            }
        } else {
            SwitchRow(
                title = stringResource(R.string.hist_on_switch),
                subtitle = null,
                checked = true,
                onCheckedChange = { container.settings.update { s -> s.copy(historyEnabled = false) } },
            )
            if (entries.isEmpty()) {
                Text(stringResource(R.string.hist_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEach { entry -> HistoryItem(entry) { container.history.remove(entry.id) } }
                OutlinedButton(
                    onClick = { container.history.clear() },
                    modifier = Modifier.fillMaxWidth().testTag("hist_clear"),
                ) { Text(stringResource(R.string.hist_clear)) }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry, onDelete: () -> Unit) {
    val context = LocalContext.current
    val kindLabel = if (entry.kind == "reply") {
        stringResource(R.string.hist_kind_reply)
    } else {
        ToolType.entries.firstOrNull { it.apiId == entry.kind }?.let { stringResource(it.labelRes) } ?: entry.kind
    }
    val styleLabel = ReplyStyle.entries.firstOrNull { it.apiId == entry.style }?.let { stringResource(it.labelRes) }

    SoftCard(Modifier.fillMaxWidth().testTag("hist_item")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(kindLabel, styleLabel).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.createdAt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
        Text(
            entry.input,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.outputs.forEach { out ->
                Text(
                    out,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { ClipboardHelper.copy(context, out) },
                )
            }
        }
    }
}
