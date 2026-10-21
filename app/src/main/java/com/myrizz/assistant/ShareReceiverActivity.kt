package com.myrizz.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.myrizz.assistant.ml.ImageIntake
import com.myrizz.assistant.overlay.BubbleControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Share to MyRizz AI" target. The user explicitly shares a message or screenshot from any app;
 * it opens the floating panel over that app (or the main app when overlays are not allowed).
 * Nothing is sent to the AI here: the user still has to tap Generate.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = applicationContext.container
        val incoming = intent

        val text = if (incoming?.type?.startsWith("text/") == true) {
            incoming.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else null
        val imageUri: Uri? = if (incoming?.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)
        } else null

        when {
            text != null -> {
                // The latest messages matter most, so keep the end of a very long share.
                deliverAndOpen(IncomingShare(text = text.takeLast(MAX_INPUT_CHARS)))
            }
            imageUri != null -> lifecycleScope.launch {
                val file = withContext(Dispatchers.IO) {
                    runCatching { ImageIntake.prepare(this@ShareReceiverActivity, imageUri, container.screenshotDir) }.getOrNull()
                }
                if (file == null) {
                    Toast.makeText(this@ShareReceiverActivity, R.string.err_ocr_failed, Toast.LENGTH_LONG).show()
                } else {
                    deliverAndOpen(IncomingShare(imagePath = file.absolutePath))
                }
                finish()
            }
            else -> {
                Toast.makeText(this, R.string.err_empty, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        if (text != null) finish()
    }

    private fun deliverAndOpen(share: IncomingShare) {
        applicationContext.container.deliver(share)
        if (!BubbleControl.openPanel(this)) {
            // Overlay not allowed (or refused): fall back to the main app, which shows the same assistant.
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
