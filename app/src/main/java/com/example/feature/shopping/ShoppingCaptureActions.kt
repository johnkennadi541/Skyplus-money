package com.example.feature.shopping

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.example.domain.usecase.ShoppingInputParser
import com.example.domain.usecase.ShoppingItemDraft
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

/**
 * Voice recognition uses Android's speech-recognition activity; the app does not request or retain
 * microphone recordings. Photo OCR runs on-device with ML Kit and never uploads the selected image.
 */
@Composable
fun ShoppingCaptureActions(
  onDraftsReady: (List<ShoppingItemDraft>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var message by remember { mutableStateOf<String?>(null) }

  val speechLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val spoken = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
        .orEmpty()
      val drafts = ShoppingInputParser.parse(spoken)
      if (drafts.isEmpty()) message = "No shopping items were recognised. Try again or type them."
      else onDraftsReady(drafts)
    }
  }

  fun processImage(image: InputImage) {
    message = "Reading text from photo…"
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
      .addOnSuccessListener { result ->
        val drafts = ShoppingInputParser.parse(result.text)
        if (drafts.isEmpty()) message = "No readable list text found. Try a clearer photo."
        else {
          message = null
          onDraftsReady(drafts)
        }
      }
      .addOnFailureListener { message = "Could not read this photo. Try another image or type the items." }
      .addOnCompleteListener { recognizer.close() }
  }

  val photoLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent(),
  ) { uri ->
    if (uri != null) {
      try {
        processImage(InputImage.fromFilePath(context, uri))
      } catch (_: Exception) {
        message = "Could not open this photo. Choose another image."
      }
    }
  }

  val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicturePreview(),
  ) { bitmap ->
    if (bitmap != null) processImage(InputImage.fromBitmap(bitmap, 0))
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = {
          message = null
          try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
              putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
              putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the items you want to add")
              putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechLauncher.launch(intent)
          } catch (_: ActivityNotFoundException) {
            message = "Speech recognition is not available on this device."
          }
        },
        modifier = Modifier.weight(1f).testTag("shopping_voice_add_button"),
      ) {
        Icon(Icons.Rounded.Mic, contentDescription = null)
        Text(" Voice")
      }
      OutlinedButton(
        onClick = {
          message = null
          photoLauncher.launch("image/*")
        },
        modifier = Modifier.weight(1f).testTag("shopping_photo_add_button"),
      ) {
        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
        Text(" Photo")
      }
      OutlinedButton(
        onClick = { message = null; cameraLauncher.launch(null) },
        modifier = Modifier.weight(1f).testTag("shopping_camera_add_button"),
      ) {
        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
        Text(" Camera")
      }
    }
    message?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
  }
}

/** Review gate shared by voice and OCR: nothing is persisted until the user confirms. */
@Composable
fun ShoppingImportReviewDialog(
  drafts: List<ShoppingItemDraft>,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Review imported items") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          "Check names and quantities before adding. Prices are never guessed; imported items without a price are marked for price entry and should not be treated as free.",
          style = MaterialTheme.typography.bodySmall,
        )
        drafts.forEachIndexed { index, item ->
          Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("${index + 1}. ${item.name}", style = MaterialTheme.typography.bodyMedium)
            Text(
              "${item.quantityDecimal} ${item.unit} · ${item.estimatedUnitPriceDecimal?.let { "Unit price: $it" } ?: "Price not provided"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onConfirm, modifier = Modifier.testTag("shopping_import_confirm_button")) {
        Text("Add items")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
