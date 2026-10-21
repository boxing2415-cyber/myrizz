package com.myrizz.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myrizz.assistant.BuildConfig
import com.myrizz.assistant.LocaleHelper
import com.myrizz.assistant.R
import com.myrizz.assistant.UiError
import com.myrizz.assistant.container
import com.myrizz.assistant.domain.Routes
import com.myrizz.assistant.domain.UiLanguage
import com.myrizz.assistant.domain.UrlValidator
import com.myrizz.assistant.overlay.BubbleControl
import com.myrizz.assistant.toUiError
import com.myrizz.assistant.ui.components.BrandMark
import com.myrizz.assistant.ui.components.ErrorBanner
import com.myrizz.assistant.ui.components.GradientButton
import com.myrizz.assistant.ui.components.NavRow
import com.myrizz.assistant.ui.components.ReplyLanguageChips
import com.myrizz.assistant.ui.components.ScreenScaffold
import com.myrizz.assistant.ui.components.SectionLabel
import com.myrizz.assistant.ui.components.SoftCard
import com.myrizz.assistant.ui.components.SwitchRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data object Ok : ConnectionTest
    data object NoAi : ConnectionTest
    data class Failed(val error: UiError) : ConnectionTest
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val container = LocalContext.current.container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(settings.backendUrl) }
    var token by remember { mutableStateOf(settings.accessToken) } // never put the token in saved instance state
    var urlInvalid by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<ConnectionTest>(ConnectionTest.Idle) }
    val scope = rememberCoroutineScope()

    fun save(): Boolean {
        val normalized = if (url.isBlank()) "" else UrlValidator.normalizeHttps(url)
        if (normalized == null) {
            urlInvalid = true
            saved = false
            return false
        }
        container.settings.update { it.copy(backendUrl = normalized, accessToken = token.trim()) }
        url = normalized
        urlInvalid = false
        saved = true
        return true
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        SectionLabel(stringResource(R.string.settings_section_ai))
        SoftCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    saved = false
                    urlInvalid = false
                },
                modifier = Modifier.fillMaxWidth().testTag("backend_url"),
                label = { Text(stringResource(R.string.settings_backend_url)) },
                placeholder = { Text(stringResource(R.string.settings_backend_url_hint)) },
                isError = urlInvalid,
                supportingText = if (urlInvalid) {
                    { Text(stringResource(R.string.settings_backend_url_invalid)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    saved = false
                },
                modifier = Modifier.fillMaxWidth().testTag("backend_token"),
                label = { Text(stringResource(R.string.settings_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientButton(
                    text = stringResource(R.string.settings_save),
                    onClick = { save() },
                    modifier = Modifier.weight(1f).testTag("backend_save"),
                )
                OutlinedButton(
                    onClick = {
                        if (save()) {
                            test = ConnectionTest.Testing
                            scope.launch {
                                test = try {
                                    val health = container.ai.health()
                                    if (health.aiConfigured) ConnectionTest.Ok else ConnectionTest.NoAi
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    ConnectionTest.Failed(e.toUiError())
                                }
                            }
                        }
                    },
                    enabled = url.isNotBlank() && test != ConnectionTest.Testing,
                    modifier = Modifier.weight(1f).testTag("backend_test"),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        stringResource(if (test == ConnectionTest.Testing) R.string.settings_testing else R.string.settings_test),
                    )
                }
            }
            if (saved) Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
            when (val t = test) {
                ConnectionTest.Ok -> Text(stringResource(R.string.settings_test_ok), color = MaterialTheme.colorScheme.primary)
                ConnectionTest.NoAi -> Text(stringResource(R.string.settings_test_no_ai), color = MaterialTheme.colorScheme.error)
                is ConnectionTest.Failed -> ErrorBanner(t.error, onDismiss = { test = ConnectionTest.Idle }, onOpenSettings = null)
                else -> Unit
            }
        }

        SectionLabel(stringResource(R.string.settings_section_general))
        NavRow(Icons.Rounded.Language, stringResource(R.string.settings_language), stringResource(R.string.settings_language_sub), { onNavigate(Routes.LANGUAGE) })
        NavRow(Icons.Rounded.Palette, stringResource(R.string.settings_styles), null, { onNavigate(Routes.TONES) })
        NavRow(Icons.Rounded.Security, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_sub), { onNavigate(Routes.PRIVACY) })
        NavRow(Icons.Rounded.Info, stringResource(R.string.settings_about), null, { onNavigate(Routes.ABOUT) })
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val container = LocalContext.current.container
    val settings by container.settings.state.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.lang_title), onBack = onBack) {
        SectionLabel(stringResource(R.string.lang_ui_title))
        SoftCard(Modifier.fillMaxWidth()) {
            UiLanguage.entries.forEach { lang ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            container.settings.update { it.copy(uiLanguage = lang) }
                            LocaleHelper.apply(lang) // recreates the Activity in the new language
                        }
                        .testTag("ui_lang_${lang.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.uiLanguage == lang, onClick = null)
                    Text(stringResource(lang.labelRes), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }
        }
        SectionLabel(stringResource(R.string.lang_reply_title))
        ReplyLanguageChips(settings.replyLanguage, onSelect = { lang ->
            container.settings.update { it.copy(replyLanguage = lang) }
        })
        Text(
            stringResource(R.string.lang_reply_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }

    fun deleted() = Toast.makeText(context, R.string.privacy_cleared, Toast.LENGTH_SHORT).show()

    ScreenScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) {
        SoftCard(Modifier.fillMaxWidth()) {
            listOf(
                R.string.privacy_p1, R.string.privacy_p2, R.string.privacy_p3,
                R.string.privacy_p4, R.string.privacy_p5, R.string.privacy_p6,
            ).forEach { res ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            SwitchRow(
                title = stringResource(R.string.privacy_consent),
                subtitle = stringResource(R.string.privacy_consent_sub),
                checked = settings.consentGranted,
                onCheckedChange = { on -> container.settings.update { it.copy(consentGranted = on) } },
                modifier = Modifier.testTag("privacy_consent"),
            )
            SwitchRow(
                title = stringResource(R.string.privacy_history),
                subtitle = stringResource(R.string.privacy_history_sub),
                checked = settings.historyEnabled,
                onCheckedChange = { on ->
                    container.settings.update { it.copy(historyEnabled = on) }
                    if (!on) container.history.clear()
                },
                modifier = Modifier.testTag("privacy_history"),
            )
        }
        OutlinedButton(
            onClick = { container.history.clear(); deleted() },
            modifier = Modifier.fillMaxWidth().testTag("privacy_clear_history"),
        ) { Text(stringResource(R.string.privacy_clear_history)) }
        OutlinedButton(
            onClick = { container.favorites.clear(); deleted() },
            modifier = Modifier.fillMaxWidth().testTag("privacy_clear_favorites"),
        ) { Text(stringResource(R.string.privacy_clear_favorites)) }
        GradientButton(
            text = stringResource(R.string.privacy_clear_all),
            onClick = { confirmWipe = true },
            modifier = Modifier.fillMaxWidth().testTag("privacy_clear_all"),
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.privacy_clear_all)) },
            text = { Text(stringResource(R.string.privacy_clear_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWipe = false
                        container.wipeAll()
                        BubbleControl.disable(context)
                        deleted()
                        LocaleHelper.apply(UiLanguage.SYSTEM)
                    },
                    modifier = Modifier.testTag("privacy_confirm_wipe"),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        SoftCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandMark(size = 56.dp, modifier = Modifier.size(56.dp))
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_platform_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
