package com.example.feature.shopping

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.usecase.ShoppingReceiptDraft
import com.example.domain.usecase.ShoppingReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ShoppingReceiptCaptureAction(
  onReceiptReady: (ShoppingReceiptDraft) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var error by remember { mutableStateOf<String?>(null) }
  fun processReceiptImage(image: InputImage) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
      .addOnSuccessListener { result ->
        val draft = ShoppingReceiptParser.parse(result.text)
        if (draft.totalDecimal == null) error = draft.warnings.joinToString(" ")
        onReceiptReady(draft)
      }
      .addOnFailureListener { error = "Could not read receipt. Try a clearer photo." }
      .addOnCompleteListener { recognizer.close() }
  }

  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) {
      error = null
      try {
        processReceiptImage(InputImage.fromFilePath(context, uri))
      } catch (_: Exception) {
        error = "Could not open this receipt image."
      }
    }
  }
  val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
    if (bitmap != null) processReceiptImage(InputImage.fromBitmap(bitmap, 0))
  }

  androidx.compose.foundation.layout.Row(
    modifier = modifier,
    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
  ) {
    OutlinedButton(
      onClick = { error = null; launcher.launch("image/*") },
      modifier = Modifier.weight(1f).testTag("shopping_receipt_scan_button"),
    ) {
      Icon(Icons.Rounded.ReceiptLong, contentDescription = null)
      Text(" Gallery")
    }
    OutlinedButton(
      onClick = { error = null; cameraLauncher.launch(null) },
      modifier = Modifier.weight(1f).testTag("shopping_receipt_camera_button"),
    ) {
      Icon(Icons.Rounded.ReceiptLong, contentDescription = null)
      Text(" Camera")
    }
  }
  error?.let { Text(it) }
}

@Composable
fun ShoppingReceiptReviewDialog(
  draft: ShoppingReceiptDraft,
  accounts: List<FinancialAccount>,
  onDismiss: () -> Unit,
  onConfirm: (accountId: String, currencyCode: String, totalMinor: Long, merchant: String?, purchaseDate: Long?) -> Unit,
) {
  var amountText by remember(draft.fingerprint) { mutableStateOf(draft.totalDecimal.orEmpty()) }
  var merchantText by remember(draft.fingerprint) { mutableStateOf(draft.merchantCandidate.orEmpty()) }
  var purchaseDateText by remember(draft.fingerprint) {
    mutableStateOf(
      draft.purchaseDateMillis?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it))
      }.orEmpty()
    )
  }
  var selectedAccountId by remember(draft.fingerprint) { mutableStateOf(accounts.firstOrNull { it.isActive }?.id) }
  val activeAccounts = accounts.filter { it.isActive }
  val selected = activeAccounts.firstOrNull { it.id == selectedAccountId }
  val totalMinor = selected?.let { account ->
    runCatching { Money.fromDecimalString(amountText, account.currencyCode).minorUnits }.getOrNull()
  }
  val purchaseDateMillis = purchaseDateText.takeIf { it.isNotBlank() }?.let { value ->
    runCatching {
      SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
      }.parse(value)?.time
    }.getOrNull()
  }
  val dateValid = purchaseDateText.isBlank() || purchaseDateMillis != null
  val currencyConflict = draft.currencyCodeCandidate != null &&
    selected != null && !draft.currencyCodeCandidate.equals(selected.currencyCode, ignoreCase = true)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Review receipt") },
    text = {
      androidx.compose.foundation.layout.Column {
        Text("Check the extracted details. Nothing is recorded until you confirm.")
        if (draft.warnings.isNotEmpty()) Text(draft.warnings.joinToString(" "))
        OutlinedTextField(value = merchantText, onValueChange = { merchantText = it }, label = { Text("Store / Merchant") })
        OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Receipt total") })
        OutlinedTextField(value = purchaseDateText, onValueChange = { purchaseDateText = it }, label = { Text("Purchase date (YYYY-MM-DD, optional)") })
        Text("Choose the account to record this expense:")
        activeAccounts.forEach { account ->
          OutlinedButton(
            onClick = { selectedAccountId = account.id },
            enabled = true,
          ) {
            Text((if (account.id == selectedAccountId) "✓ " else "") + account.name + " · " + account.currencyCode)
          }
        }
        if (currencyConflict) Text("Receipt currency " + draft.currencyCodeCandidate + " does not match selected account currency.")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val account = selected ?: return@Button
          val minor = totalMinor ?: return@Button
          onConfirm(account.id, account.currencyCode, minor, merchantText.trim().ifBlank { null }, purchaseDateMillis)
        },
        enabled = selected != null && totalMinor != null && totalMinor > 0L && !currencyConflict && dateValid,
        modifier = Modifier.testTag("shopping_receipt_confirm_button"),
      ) { Text("Add Expense") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
