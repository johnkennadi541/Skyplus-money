package com.example.feature.smartdetection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import com.example.feature.smartdetection.engine.MerchantCategoryResolver
import com.example.domain.usecase.FormatMoneyUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDetectionSettingsScreen(
  viewModel: SmartDetectionViewModel,
  onOpenReviewQueue: () -> Unit,
  onOpenAccountMapping: () -> Unit,
  onOpenDetectionHistory: () -> Unit,
  onOpenNotificationSetup: () -> Unit = {},
  onOpenSmsPolicyStatus: () -> Unit = {},
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val isPermGranted by viewModel.isPermissionGranted.collectAsStateWithLifecycle()
  val reviewQueue by viewModel.reviewQueue.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) { viewModel.refreshPermissionStatus() }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.dismissMessage()
    }
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Smart Detection", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("smart_detection_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("smart_detection_settings_screen"),
      contentPadding = PaddingValues(OceanSpacing.md),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Permission Banner
      item {
        OceanCard(
          modifier = Modifier.widthIn(max = 760.dp).testTag("notification_access_card"),
          onClick = onOpenNotificationSetup,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (isPermGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
              contentDescription = null,
              tint = if (isPermGranted) OceanTheme.colors.positive else OceanTheme.colors.warning,
              modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(OceanSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isPermGranted) "Notification Access Active" else "Notification Access Setup Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = if (isPermGranted) {
                  "On-device NotificationListenerService is authorized. Tap to review privacy & setup details."
                } else {
                  "Explicit user permission is required. Tap to review setup instructions or grant access."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (!isPermGranted) {
            Spacer(modifier = Modifier.height(OceanSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
              Button(
                onClick = { viewModel.openNotificationSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
                modifier = Modifier.weight(1f).testTag("enable_notification_access_button"),
              ) {
                Text("Grant Access")
              }
              OutlinedButton(
                onClick = onOpenNotificationSetup,
                modifier = Modifier.testTag("open_notification_setup_button"),
              ) {
                Text("Setup Guide")
              }
            }
          }
        }
      }

      // Master Toggle
      item {
        OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Transaction Detection Engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Pause or resume SMS notification parsing from your current default SMS app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.isNotificationDetectionEnabled,
              onCheckedChange = { viewModel.toggleNotificationDetection(it) },
              modifier = Modifier.testTag("toggle_notification_detection_switch"),
            )
          }
        }
      }

      // Auto-save toggle
      item {
        OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Automatic Save (High Confidence)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Completed high-confidence transactions with an exact account match are posted automatically. Ambiguous items remain in Review Queue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.isAutoSaveHighConfidenceEnabled,
              onCheckedChange = { viewModel.toggleAutoSave(it) },
              modifier = Modifier.testTag("toggle_auto_save_switch"),
            )
          }
        }
      }

      // Navigation Cards
      item {
        OceanCard(
          modifier = Modifier.widthIn(max = 760.dp).testTag("nav_review_queue_card"),
          onClick = onOpenReviewQueue,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.RateReview, contentDescription = null, tint = OceanTheme.colors.gold)
              Spacer(modifier = Modifier.width(OceanSpacing.sm))
              Column {
                Text("Review Queue", style = MaterialTheme.typography.titleMedium)
                Text("SMS detections that could not be safely matched to an account or transaction type", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            OceanStatusBadge(
              text = "${reviewQueue.size} Pending",
              tone = if (reviewQueue.isNotEmpty()) BadgeTone.WARNING else BadgeTone.INFO,
            )
          }
        }
      }

      item {
        OceanCard(
          modifier = Modifier.widthIn(max = 760.dp).testTag("nav_account_mapping_card"),
          onClick = onOpenAccountMapping,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
              Spacer(modifier = Modifier.width(OceanSpacing.sm))
              Column {
                Text("Account Mapping Rules", style = MaterialTheme.typography.titleMedium)
                Text("Map account hints (last 4 digits) to SKYPLUS accounts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Text("Manage →", style = MaterialTheme.typography.labelMedium, color = OceanTheme.colors.gold)
          }
        }
      }

      item {
        OceanCard(
          modifier = Modifier.widthIn(max = 760.dp).testTag("nav_detection_history_card"),
          onClick = onOpenDetectionHistory,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
              Spacer(modifier = Modifier.width(OceanSpacing.sm))
              Column {
                Text("Automatic Detection History", style = MaterialTheme.typography.titleMedium)
                Text("Audit log of auto-saved, reviewed, and duplicate events", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Text("View →", style = MaterialTheme.typography.labelMedium, color = OceanTheme.colors.gold)
          }
        }
      }

      // SMS Policy Card (clickable to SmsPolicyStatusScreen)
      item {
        OceanCard(
          modifier = Modifier.widthIn(max = 760.dp).testTag("nav_sms_policy_card"),
          onClick = onOpenSmsPolicyStatus,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.Policy, contentDescription = null, tint = OceanTheme.colors.positive)
              Spacer(modifier = Modifier.width(OceanSpacing.sm))
              Column {
                Text("SMS Detection & Play Policy Status", style = MaterialTheme.typography.titleMedium)
                Text("SMS disabled per Google Play Restricted Permissions policy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            OceanStatusBadge(text = "COMPLIANT", tone = BadgeTone.POSITIVE)
          }
        }
      }

      // Strict SMS-only source boundary; no email or bank-app notification access.
      item {
        OceanCard(
          modifier = Modifier.widthIn(max = 760.dp).testTag("sms_only_source_policy_card"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
          ) {
            Icon(Icons.Rounded.Sms, contentDescription = null, tint = OceanTheme.colors.positive)
            Column(modifier = Modifier.weight(1f)) {
              Text("SMS Notifications Only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                "Only notifications posted by your phone's current default SMS app are inspected. Email apps and bank/UPI app notifications are ignored before their content is read. SKYPLUS MONEY does not read your SMS inbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAccessSetupScreen(
  viewModel: SmartDetectionViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val isPermGranted by viewModel.isPermissionGranted.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) { viewModel.refreshPermissionStatus() }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Notification Access Setup", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(OceanSpacing.md)
          .widthIn(max = 760.dp)
          .testTag("notification_access_setup_screen"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    ) {
      OceanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = OceanTheme.colors.gold, modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.width(OceanSpacing.sm))
          Column {
            Text(
              text = if (isPermGranted) "Permission Granted" else "Permission Not Enabled",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            OceanStatusBadge(
              text = if (isPermGranted) "ACTIVE" else "ACTION NEEDED",
              tone = if (isPermGranted) BadgeTone.POSITIVE else BadgeTone.WARNING,
            )
          }
        }
        Spacer(modifier = Modifier.height(OceanSpacing.sm))
        Text(
          text = "Why SKYPLUS MONEY requests Notification Access:\n" +
            "• Detects eligible bank transaction SMS notifications posted by your phone's current default SMS app.\n" +
            "• Posts completed, high-confidence entries automatically when the exact account is matched.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      OceanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Security, contentDescription = null, tint = OceanTheme.colors.positive)
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          Text("Strict Privacy Guarantees", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        Text(
          text = "• SMS Only: Only the current default SMS app's posted notifications are inspected.\n" +
            "• No Email Access: Email/Gmail notifications and every other app package are ignored before their content is read.\n" +
            "• No Inbox Reading: SKYPLUS MONEY does not request READ_SMS/RECEIVE_SMS or read your SMS inbox.\n" +
            "• On-Device Only: Parsing runs locally; raw SMS bodies are not stored.\n" +
            "• OTP & PIN Rejection: OTP, PIN, MPIN, CVV and password notifications are discarded.\n" +
            "• Encrypted Storage: Normalized transaction data is stored inside your SQLCipher-encrypted database.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Button(
        onClick = { viewModel.openNotificationSettings() },
        colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth().testTag("open_system_notification_settings_btn"),
      ) {
        Text(if (isPermGranted) "Manage Android Notification Settings" else "Enable Notification Access in Settings")
      }

      OutlinedButton(
        onClick = { viewModel.refreshPermissionStatus() },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Refresh Permission Status")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsPolicyStatusScreen(
  onOpenNotificationSetup: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("SMS Policy Status", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(OceanSpacing.md)
          .widthIn(max = 760.dp)
          .testTag("sms_policy_status_screen"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    ) {
      OceanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Sms, contentDescription = null, tint = OceanTheme.colors.warning, modifier = Modifier.size(28.dp))
          Spacer(modifier = Modifier.width(OceanSpacing.sm))
          Column {
            Text("SMS Auto-Detection Disabled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OceanStatusBadge(text = "BLOCKED BY GOOGLE PLAY POLICY", tone = BadgeTone.WARNING)
          }
        }
        Spacer(modifier = Modifier.height(OceanSpacing.sm))
        Text(
          text = "Google Play Policy Finding:\n" +
            "Under Google Play's SMS and Call Log Permissions Policy, READ_SMS and RECEIVE_SMS permissions are restricted strictly to apps acting as the user's selected Default SMS Handler.\n\n" +
            "Personal finance and expense-tracking applications are NOT eligible for SMS reading permissions unless they serve as the primary messaging app. Accordingly:\n" +
            "• READ_SMS and RECEIVE_SMS are NOT requested in AndroidManifest.xml.\n" +
            "• No Accessibility Service or hidden API workarounds are used.\n" +
            "• NotificationListenerService is used as the policy-compliant, user-consented alternative.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Button(
        onClick = onOpenNotificationSetup,
        colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Use Notification Detection Instead")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
  viewModel: SmartDetectionViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val reviewQueue by viewModel.reviewQueue.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val savedMerchantCategory by viewModel.merchantCategoryOverride.collectAsStateWithLifecycle()
  var itemToApprove by remember { mutableStateOf<TransactionDetectionEvent?>(null) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Review Queue (${reviewQueue.size})", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    if (reviewQueue.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("review_queue_empty"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = OceanTheme.colors.positive, modifier = Modifier.size(48.dp))
          Spacer(modifier = Modifier.height(OceanSpacing.sm))
          Text("Review Queue is Empty", style = MaterialTheme.typography.titleMedium)
          Text("No uncertain transactions require manual confirmation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("review_queue_list"),
        contentPadding = PaddingValues(OceanSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        items(reviewQueue, key = { it.id }) { item ->
          ReviewQueueItemCard(
            item = item,
            onApprove = {
              itemToApprove = item
              viewModel.loadMerchantCategoryOverride(item.detectedMerchant)
            },
            onDismiss = { viewModel.dismissReviewItem(item.id) },
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }
      }
    }
  }

  itemToApprove?.let { item ->
    TransactionCorrectionDialog(
      event = item,
      accounts = accounts,
      preferredCategory = savedMerchantCategory,
      onDismiss = { itemToApprove = null },
      onConfirm = { accountId, type, category, merchant, amountDecimal, rememberMerchantCategory ->
        viewModel.approveReviewItem(
          eventId = item.id,
          selectedAccountId = accountId,
          category = category,
          merchant = merchant,
          correctedType = type,
          correctedAmountDecimal = amountDecimal,
          currencyCode = item.detectedCurrency,
          rememberMerchantCategory = rememberMerchantCategory,
        )
        itemToApprove = null
      },
    )
  }
}

@Composable
fun ReviewQueueItemCard(
  item: TransactionDetectionEvent,
  onApprove: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val formattedAmount = FormatMoneyUseCase().format(item.detectedMoney)
  val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
  val dateStr = remember(item.receivedAt) { dateFormat.format(Date(item.receivedAt)) }

  OceanCard(modifier = modifier.testTag("review_item_${item.id}")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.detectedMerchant ?: item.senderOrTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "From: ${item.rawPackageName} • $dateStr",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = formattedAmount,
        style = OceanNumericStyle,
        color = OceanTheme.colors.gold,
      )
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    Row(
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OceanStatusBadge(text = item.detectedType.name, tone = BadgeTone.INFO)
      OceanStatusBadge(
        text = "${(item.confidence * 100).toInt()}% (${item.confidenceLevel.name})",
        tone = when (item.confidenceLevel) {
          DetectionConfidence.HIGH -> BadgeTone.POSITIVE
          DetectionConfidence.MEDIUM -> BadgeTone.WARNING
          DetectionConfidence.LOW -> BadgeTone.NEGATIVE
        },
      )
      item.detectedAccountLast4?.let {
        Text("••$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
      }
    }

    if (item.confidenceReasons.isNotEmpty()) {
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Text(
        text = "Confidence rules: ${item.confidenceReasons.joinToString(", ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_review_${item.id}")) {
        Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
      Button(
        onClick = onApprove,
        colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
        modifier = Modifier.testTag("approve_review_${item.id}"),
      ) {
        Text("Review & Correct")
      }
    }
  }
}

@Composable
fun TransactionCorrectionDialog(
  event: TransactionDetectionEvent,
  accounts: List<FinancialAccount>,
  preferredCategory: String? = null,
  onDismiss: () -> Unit,
  onConfirm: (
    accountId: String,
    type: TransactionType,
    category: String,
    merchant: String?,
    amountDecimal: String,
    rememberMerchantCategory: Boolean,
  ) -> Unit,
) {
  var selectedType by remember {
    mutableStateOf(
      if (event.detectedType == TransactionType.PENDING) TransactionType.EXPENSE else event.detectedType
    )
  }
  var selectedAccountId by remember {
    mutableStateOf(
      event.mappedAccountId
        ?: accounts.firstOrNull { it.currencyCode.equals(event.detectedCurrency, true) }?.id
        ?: accounts.firstOrNull()?.id.orEmpty()
    )
  }
  var selectedCategory by remember(event.id, preferredCategory) {
    mutableStateOf(
      when {
        selectedType == TransactionType.EXPENSE && preferredCategory in TransactionCategories.EXPENSE_CATEGORIES -> preferredCategory!!
        selectedType == TransactionType.INCOME -> TransactionCategories.INCOME_CATEGORIES.first()
        selectedType == TransactionType.REFUND || selectedType == TransactionType.REVERSAL -> "Refunds"
        else -> MerchantCategoryResolver.resolve(event.detectedMerchant, selectedType)
          ?: TransactionCategories.EXPENSE_CATEGORIES.first()
      }
    )
  }
  var rememberForMerchant by remember(event.id) { mutableStateOf(false) }
  var merchantText by remember { mutableStateOf(event.detectedMerchant.orEmpty()) }
  var amountText by remember { mutableStateOf(event.detectedMoney.toBigDecimal().toPlainString()) }

  val categories =
    if (selectedType == TransactionType.INCOME || selectedType == TransactionType.REFUND || selectedType == TransactionType.REVERSAL) {
      TransactionCategories.INCOME_CATEGORIES
    } else {
      TransactionCategories.EXPENSE_CATEGORIES
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Transaction Correction") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount (${event.detectedCurrency})") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("correction_amount_input"),
        )

        Text("Classification", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(
            TransactionType.EXPENSE,
            TransactionType.INCOME,
            TransactionType.REFUND,
            TransactionType.REVERSAL,
          ).forEach { type ->
            FilterChip(
              selected = selectedType == type,
              onClick = {
                selectedType = type
                selectedCategory = when (type) {
                  TransactionType.INCOME -> TransactionCategories.INCOME_CATEGORIES.first()
                  TransactionType.REFUND, TransactionType.REVERSAL -> "Refunds"
                  else -> MerchantCategoryResolver.resolve(event.detectedMerchant, type)
                    ?: TransactionCategories.EXPENSE_CATEGORIES.first()
                }
                rememberForMerchant = false
              },
              label = { Text(type.name) },
            )
          }
        }

        Text("Target Account", style = MaterialTheme.typography.labelMedium)
        accounts.forEach { acc ->
          val isSelected = selectedAccountId == acc.id
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable { selectedAccountId = acc.id }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = CircleShape,
              color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
              modifier = Modifier.size(16.dp),
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text("${acc.name} (${acc.currencyCode})", style = MaterialTheme.typography.bodyMedium)
          }
        }

        Text("Category", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          categories.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat) },
            )
          }
        }

        if (selectedType == TransactionType.EXPENSE && merchantText.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = rememberForMerchant,
              onCheckedChange = { rememberForMerchant = it },
              modifier = Modifier.testTag("remember_merchant_category_checkbox"),
            )
            Text("Remember this category for this merchant", style = MaterialTheme.typography.bodySmall)
          }
        }

        OutlinedTextField(
          value = merchantText,
          onValueChange = { merchantText = it },
          label = { Text("Merchant / Payee") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("correction_merchant_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (selectedAccountId.isNotBlank() && amountText.isNotBlank()) {
            onConfirm(selectedAccountId, selectedType, selectedCategory, merchantText.ifBlank { null }, amountText, rememberForMerchant)
          }
        },
        enabled = selectedAccountId.isNotBlank() && amountText.isNotBlank(),
        modifier = Modifier.testTag("confirm_correction_button"),
      ) {
        Text("Approve & Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMappingScreen(
  viewModel: SmartDetectionViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val mappingRules by viewModel.mappingRules.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  var isAddDialogOpen by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Account Mapping Rules", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    floatingActionButton = {
      Button(
        onClick = { isAddDialogOpen = true },
        colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
        modifier = Modifier.testTag("add_mapping_rule_button"),
      ) {
        Text("+ Add Mapping Rule")
      }
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("account_mapping_screen"),
      contentPadding = PaddingValues(OceanSpacing.md),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
          Text(
            text = "When an incoming notification contains an account hint (such as 'ending in 1234'), the Smart Detection Engine maps it directly to the designated account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (mappingRules.isEmpty()) {
        item {
          OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
            Text(
              text = "No custom mapping rules configured. Automatic detection uses account last-4 numbers configured on each account.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(mappingRules, key = { it.id }) { rule ->
          val mappedAccount = accounts.firstOrNull { it.id == rule.accountId }
          OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Hint Pattern: '${rule.matchPattern}'", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Maps to: ${mappedAccount?.name ?: "Unknown Account"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
              }
              IconButton(onClick = { viewModel.deleteMappingRule(rule.id) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete Rule", tint = OceanTheme.colors.negative)
              }
            }
          }
        }
      }
    }
  }

  if (isAddDialogOpen) {
    var pattern by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var selectedAccId by remember { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
      onDismissRequest = { isAddDialogOpen = false },
      title = { Text("Add Mapping Rule") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
          OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = { Text("Match Pattern (e.g. 1234)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = hint,
            onValueChange = { hint = it },
            label = { Text("Bank Name / Hint (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text("Target Account", style = MaterialTheme.typography.labelMedium)
          accounts.forEach { acc ->
            Row(
              modifier = Modifier.fillMaxWidth().clickable { selectedAccId = acc.id }.padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                shape = CircleShape,
                color = if (selectedAccId == acc.id) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp),
              ) {}
              Spacer(modifier = Modifier.width(8.dp))
              Text("${acc.name} (${acc.currencyCode})", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (pattern.isNotBlank() && selectedAccId.isNotBlank()) {
              viewModel.addMappingRule(selectedAccId, pattern, hint)
              isAddDialogOpen = false
            }
          },
          enabled = pattern.isNotBlank() && selectedAccId.isNotBlank(),
        ) {
          Text("Save Rule")
        }
      },
      dismissButton = {
        TextButton(onClick = { isAddDialogOpen = false }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionHistoryScreen(
  viewModel: SmartDetectionViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val events by viewModel.detectionEvents.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Detection History (${events.size})", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (events.isNotEmpty()) {
            IconButton(onClick = { viewModel.clearHistory() }, modifier = Modifier.testTag("clear_detection_history_btn")) {
              Icon(Icons.Rounded.Delete, contentDescription = "Clear History", tint = OceanTheme.colors.negative)
            }
          }
        },
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    if (events.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("detection_history_empty"),
        contentAlignment = Alignment.Center,
      ) {
        Text("No detection events recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("detection_history_list"),
        contentPadding = PaddingValues(OceanSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        items(events, key = { it.id }) { event ->
          val formattedAmount = FormatMoneyUseCase().format(event.detectedMoney)
          val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
          val dateStr = remember(event.receivedAt) { dateFormat.format(Date(event.receivedAt)) }

          OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(event.detectedMerchant ?: event.senderOrTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("From: ${event.rawPackageName} • $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Text(formattedAmount, style = OceanNumericStyle, color = OceanTheme.colors.gold)
            }
            Spacer(modifier = Modifier.height(OceanSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
              OceanStatusBadge(
                text = event.status.name,
                tone = when (event.status) {
                  DetectionEventStatus.AUTO_SAVED, DetectionEventStatus.APPROVED -> BadgeTone.POSITIVE
                  DetectionEventStatus.IN_REVIEW -> BadgeTone.WARNING
                  DetectionEventStatus.DISMISSED -> BadgeTone.INFO
                  DetectionEventStatus.DUPLICATE -> BadgeTone.NEGATIVE
                },
              )
              Text("Score: ${(event.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
          }
        }
      }
    }
  }
}
