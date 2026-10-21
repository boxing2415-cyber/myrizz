package com.myrizz.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.myrizz.assistant.domain.Routes
import com.myrizz.assistant.domain.ToolType
import com.myrizz.assistant.ui.AssistantSheet
import com.myrizz.assistant.ui.AssistantTab

/**
 * Entry in the system text-selection menu ("Rewrite with MyRizz AI"), the Android-supported way to
 * replace text in another app:
 *  - for editable fields the chosen result REPLACES the selected text (EXTRA_PROCESS_TEXT result);
 *  - for read-only text (a received message) it offers reply ideas to copy.
 * The selected text is only used after the user taps a button.
 */
class ProcessTextActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
            .take(MAX_INPUT_CHARS)
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        val controller = ViewModelProvider(this)[AssistantViewModel::class.java].controller

        if (savedInstanceState == null) {
            controller.setInput(selected)
            controller.setToolInput(selected)
            controller.setTool(if (readOnly) ToolType.REPLY else ToolType.IMPROVE)
        }

        val doReplace: (String) -> Unit = { replaced -> replaceSelection(replaced) }

        setContent {
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AssistantSheet(
                controller = controller,
                visibleState = visible,
                onClose = { finish() },
                onPickScreenshot = null,
                onOpenSettings = {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_ROUTE, Routes.SETTINGS),
                    )
                    finish()
                },
                initialTab = if (readOnly) AssistantTab.REPLIES else AssistantTab.TOOLS,
                onCopied = { finish() },
                replaceAction = if (readOnly) null else doReplace,
                useImePadding = true,
            )
        }
    }

    private fun replaceSelection(text: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}
