package com.myrizz.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myrizz.assistant.R
import com.myrizz.assistant.container
import com.myrizz.assistant.domain.Routes
import com.myrizz.assistant.domain.UrlValidator
import com.myrizz.assistant.overlay.BubbleControl
import com.myrizz.assistant.overlay.OverlayService
import com.myrizz.assistant.ui.components.BrandMark
import com.myrizz.assistant.ui.components.SoftCard
import com.myrizz.assistant.ui.rememberPermissionUi
import com.myrizz.assistant.ui.theme.Brand

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val perms = rememberPermissionUi()
    val overlayGranted by perms.overlayGranted
    var wantEnable by remember { mutableStateOf(false) }

    // The user asked for the bubble but had to grant the permission first: finish the job on return.
    LaunchedEffect(overlayGranted, wantEnable) {
        if (overlayGranted && wantEnable) {
            wantEnable = false
            BubbleControl.enable(context)
        }
    }
    // Restore the bubble if the user left it on and the process was restarted.
    LaunchedEffect(overlayGranted) {
        if (overlayGranted && container.settings.current.bubbleEnabled && !OverlayService.running) {
            BubbleControl.enable(context)
        }
    }

    val bubbleOn = settings.bubbleEnabled && overlayGranted
    val backendOk = UrlValidator.normalizeHttps(settings.backendUrl) != null

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color(0x663B1478), Color(0x00000000)))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onNavigate(Routes.SETTINGS) }, modifier = Modifier.testTag("home_settings")) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }

            SoftCard(Modifier.fillMaxWidth().testTag("bubble_card")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.home_bubble_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                when {
                                    bubbleOn -> R.string.home_bubble_on
                                    settings.bubbleEnabled && !overlayGranted -> R.string.home_bubble_needs_permission
                                    else -> R.string.home_bubble_off
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleOn,
                        onCheckedChange = { on ->
                            if (!on) {
                                wantEnable = false
                                BubbleControl.disable(context)
                            } else if (!overlayGranted) {
                                wantEnable = true
                                perms.requestOverlay()
                            } else {
                                BubbleControl.enable(context)
                                if (!perms.notificationsGranted.value) perms.requestNotifications()
                            }
                        },
                        modifier = Modifier.testTag("bubble_switch"),
                    )
                }
            }

            if (!backendOk) {
                SoftCard(Modifier.fillMaxWidth().testTag("setup_required_card"), onClick = { onNavigate(Routes.SETTINGS) }) {
                    Text(
                        stringResource(R.string.home_setup_required_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(stringResource(R.string.home_setup_required_body), style = MaterialTheme.typography.bodyMedium)
                }
            }

            FeatureRow(
                FeatureItem(Icons.Rounded.AutoAwesome, R.string.home_card_assistant, R.string.home_card_assistant_sub, Routes.ASSISTANT),
                FeatureItem(Icons.Rounded.AddPhotoAlternate, R.string.home_card_screenshot, R.string.home_card_screenshot_sub, Routes.SCREENSHOT),
                onNavigate,
            )
            FeatureRow(
                FeatureItem(Icons.Rounded.Palette, R.string.home_card_styles, R.string.home_card_styles_sub, Routes.TONES),
                FeatureItem(Icons.Rounded.Favorite, R.string.home_card_favorites, R.string.home_card_favorites_sub, Routes.FAVORITES),
                onNavigate,
            )
            FeatureRow(
                FeatureItem(Icons.Rounded.History, R.string.home_card_history, R.string.home_card_history_sub, Routes.HISTORY),
                FeatureItem(Icons.Rounded.Tune, R.string.home_card_setup, R.string.home_card_setup_sub, Routes.SETUP),
                onNavigate,
            )
        }
    }
}

private data class FeatureItem(val icon: ImageVector, val title: Int, val subtitle: Int, val route: String)

@Composable
private fun FeatureRow(a: FeatureItem, b: FeatureItem, onNavigate: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureCard(a, onNavigate, Modifier.weight(1f).fillMaxHeight())
        FeatureCard(b, onNavigate, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun FeatureCard(item: FeatureItem, onNavigate: (String) -> Unit, modifier: Modifier) {
    SoftCard(modifier.testTag("card_${item.route}"), onClick = { onNavigate(item.route) }) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brand.gradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = Color.White)
        }
        Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(item.subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
