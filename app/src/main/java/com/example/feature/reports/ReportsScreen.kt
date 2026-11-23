package com.example.feature.reports

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.EmailReportSettings
import com.example.domain.model.DailyAutoEntryReportSettings
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.ReportCategoryTab
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportExportFormat
import com.example.domain.model.ReportFrequency
import com.example.domain.model.ReportPeriodType

@Composable
fun ReportsScreen(
  viewModel: ReportsViewModel,
  onNavigateBack: (() -> Unit)? = null,
  defaultRecipientEmail: String = "",
  modifier: Modifier = Modifier,
) {
  val snapshot by viewModel.reportSnapshot.collectAsStateWithLifecycle()
  val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
  val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
  val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
  val emailSettings by viewModel.emailSettings.collectAsStateWithLifecycle()
  val dailyAutoEntrySettings by viewModel.dailyAutoEntrySettings.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val emailPreview by viewModel.emailPreview.collectAsStateWithLifecycle()
  val pendingSafExport by viewModel.pendingSafExport.collectAsStateWithLifecycle()
  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { granted ->
        if (granted) {
          viewModel.setDailyAutoEntryReportSettings(dailyAutoEntrySettings.copy(enabled = true))
        }
      },
    )

  val context = LocalContext.current

  val onToggleDailyAutoEntryReport: (Boolean) -> Unit = { enabled ->
    if (
      enabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.setDailyAutoEntryReportSettings(dailyAutoEntrySettings.copy(enabled = enabled))
    }
  }

  val createDocumentLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("*/*"),
      onResult = { uri ->
        viewModel.writePendingExportToUri(context, uri)
      },
    )

  LaunchedEffect(pendingSafExport) {
    val export = pendingSafExport ?: return@LaunchedEffect
    createDocumentLauncher.launch(export.fileName)
  }

  ReportsContent(
    snapshot = snapshot,
    selectedPeriod = selectedPeriod,
    selectedTab = selectedTab,
    selectedCurrency = selectedCurrency,
    emailSettings = emailSettings,
    dailyAutoEntrySettings = dailyAutoEntrySettings,
    defaultRecipientEmail = defaultRecipientEmail,
    userMessage = userMessage,
    emailPreview = emailPreview,
    onDismissMessage = viewModel::dismissMessage,
    onSelectPeriod = viewModel::selectPeriod,
    onSetCustomDateRange = viewModel::setCustomDateRange,
    onSelectTab = viewModel::selectTab,
    onSelectCurrency = viewModel::selectCurrency,
    onSaveExport = viewModel::prepareExportForSave,
    onShareExport = { format -> viewModel.shareExportReport(context, format) },
    onToggleEmailEnabled = viewModel::setEmailReportsEnabled,
    onSetDailyAutoEntrySettings = viewModel::setDailyAutoEntryReportSettings,
    onToggleDailyAutoEntryReport = onToggleDailyAutoEntryReport,
    onSelectFrequency = viewModel::setEmailFrequency,
    onSaveRecipient = viewModel::saveRecipientEmail,
    onOpenEmailPreview = viewModel::openEmailPreview,
    onDismissEmailPreview = viewModel::dismissEmailPreview,
    onConfirmSendEmail = { viewModel.confirmAndLaunchEmailComposer(context) },
    onNavigateBack = onNavigateBack,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsContent(
  snapshot: FinancialReportSnapshot,
  selectedPeriod: ReportPeriodType = ReportPeriodType.MONTHLY,
  selectedTab: ReportCategoryTab = ReportCategoryTab.OVERVIEW,
  selectedCurrency: String? = null,
  emailSettings: EmailReportSettings = EmailReportSettings(),
  dailyAutoEntrySettings: DailyAutoEntryReportSettings = DailyAutoEntryReportSettings(),
  defaultRecipientEmail: String = "",
  userMessage: String? = null,
  emailPreview: PreparedEmailPreview? = null,
  onDismissMessage: () -> Unit = {},
  onSelectPeriod: (ReportPeriodType) -> Unit = {},
  onSetCustomDateRange: (Long?, Long?) -> Unit = { _, _ -> },
  onSelectTab: (ReportCategoryTab) -> Unit = {},
  onSelectCurrency: (String) -> Unit = {},
  onSaveExport: (ReportExportFormat) -> Unit = {},
  onShareExport: (ReportExportFormat) -> Unit = {},
  onToggleEmailEnabled: (Boolean) -> Unit = {},
  onSetDailyAutoEntrySettings: (DailyAutoEntryReportSettings) -> Unit = {},
  onToggleDailyAutoEntryReport: (Boolean) -> Unit = {},
  onSelectFrequency: (ReportFrequency) -> Unit = {},
  onSaveRecipient: (String) -> Unit = {},
  onOpenEmailPreview: (String) -> Unit = {},
  onDismissEmailPreview: () -> Unit = {},
  onConfirmSendEmail: () -> Unit = {},
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  if (onNavigateBack != null) {
    BackHandler(onBack = onNavigateBack)
  }

  val snackbarHostState = remember { SnackbarHostState() }
  var showDateRangePicker by remember { mutableStateOf(false) }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it)
      onDismissMessage()
    }
  }

  val activeCurrency =
    selectedCurrency?.takeIf { it in snapshot.currencies }
      ?: snapshot.currencies.firstOrNull()
  val activeReport = activeCurrency?.let { snapshot.reportsByCurrency[it] }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("reports_screen"),
      contentPadding = PaddingValues(OceanSpacing.md),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (onNavigateBack != null) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("reports_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_navigate_back),
              )
            }
          }
          OceanSectionHeader(
            title = stringResource(R.string.reports_title),
            subtitle = "${snapshot.window.periodType.label} • ${snapshot.window.label}",
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Period Filter Bar
      item {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .widthIn(max = 760.dp)
              .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          listOf(
            ReportPeriodType.DAILY,
            ReportPeriodType.WEEKLY,
            ReportPeriodType.MONTHLY,
            ReportPeriodType.YEARLY,
          ).forEach { period ->
            FilterChip(
              selected = selectedPeriod == period,
              onClick = { onSelectPeriod(period) },
              label = { Text(period.label) },
              modifier = Modifier.testTag("report_period_${period.name.lowercase()}"),
            )
          }
          FilterChip(
            selected = selectedPeriod == ReportPeriodType.CUSTOM,
            onClick = { showDateRangePicker = true },
            leadingIcon = {
              Icon(
                Icons.Rounded.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text(ReportPeriodType.CUSTOM.label) },
            modifier = Modifier.testTag("report_period_custom"),
          )
        }
      }

      // Currency selector chips when multi-currency data exists
      if (snapshot.currencies.size > 1) {
        item {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            snapshot.currencies.forEach { currency ->
              FilterChip(
                selected = currency == activeCurrency,
                onClick = { onSelectCurrency(currency) },
                label = { Text(currency) },
                modifier = Modifier.testTag("report_currency_${currency.lowercase()}"),
              )
            }
          }
        }
      }

      // Daily automatic-entry summary scheduling
      item {
        DailyAutoEntryReportCard(
          settings = dailyAutoEntrySettings,
          onSettingsChanged = onSetDailyAutoEntrySettings,
          onToggleEnabled = onToggleDailyAutoEntryReport,
        )
      }

      // Category Tabs
      item {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .widthIn(max = 760.dp)
              .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          ReportCategoryTab.entries.forEach { tab ->
            FilterChip(
              selected = selectedTab == tab,
              onClick = { onSelectTab(tab) },
              label = { Text(tab.label) },
              modifier = Modifier.testTag("report_tab_${tab.name.lowercase()}"),
            )
          }
        }
      }

      // Main Section Body
      item {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
          if (selectedTab == ReportCategoryTab.EXPORTS_EMAIL) {
            ExportsAndEmailSection(
              emailSettings = emailSettings,
              defaultRecipientFallback = defaultRecipientEmail,
              onSaveExport = onSaveExport,
              onShareExport = onShareExport,
              onToggleEmailEnabled = onToggleEmailEnabled,
              onSelectFrequency = onSelectFrequency,
              onSaveRecipient = onSaveRecipient,
              onOpenEmailPreview = onOpenEmailPreview,
            )
          } else if (activeReport == null) {
            OceanCard(modifier = Modifier.testTag("reports_no_data_card")) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(OceanSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Assessment,
                  contentDescription = null,
                  tint = OceanTheme.colors.gold,
                  modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.height(OceanSpacing.sm))
                Text(
                  text = "No Financial Data Recorded Yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(OceanSpacing.xs))
                Text(
                  text = "Add accounts, transactions, budgets, loans, investments, or shopping lists to generate live multi-currency analytics.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            when (selectedTab) {
              ReportCategoryTab.OVERVIEW ->
                OverviewReportSection(report = activeReport, windowLabel = snapshot.window.label)
              ReportCategoryTab.INCOME_EXPENSE ->
                IncomeExpenseReportSection(report = activeReport.incomeExpense)
              ReportCategoryTab.BUDGETS ->
                BudgetReportSection(report = activeReport.budgets, currencyCode = activeReport.currencyCode)
              ReportCategoryTab.LOANS_EMI ->
                LoansEmiReportSection(report = activeReport.loans, currencyCode = activeReport.currencyCode)
              ReportCategoryTab.INVESTMENTS ->
                InvestmentsReportSection(report = activeReport.investments, currencyCode = activeReport.currencyCode)
              ReportCategoryTab.SHOPPING ->
                ShoppingReportSection(report = activeReport.shopping, currencyCode = activeReport.currencyCode)
              ReportCategoryTab.EXPORTS_EMAIL -> Unit
            }
          }
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(OceanSpacing.md),
    )
  }

  if (showDateRangePicker) {
    val pickerState = rememberDateRangePickerState()
    DatePickerDialog(
      onDismissRequest = { showDateRangePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            onSetCustomDateRange(
              pickerState.selectedStartDateMillis,
              pickerState.selectedEndDateMillis?.let { it + 86_399_999L },
            )
            showDateRangePicker = false
          },
          modifier = Modifier.testTag("confirm_custom_report_range_button"),
        ) {
          Text("Apply")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
      },
    ) {
      DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
    }
  }

  emailPreview?.let { preview ->
    AlertDialog(
      onDismissRequest = onDismissEmailPreview,
      title = { Text("Preview Report Before Sending") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          Text(
            text = "Recipient: ${preview.recipientEmail.ifBlank { "(Select in your email app)" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = "Subject: ${preview.subject}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Attachment: ${preview.attachmentFile.fileName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = preview.bodyPreview,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = onConfirmSendEmail,
          colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
          modifier = Modifier.testTag("confirm_launch_email_button"),
        ) {
          Text("Open Email App", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissEmailPreview, modifier = Modifier.testTag("cancel_email_preview_button")) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun DailyAutoEntryReportCard(
  settings: DailyAutoEntryReportSettings,
  onSettingsChanged: (DailyAutoEntryReportSettings) -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
) {
  val context = LocalContext.current

  OceanCard(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).testTag("daily_auto_entry_report_card")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      Icon(
        imageVector = Icons.Rounded.NotificationsActive,
        contentDescription = null,
        tint = OceanTheme.colors.gold,
        modifier = Modifier.size(28.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text("Daily Auto-Entry Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
          "One notification each day with automatic income, expenses, duplicates and items needing attention.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = settings.enabled,
        onCheckedChange = onToggleEnabled,
        modifier = Modifier.testTag("daily_auto_entry_report_switch"),
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Report time", style = MaterialTheme.typography.labelLarge)
        Text("Uses your phone's local time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Button(
        onClick = {
          TimePickerDialog(
            context,
            { _, hour, minute ->
              onSettingsChanged(settings.copy(hourOfDay = hour, minute = minute))
            },
            settings.hourOfDay,
            settings.minute,
            false,
          ).show()
        },
        modifier = Modifier.testTag("daily_auto_entry_report_time_button"),
      ) {
        Text(
          String.format(java.util.Locale.getDefault(), "%02d:%02d", settings.hourOfDay, settings.minute),
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

fun emptyReportSnapshot(): FinancialReportSnapshot =
  FinancialReportSnapshot(
    window =
      ReportDateWindow(
        periodType = ReportPeriodType.MONTHLY,
        startDateMillis = 0L,
        endDateMillis = 0L,
        label = "Current Month",
      ),
    generatedAtMillis = 0L,
    currencies = emptyList(),
    reportsByCurrency = emptyMap(),
  )
