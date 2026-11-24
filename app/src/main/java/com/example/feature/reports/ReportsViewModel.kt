package com.example.feature.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.data.local.ReportPreferencesDataSource
import com.example.domain.model.AuthSessionState
import com.example.domain.model.EmailReportSettings
import com.example.domain.model.DailyAutoEntryReportSettings
import com.example.domain.model.ExportedReportFile
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.ReportCategoryTab
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportExportFormat
import com.example.domain.model.ReportFrequency
import com.example.domain.model.ReportPeriodType
import com.example.domain.repository.AuthRepository
import com.example.worker.DailyAutoEntryReportScheduler
import com.example.domain.usecase.ObserveFinancialReportsUseCase
import com.example.domain.usecase.ReportExportGenerator
import com.example.domain.usecase.ReportsCalculationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PreparedEmailPreview(
  val recipientEmail: String,
  val subject: String,
  val bodyPreview: String,
  val attachmentFile: ExportedReportFile,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val observeFinancialReportsUseCase: ObserveFinancialReportsUseCase,
  private val calculationEngine: ReportsCalculationEngine,
  private val exportGenerator: ReportExportGenerator,
  private val reportPreferencesDataSource: ReportPreferencesDataSource,
  private val dailyAutoEntryReportScheduler: DailyAutoEntryReportScheduler,
  private val dispatchers: DispatcherProvider,
) : ViewModel() {

  private val _selectedPeriod = MutableStateFlow(ReportPeriodType.MONTHLY)
  val selectedPeriod: StateFlow<ReportPeriodType> = _selectedPeriod.asStateFlow()

  private val _customStartMillis = MutableStateFlow<Long?>(null)
  val customStartMillis: StateFlow<Long?> = _customStartMillis.asStateFlow()

  private val _customEndMillis = MutableStateFlow<Long?>(null)
  val customEndMillis: StateFlow<Long?> = _customEndMillis.asStateFlow()

  private val _selectedTab = MutableStateFlow(ReportCategoryTab.OVERVIEW)
  val selectedTab: StateFlow<ReportCategoryTab> = _selectedTab.asStateFlow()

  private val _selectedCurrency = MutableStateFlow<String?>(null)
  val selectedCurrency: StateFlow<String?> = _selectedCurrency.asStateFlow()

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  private val _emailPreview = MutableStateFlow<PreparedEmailPreview?>(null)
  val emailPreview: StateFlow<PreparedEmailPreview?> = _emailPreview.asStateFlow()

  private val _pendingSafExport = MutableStateFlow<ExportedReportFile?>(null)
  val pendingSafExport: StateFlow<ExportedReportFile?> = _pendingSafExport.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect { session ->
          _selectedPeriod.value = ReportPeriodType.MONTHLY
          _customStartMillis.value = null
          _customEndMillis.value = null
          _selectedTab.value = ReportCategoryTab.OVERVIEW
          _selectedCurrency.value = null
          _userMessage.value = null
          _isErrorMessage.value = false
          _emailPreview.value = null
          _pendingSafExport.value = null

          val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
          if (!uid.isNullOrBlank()) {
            val dailySettings = reportPreferencesDataSource.observeDailyAutoEntrySettings(uid).first()
            dailyAutoEntryReportScheduler.updateSchedule(uid, dailySettings)
          }
        }
    }
  }

  private val dateWindowFlow: StateFlow<ReportDateWindow> =
    combine(_selectedPeriod, _customStartMillis, _customEndMillis) { period, start, end ->
      calculationEngine.buildDateWindow(
        periodType = period,
        customStartMillis = start,
        customEndMillis = end,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = calculationEngine.buildDateWindow(ReportPeriodType.MONTHLY),
    )

  @OptIn(ExperimentalCoroutinesApi::class)
  val reportSnapshot: StateFlow<FinancialReportSnapshot> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) {
          observeFinancialReportsUseCase(uid, dateWindowFlow)
        } else {
          flowOf(
            FinancialReportSnapshot(
              window = dateWindowFlow.value,
              generatedAtMillis = System.currentTimeMillis(),
              currencies = emptyList(),
              reportsByCurrency = emptyMap(),
            )
          )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue =
          FinancialReportSnapshot(
            window = dateWindowFlow.value,
            generatedAtMillis = System.currentTimeMillis(),
            currencies = emptyList(),
            reportsByCurrency = emptyMap(),
          ),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val emailSettings: StateFlow<EmailReportSettings> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) reportPreferencesDataSource.observeSettings(uid) else flowOf(EmailReportSettings())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EmailReportSettings(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val dailyAutoEntrySettings: StateFlow<DailyAutoEntryReportSettings> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) reportPreferencesDataSource.observeDailyAutoEntrySettings(uid)
        else flowOf(DailyAutoEntryReportSettings())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyAutoEntryReportSettings(),
      )

  fun setDailyAutoEntryReportSettings(settings: DailyAutoEntryReportSettings) {
    viewModelScope.launch {
      val uid = currentAuthenticatedUserId() ?: return@launch
      reportPreferencesDataSource.setDailyAutoEntrySettings(uid, settings)
      dailyAutoEntryReportScheduler.updateSchedule(uid, settings)
      _isErrorMessage.value = false
      _userMessage.value =
        if (settings.enabled) "Daily auto-entry report scheduled for ${settings.formattedTime}."
        else "Daily auto-entry report disabled."
    }
  }

  fun selectPeriod(periodType: ReportPeriodType) {
    _selectedPeriod.value = periodType
  }

  fun setCustomDateRange(startMillis: Long?, endMillis: Long?) {
    if (startMillis != null && endMillis != null) {
      _customStartMillis.value = minOf(startMillis, endMillis)
      _customEndMillis.value = maxOf(startMillis, endMillis)
      _selectedPeriod.value = ReportPeriodType.CUSTOM
    }
  }

  fun selectTab(tab: ReportCategoryTab) {
    _selectedTab.value = tab
  }

  fun selectCurrency(currencyCode: String) {
    _selectedCurrency.value = currencyCode
  }

  private suspend fun currentAuthenticatedUserId(): String? =
    (authRepository.observeAuthSession().first() as? AuthSessionState.Authenticated)?.user?.uid

  fun setEmailReportsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val uid = currentAuthenticatedUserId() ?: return@launch
      reportPreferencesDataSource.setEnabled(uid, enabled)
    }
  }

  fun setEmailFrequency(frequency: ReportFrequency) {
    viewModelScope.launch {
      val uid = currentAuthenticatedUserId() ?: return@launch
      reportPreferencesDataSource.setFrequency(uid, frequency)
    }
  }

  fun saveRecipientEmail(emailInput: String) {
    val trimmed = emailInput.trim()
    if (trimmed.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
      _isErrorMessage.value = true
      _userMessage.value = "Enter a valid recipient email address."
      return
    }
    viewModelScope.launch {
      val uid = currentAuthenticatedUserId() ?: return@launch
      reportPreferencesDataSource.setRecipientEmail(uid, trimmed)
      _isErrorMessage.value = false
      _userMessage.value =
        if (trimmed.isEmpty()) "Recipient email cleared."
        else "Email recipient saved."
    }
  }

  fun prepareExportForSave(format: ReportExportFormat) {
    viewModelScope.launch {
      runCatching {
        withContext(dispatchers.default) {
          exportGenerator.generateExport(reportSnapshot.value, format)
        }
      }.onSuccess { exported ->
        _pendingSafExport.value = exported
      }.onFailure { err ->
        _isErrorMessage.value = true
        _userMessage.value = err.message ?: "Unable to prepare ${format.label} export."
      }
    }
  }

  fun writePendingExportToUri(context: Context, uri: Uri?) {
    val export = _pendingSafExport.value ?: return
    _pendingSafExport.value = null
    if (uri == null) return

    viewModelScope.launch {
      runCatching {
        withContext(dispatchers.io) {
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(export.bytes)
            stream.flush()
          } ?: throw IllegalStateException("Unable to open destination file.")
        }
      }.onSuccess {
        _isErrorMessage.value = false
        _userMessage.value = "${export.format.label} report saved (${export.fileName})."
      }.onFailure { err ->
        _isErrorMessage.value = true
        _userMessage.value = err.message ?: "Failed to save ${export.format.label} report."
      }
    }
  }

  fun shareExportReport(context: Context, format: ReportExportFormat) {
    viewModelScope.launch {
      runCatching {
        val exported =
          withContext(dispatchers.default) {
            exportGenerator.generateExport(reportSnapshot.value, format)
          }
        val shareUri = writeTempCacheFileAndGetUri(context, exported)
        val sendIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = exported.mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, "SKYPLUS MONEY — ${reportSnapshot.value.window.label} Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        val chooser = Intent.createChooser(sendIntent, "Share ${exported.format.label} Financial Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        exported
      }.onSuccess { exported ->
        _isErrorMessage.value = false
        _userMessage.value = "Prepared ${exported.format.label} share sheet."
      }.onFailure { err ->
        _isErrorMessage.value = true
        _userMessage.value = err.message ?: "Unable to share ${format.label} report."
      }
    }
  }

  fun openEmailPreview(recipientInput: String) {
    val recipient = recipientInput.trim()
    if (recipient.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
      _isErrorMessage.value = true
      _userMessage.value = "Enter a valid recipient email before previewing."
      return
    }
    viewModelScope.launch {
      runCatching {
        val snapshot = reportSnapshot.value
        withContext(dispatchers.default) {
          val pdfFile = exportGenerator.generateExport(snapshot, ReportExportFormat.PDF)
          val body = exportGenerator.buildEmailSummaryBody(snapshot)
          PreparedEmailPreview(
            recipientEmail = recipient,
            subject = "SKYPLUS MONEY ${emailSettings.value.frequency.label} Report — ${snapshot.window.label}",
            bodyPreview = body,
            attachmentFile = pdfFile,
          )
        }
      }.onSuccess { preview ->
        _emailPreview.value = preview
      }.onFailure { err ->
        _isErrorMessage.value = true
        _userMessage.value = err.message ?: "Unable to build email preview."
      }
    }
  }

  fun dismissEmailPreview() {
    _emailPreview.value = null
  }

  fun confirmAndLaunchEmailComposer(context: Context) {
    val preview = _emailPreview.value ?: return
    viewModelScope.launch {
      runCatching {
        val uid = currentAuthenticatedUserId()
        val shareUri = writeTempCacheFileAndGetUri(context, preview.attachmentFile)
        val intent =
          Intent(Intent.ACTION_SEND).apply {
            type = preview.attachmentFile.mimeType
            if (preview.recipientEmail.isNotBlank()) {
              putExtra(Intent.EXTRA_EMAIL, arrayOf(preview.recipientEmail))
            }
            putExtra(Intent.EXTRA_SUBJECT, preview.subject)
            putExtra(Intent.EXTRA_TEXT, preview.bodyPreview)
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        val chooser = Intent.createChooser(intent, "Send Financial Report via Email")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        if (uid != null) {
          reportPreferencesDataSource.recordPreparedAt(uid, System.currentTimeMillis())
        }
      }.onSuccess {
        _emailPreview.value = null
        _isErrorMessage.value = false
        _userMessage.value = "Email draft ready for your review and sending."
      }.onFailure { err ->
        _emailPreview.value = null
        _isErrorMessage.value = true
        _userMessage.value = err.message ?: "No email app available to compose report."
      }
    }
  }

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  private suspend fun writeTempCacheFileAndGetUri(
    context: Context,
    exported: ExportedReportFile,
  ): Uri =
    withContext(dispatchers.io) {
      val reportsDir = File(context.cacheDir, "reports")
      if (!reportsDir.exists()) {
        reportsDir.mkdirs()
      }
      val outFile = File(reportsDir, exported.fileName)
      outFile.writeBytes(exported.bytes)
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outFile,
      )
    }
}
