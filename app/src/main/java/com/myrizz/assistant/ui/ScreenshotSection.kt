@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myrizz.assistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.R
import com.myrizz.assistant.ShotState
import com.myrizz.assistant.container
import com.myrizz.assistant.ml.ImageIntake
import com.myrizz.assistant.ui.components.GradientButton
import com.myrizz.assistant.ui.components.SectionLabel
import com.myrizz.assistant.ui.components.SoftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the system photo picker (no storage permission needed), copies the chosen image into a private,
 * downscaled temporary file and hands it to the controller for on-device OCR. Returns the "open picker" action.
 */
@Composable
fun rememberScreenshotPicker(controller: AssistantController): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    runCatching { ImageIntake.prepare(context, uri, context.container.screenshotDir) }.getOrNull()
                }
                if (file != null) controller.onScreenshotFile(file) else controller.reportImageUnreadable()
            }
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/** Shown when no screenshot has been chosen yet. */
@Composable
fun ScreenshotIntro(onPick: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.shot_intro), style = MaterialTheme.typography.bodyLarge)
        GradientButton(
            text = stringResource(R.string.shot_pick),
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().testTag("shot_pick_button"),
            icon = Icons.Rounded.AddPhotoAlternate,
        )
    }
}

/**
 * Review step for a chosen screenshot: preview, OCR transcript (editable, with Me/Them turns),
 * optional AI vision upload (needs consent) and "Generate replies".
 */
@Composable
fun ScreenshotSection(
    controller: AssistantController,
    shot: ShotState,
    onPickAnother: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onGenerated: () -> Unit = {},
) {
    val preview by produceState<ImageBitmap?>(initialValue = null, shot.file) {
        value = withContext(Dispatchers.Default) {
            shot.file?.let { f -> ImageIntake.decode(f)?.asImageBitmap() }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        preview?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }

        if (shot.loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.shot_reading), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionLabel(stringResource(R.string.shot_extracted))
        Text(
            stringResource(R.string.shot_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (shot.lowConfidence && !shot.loading) {
            Text(
                stringResource(R.string.shot_low_confidence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (shot.usedVision) {
            Text(
                stringResource(R.string.shot_used_vision),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OutlinedTextField(
            value = shot.text,
            onValueChange = controller::setShotText,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("shot_text"),
            minLines = 5,
            maxLines = 14,
            shape = RoundedCornerShape(18.dp),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = controller::swapShotSides,
                enabled = shot.text.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.shot_swap))
            }
            OutlinedButton(
                onClick = controller::improveWithVision,
                enabled = !shot.loading && shot.file != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("shot_vision_button"),
            ) {
                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.shot_vision))
            }
        }
        Text(
            stringResource(R.string.shot_vision_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GradientButton(
            text = stringResource(R.string.shot_use),
            onClick = {
                controller.generateFromShot()
                onGenerated()
            },
            modifier = Modifier.fillMaxWidth().testTag("shot_generate_button"),
            enabled = !shot.loading && shot.text.isNotBlank(),
            icon = Icons.Rounded.AutoAwesome,
        )

        Row {
            if (onPickAnother != null) {
                TextButton(onClick = onPickAnother) { Text(stringResource(R.string.shot_pick)) }
            }
            TextButton(onClick = controller::clearShot, modifier = Modifier.testTag("shot_discard")) {
                Text(stringResource(R.string.shot_discard))
            }
        }
    }
}
