@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myrizz.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.AssistantState
import com.myrizz.assistant.R
import com.myrizz.assistant.UiError
import com.myrizz.assistant.domain.ReplyLanguage
import com.myrizz.assistant.domain.ReplyStyle
import com.myrizz.assistant.ui.theme.Brand

@Composable
fun BrandMark(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brand.gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    val fill = if (enabled) Brand.gradient else SolidColor(MaterialTheme.colorScheme.surfaceVariant)
    val contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(fill)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = contentColor, strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Text(text, color = contentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Standard screen: transparent top bar with optional back arrow and a scrolling, edge-to-edge body. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun StyleChips(selected: ReplyStyle, onSelect: (ReplyStyle) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReplyStyle.entries.forEach { style ->
            FilterChip(
                selected = style == selected,
                onClick = { onSelect(style) },
                label = { Text(stringResource(style.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.testTag("style_${style.apiId}"),
            )
        }
    }
}

@Composable
fun ReplyLanguageChips(selected: ReplyLanguage, onSelect: (ReplyLanguage) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReplyLanguage.entries.forEach { lang ->
            FilterChip(
                selected = lang == selected,
                onClick = { onSelect(lang) },
                label = { Text(stringResource(lang.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.gradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun errorText(error: UiError): String = when (error) {
    UiError.EmptyInput -> stringResource(R.string.err_empty)
    UiError.TooLong -> stringResource(R.string.err_too_long)
    UiError.Sensitive -> stringResource(R.string.err_sensitive)
    UiError.NotConfigured -> stringResource(R.string.err_not_configured)
    UiError.Offline -> stringResource(R.string.err_offline)
    UiError.Timeout -> stringResource(R.string.err_timeout)
    UiError.RateLimited -> stringResource(R.string.err_rate_limited)
    UiError.Unauthorized -> stringResource(R.string.err_unauthorized)
    UiError.ServerNotConfigured -> stringResource(R.string.err_server_not_configured)
    UiError.BadResponse -> stringResource(R.string.err_bad_response)
    UiError.NoTextFound -> stringResource(R.string.err_no_text)
    UiError.OcrFailed -> stringResource(R.string.err_ocr_failed)
    is UiError.Http -> stringResource(R.string.err_http, error.code)
}

@Composable
fun ErrorBanner(
    error: UiError,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    settingsLabel: String? = null,
) {
    val needsSettings = error is UiError.NotConfigured ||
        error is UiError.Unauthorized ||
        error is UiError.ServerNotConfigured
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_banner"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(errorText(error), color = MaterialTheme.colorScheme.onErrorContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (needsSettings && onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) {
                        Text(settingsLabel ?: stringResource(R.string.action_open_settings))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
}

/** Inline consent request (no dialog, so it also works inside the floating overlay window). */
@Composable
fun ConsentCard(onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth().testTag("consent_card")) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.consent_body), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientButton(stringResource(R.string.consent_accept), onAccept, Modifier.weight(1f))
            TextButton(onClick = onDecline) { Text(stringResource(R.string.consent_decline)) }
        }
    }
}

/** Consent request and error banner for the assistant controller. */
@Composable
fun StatusArea(
    state: AssistantState,
    controller: AssistantController,
    onOpenSettings: (() -> Unit)?,
    settingsLabel: String? = null,
) {
    if (state.consentNeeded) {
        ConsentCard(onAccept = controller::acceptConsent, onDecline = controller::declineConsent)
    }
    val error = state.error
    if (error != null) {
        ErrorBanner(error, onDismiss = controller::clearError, onOpenSettings = onOpenSettings, settingsLabel = settingsLabel)
    }
}
