@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myrizz.assistant.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.ClipboardHelper
import com.myrizz.assistant.MAX_INPUT_CHARS
import com.myrizz.assistant.R
import com.myrizz.assistant.ReplyItem
import com.myrizz.assistant.domain.ToolType
import com.myrizz.assistant.ui.components.ReplyLanguageChips
import com.myrizz.assistant.ui.components.SectionLabel
import com.myrizz.assistant.ui.components.SoftCard
import com.myrizz.assistant.ui.components.StatusArea
import com.myrizz.assistant.ui.components.StyleChips
import com.myrizz.assistant.ui.components.GradientButton

enum class AssistantTab { REPLIES, TOOLS }

/**
 * The reply assistant UI. One implementation is shared by the main app, the floating overlay panel and
 * the text-selection sheet. It does not scroll by itself: put it inside a scrolling container.
 *
 * @param onPickScreenshot opens the system photo picker; null hides the screenshot button.
 * @param onCopied called after a reply was copied (the overlay closes itself so you can paste at once).
 * @param replaceAction when non-null a "Replace text" button appears on every result (text-selection flow).
 */
@Composable
fun AssistantContent(
    controller: AssistantController,
    onPickScreenshot: (() -> Unit)?,
    modifier: Modifier = Modifier,
    initialTab: AssistantTab = AssistantTab.REPLIES,
    onCopied: (() -> Unit)? = null,
    replaceAction: ((String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    settingsLabel: String? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val favorites by controller.favorites.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val context = LocalContext.current

    val copy: (String) -> Unit = { text ->
        ClipboardHelper.copy(context, text)
        onCopied?.invoke()
    }
    val pasteInto: ((String) -> Unit) -> Unit = { apply ->
        val text = ClipboardHelper.paste(context)
        if (text == null) {
            Toast.makeText(context, R.string.nothing_to_paste, Toast.LENGTH_SHORT).show()
        } else {
            apply(text.take(MAX_INPUT_CHARS))
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TabSwitch(tab, onSelect = { tab = it })

        if (tab == AssistantTab.REPLIES) {
            val shot = state.shot
            if (shot != null) {
                ScreenshotSection(
                    controller = controller,
                    shot = shot,
                    onPickAnother = onPickScreenshot,
                )
            } else {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = controller::setInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("input_field"),
                    placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                    minLines = 4,
                    maxLines = 10,
                    shape = RoundedCornerShape(18.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallAction(Icons.Rounded.ContentPaste, stringResource(R.string.action_paste), "paste_button") {
                        pasteInto { controller.setInput(it) }
                    }
                    if (onPickScreenshot != null) {
                        SmallAction(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.assistant_screenshot), "screenshot_button") {
                            onPickScreenshot()
                        }
                    }
                    if (state.input.isNotBlank()) {
                        SmallAction(Icons.Rounded.Close, stringResource(R.string.action_clear), "clear_button") {
                            controller.clearConversation()
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.assistant_style))
            StyleChips(state.style, controller::setStyle)

            SectionLabel(stringResource(R.string.assistant_language))
            ReplyLanguageChips(settings.replyLanguage, controller::setReplyLanguage)

            OutlinedTextField(
                value = state.customInstruction,
                onValueChange = controller::setCustomInstruction,
                modifier = Modifier.fillMaxWidth().testTag("custom_instruction"),
                placeholder = { Text(stringResource(R.string.assistant_custom_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )

            GradientButton(
                text = stringResource(if (state.loading) R.string.assistant_generating else R.string.assistant_generate),
                onClick = { controller.generate() },
                modifier = Modifier.fillMaxWidth().testTag("generate_button"),
                enabled = state.shot == null && state.input.isNotBlank(),
                loading = state.loading,
                icon = Icons.Rounded.AutoAwesome,
            )

            StatusArea(state, controller, onOpenSettings, settingsLabel)

            if (state.replies.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.assistant_replies), Modifier.weight(1f))
                    state.detectedLanguage?.let {
                        Text(
                            stringResource(R.string.assistant_detected, languageLabel(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ReplyList(
                    items = state.replies,
                    busyId = state.busyItemId,
                    favoriteTexts = favorites.map { it.text }.toSet(),
                    controller = controller,
                    showShorten = true,
                    onCopy = copy,
                    replaceAction = replaceAction,
                )
                OutlinedButton(
                    onClick = { controller.generate(regenerate = true) },
                    enabled = !state.loading && state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("regenerate_button"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.assistant_regenerate))
                }
            }
        } else {
            Text(
                stringResource(R.string.tools_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolType.entries.forEach { tool ->
                    FilterChip(
                        selected = tool == state.tool,
                        onClick = { controller.setTool(tool) },
                        label = { Text(stringResource(tool.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("tool_${tool.apiId}"),
                    )
                }
            }
            OutlinedTextField(
                value = state.toolInput,
                onValueChange = controller::setToolInput,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag("tool_input"),
                placeholder = {
                    Text(
                        stringResource(
                            if (state.tool.textRequired) R.string.tools_input_hint else R.string.tools_input_hint_optional,
                        ),
                    )
                },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(18.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Rounded.ContentPaste, stringResource(R.string.action_paste), "tool_paste") {
                    pasteInto { controller.setToolInput(it) }
                }
                if (state.toolInput.isNotBlank()) {
                    SmallAction(Icons.Rounded.Close, stringResource(R.string.action_clear), "tool_clear") {
                        controller.setToolInput("")
                    }
                }
            }
            OutlinedTextField(
                value = state.customInstruction,
                onValueChange = controller::setCustomInstruction,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.assistant_custom_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            SectionLabel(stringResource(R.string.assistant_language))
            ReplyLanguageChips(settings.replyLanguage, controller::setReplyLanguage)

            GradientButton(
                text = stringResource(R.string.tools_run),
                onClick = {
                    if (state.tool == ToolType.REPLY) tab = AssistantTab.REPLIES
                    controller.runTool()
                },
                modifier = Modifier.fillMaxWidth().testTag("run_tool_button"),
                enabled = !state.tool.textRequired || state.toolInput.isNotBlank(),
                loading = state.loading,
                icon = Icons.Rounded.AutoAwesome,
            )
            StatusArea(state, controller, onOpenSettings, settingsLabel)

            if (state.toolItems.isNotEmpty()) {
                SectionLabel(stringResource(R.string.tools_result))
                ReplyList(
                    items = state.toolItems,
                    busyId = state.busyItemId,
                    favoriteTexts = favorites.map { it.text }.toSet(),
                    controller = controller,
                    showShorten = state.tool != ToolType.TONE,
                    onCopy = copy,
                    replaceAction = if (state.tool == ToolType.TONE) null else replaceAction,
                )
            }
        }
    }
}

@Composable
private fun TabSwitch(selected: AssistantTab, onSelect: (AssistantTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == AssistantTab.REPLIES,
            onClick = { onSelect(AssistantTab.REPLIES) },
            label = { Text(stringResource(R.string.tab_reply)) },
            modifier = Modifier.testTag("tab_replies"),
        )
        FilterChip(
            selected = selected == AssistantTab.TOOLS,
            onClick = { onSelect(AssistantTab.TOOLS) },
            label = { Text(stringResource(R.string.tab_tools)) },
            modifier = Modifier.testTag("tab_tools"),
        )
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.testTag(tag),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label)
    }
}

@Composable
private fun ReplyList(
    items: List<ReplyItem>,
    busyId: Long?,
    favoriteTexts: Set<String>,
    controller: AssistantController,
    showShorten: Boolean,
    onCopy: (String) -> Unit,
    replaceAction: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val replace: (() -> Unit)? = if (replaceAction == null) null else ({ replaceAction(item.text) })
            ReplyCard(
                text = item.text,
                isFavorite = item.text in favoriteTexts,
                busy = busyId == item.id,
                showShorten = showShorten,
                onCopy = { onCopy(item.text) },
                onTextChange = { controller.editReply(item.id, it) },
                onShorten = { controller.transformReply(item.id) },
                onToggleFavorite = { controller.toggleFavorite(item.text) },
                replaceAction = replace,
            )
        }
    }
}

/** One suggested reply: tap to copy, edit in place, shorten with AI, save as favorite, or replace text. */
@Composable
fun ReplyCard(
    text: String,
    isFavorite: Boolean,
    busy: Boolean,
    onCopy: () -> Unit,
    onTextChange: (String) -> Unit,
    onShorten: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showShorten: Boolean = true,
    replaceAction: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(text) { mutableStateOf(text) }

    SoftCard(modifier.fillMaxWidth().testTag("reply_card")) {
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().testTag("reply_edit_field"),
                shape = RoundedCornerShape(14.dp),
            )
            Row {
                TextButton(onClick = { draft = text; editing = false }) { Text(stringResource(R.string.action_cancel)) }
                TextButton(
                    onClick = {
                        if (draft.isNotBlank()) onTextChange(draft.trim())
                        editing = false
                    },
                    modifier = Modifier.testTag("reply_edit_save"),
                ) { Text(stringResource(R.string.action_save)) }
            }
        } else {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.action_copy), onClick = onCopy)
                    .testTag("reply_text"),
            )
            Text(
                stringResource(R.string.reply_tap_to_copy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.8f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCopy, modifier = Modifier.testTag("reply_copy")) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy))
            }
            IconButton(onClick = { editing = !editing }, modifier = Modifier.testTag("reply_edit")) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            if (showShorten) {
                IconButton(onClick = onShorten, enabled = !busy, modifier = Modifier.testTag("reply_shorten")) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.ContentCut, contentDescription = stringResource(R.string.reply_shorten))
                    }
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("reply_favorite")) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.reply_remove_favorite else R.string.reply_save_favorite,
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (replaceAction != null) {
                TextButton(onClick = replaceAction, modifier = Modifier.testTag("reply_replace")) {
                    Text(stringResource(R.string.reply_replace))
                }
            }
        }
    }
}

@Composable
private fun languageLabel(code: String): String = when (code.lowercase()) {
    "el", "greek" -> stringResource(R.string.reply_lang_el)
    "en", "english" -> stringResource(R.string.reply_lang_en)
    "greeklish" -> stringResource(R.string.reply_lang_greeklish)
    else -> code
}
