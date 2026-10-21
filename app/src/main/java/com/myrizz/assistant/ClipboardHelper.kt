package com.myrizz.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

/** Clipboard access that only ever happens because the user tapped Copy or Paste. */
object ClipboardHelper {

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MyRizz reply", text)
        if (Build.VERSION.SDK_INT >= 33) {
            // Hide the content from the clipboard preview that Android 13+ shows on screen.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)
        // Android 13+ shows its own "Copied" confirmation.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, R.string.copied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    /** Reads the clipboard text. Call only from an explicit "Paste" tap while our window has focus. */
    fun paste(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }
}
