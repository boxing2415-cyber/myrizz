package com.myrizz.assistant.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.R
import com.myrizz.assistant.container
import com.myrizz.assistant.ui.AssistantContent
import com.myrizz.assistant.ui.ScreenshotIntro
import com.myrizz.assistant.ui.ScreenshotSection
import com.myrizz.assistant.ui.components.GradientButton
import com.myrizz.assistant.ui.components.ScreenScaffold
import com.myrizz.assistant.ui.components.SectionLabel
import com.myrizz.assistant.ui.components.StatusArea
import com.myrizz.assistant.ui.components.StyleChips
import com.myrizz.assistant.ui.rememberScreenshotPicker

@Composable
fun AssistantScreen(controller: AssistantController, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val pick = rememberScreenshotPicker(controller)
    ScreenScaffold(title = stringResource(R.string.assistant_title), onBack = onBack) {
        AssistantContent(
            controller = controller,
            onPickScreenshot = pick,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
fun ScreenshotScreen(
    controller: AssistantController,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onGenerated: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val pick = rememberScreenshotPicker(controller)
    ScreenScaffold(title = stringResource(R.string.shot_title), onBack = onBack) {
        val shot = state.shot
        if (shot == null) {
            ScreenshotIntro(onPick = pick)
        } else {
            ScreenshotSection(controller = controller, shot = shot, onPickAnother = pick, onGenerated = onGenerated)
        }
        StatusArea(state, controller, onOpenSettings)
    }
}

@Composable
fun TonesScreen(controller: AssistantController, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(settings.defaultCustomInstruction) }
    var saved by remember { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.tones_title), onBack = onBack) {
        Text(
            stringResource(R.string.tones_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StyleChips(settings.defaultStyle) { style ->
            container.settings.update { it.copy(defaultStyle = style) }
            controller.setStyle(style)
        }
        Text(
            stringResource(R.string.tones_selected) + ": " + stringResource(settings.defaultStyle.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        SectionLabel(stringResource(R.string.tones_custom_title))
        OutlinedTextField(
            value = custom,
            onValueChange = {
                custom = it.take(300)
                saved = false
            },
            modifier = Modifier.fillMaxWidth().testTag("tones_custom"),
            placeholder = { Text(stringResource(R.string.tones_custom_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        GradientButton(
            text = stringResource(R.string.action_save),
            onClick = {
                val value = custom.trim()
                container.settings.update { it.copy(defaultCustomInstruction = value) }
                controller.setCustomInstruction(value)
                saved = true
            },
            modifier = Modifier.fillMaxWidth().testTag("tones_save"),
        )
        if (saved) {
            Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
        }
    }
}
