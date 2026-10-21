package com.myrizz.assistant.overlay

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myrizz.assistant.IncomingShare
import com.myrizz.assistant.container
import com.myrizz.assistant.ml.ImageIntake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible helper: a Service cannot show the system photo picker, so the floating panel starts this
 * transparent Activity, which opens the picker and hands the chosen image back through the container.
 * An empty [IncomingShare] means "cancelled", and the panel simply reopens.
 */
class PickerActivity : AppCompatActivity() {

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handle(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun handle(uri: Uri?) {
        val container = applicationContext.container
        if (uri == null) {
            container.deliver(IncomingShare())
            finish()
            return
        }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { ImageIntake.prepare(this@PickerActivity, uri, container.screenshotDir) }.getOrNull()
            }
            container.deliver(IncomingShare(imagePath = file?.absolutePath))
            finish()
        }
    }
}
