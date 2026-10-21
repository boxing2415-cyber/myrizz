package com.myrizz.assistant.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.myrizz.assistant.domain.ConversationParser
import com.myrizz.assistant.domain.OcrLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrOutcome(
    /** "Them: ..." / "Me: ..." transcript, ready to edit. */
    val transcript: String,
    val letterCount: Int,
)

/**
 * On-device OCR with ML Kit (bundled Latin model: English and Greeklish).
 * ML Kit has no Greek-script model, so Greek screenshots should use "Improve with AI vision".
 * The image never leaves the phone in this step.
 */
class OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): OcrOutcome {
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.right, box.top, box.bottom)
        }
        val turns = ConversationParser.parse(lines, bitmap.width)
        val transcript = ConversationParser.format(turns)
        return OcrOutcome(transcript, transcript.count { it.isLetter() })
    }
}
