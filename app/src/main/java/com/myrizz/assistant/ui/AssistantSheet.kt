package com.myrizz.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.R
import com.myrizz.assistant.ui.components.BrandMark
import com.myrizz.assistant.ui.theme.MyRizzTheme

/**
 * Bottom sheet over a dimmed scrim. Used by the floating overlay (inside a service window) and by the
 * text-selection activity. It never uses Dialog/Popup windows, which cannot be hosted by a service.
 *
 * @param useImePadding true in Activities (edge-to-edge); false in the overlay window, which resizes itself.
 */
@Composable
fun AssistantSheet(
    controller: AssistantController,
    visibleState: MutableTransitionState<Boolean>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onPickScreenshot: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    settingsLabel: String? = null,
    onOpenApp: (() -> Unit)? = null,
    initialTab: AssistantTab = AssistantTab.REPLIES,
    onCopied: (() -> Unit)? = null,
    replaceAction: ((String) -> Unit)? = null,
    useImePadding: Boolean = false,
) {
    MyRizzTheme {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Box(modifier.fillMaxSize().testTag("assistant_sheet")) {
            AnimatedVisibility(visibleState = visibleState, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                )
            }
            AnimatedVisibility(
                visibleState = visibleState,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .let { if (useImePadding) it.imePadding() else it },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 6.dp,
                ) {
                    Column {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.CenterHorizontally)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BrandMark(size = 32.dp)
                            Text(
                                stringResource(R.string.overlay_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (onOpenApp != null) {
                                IconButton(onClick = onOpenApp, modifier = Modifier.testTag("sheet_open_app")) {
                                    Icon(
                                        Icons.Rounded.Home,
                                        contentDescription = stringResource(R.string.overlay_notif_open),
                                    )
                                }
                            }
                            IconButton(onClick = onClose, modifier = Modifier.testTag("sheet_close")) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                        ) {
                            AssistantContent(
                                controller = controller,
                                onPickScreenshot = onPickScreenshot,
                                initialTab = initialTab,
                                onCopied = onCopied,
                                replaceAction = replaceAction,
                                onOpenSettings = onOpenSettings,
                                settingsLabel = settingsLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}
