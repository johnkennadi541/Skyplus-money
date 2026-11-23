package com.example

import com.example.domain.model.AccountType
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportExportFormat
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.ReportExportGenerator
import com.example.domain.usecase.ReportsCalculationEngine
import com.example.domain.usecase.ShoppingCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportExportGeneratorTest {

  private val calcEngine =
    ReportsCalculationEngine(
      budgetEngine = BudgetCalculationEngine(),
      emiEngine = EmiCalculationEngine(),
      investmentEngine = InvestmentCalculationEngine(),
      shoppingEngine = ShoppingCalculationEngine(),
    )

  private val exportGenerator = ReportExportGenerator()

  private fun buildSampleSnapshot() =
    calcEngine.calculateSnapshot(
      window =
        ReportDateWindow(
          periodType = ReportPeriodType.MONTHLY,
          startDateMillis = 1_750_000_000_000L,
          endDateMillis = 1_752_500_000_000L,
          label = "July 2025",
        ),
      accounts =
        listOf(
          FinancialAccount(
            id = "acc_secret_id",
            userId = "secret_uid_999",
            name = "Primary Bank",
            type = AccountType.BANK,
            currencyCode = "INR",
            initialBalanceMinor = 200_000_00L,
            currentBalanceMinor = 250_000_00L,
            accountNumberLast4 = "9876",
          ),
        ),
      transactions =
        listOf(
          FinancialTransaction(
            id = "txn_1",
            userId = "secret_uid_999",
            accountId = "acc_secret_id",
            type = TransactionType.INCOME,
            amountMinor = 80_000_00L,
            currencyCode = "INR",
            category = "Salary",
            merchantOrPayee = "Employer Corp",
            transactionDate = 1_751_000_000_000L,
            status = TransactionStatus.COMPLETED,
          ),
          FinancialTransaction(
            id = "txn_2",
            userId = "secret_uid_999",
            accountId = "acc_secret_id",
            type = TransactionType.EXPENSE,
            amountMinor = 12_000_00L,
            currencyCode = "INR",
            category = "Utilities",
            merchantOrPayee = "=CMD_INJECTION",
            transactionDate = 1_751_200_000_000L,
            status = TransactionStatus.COMPLETED,
          ),
        ),
      budgets = emptyList(),
      loans = emptyList(),
      loanPayments = emptyList(),
      holdings =
        listOf(
          InvestmentHolding(
            id = "holding_report_test",
            userId = "secret_uid_999",
            name = "Index Fund",
            category = InvestmentCategory.MUTUAL_FUNDS,
            symbolOrTicker = "119598",
            currencyCode = "INR",
            purchaseDate = 1_750_000_000_000L,
            quantityDecimal = "10",
            totalInvestedMinor = 100_000L,
            totalBuyCostMinor = 100_000L,
            realizedPnlMinor = 5_000L,
            currentMarketPriceDecimal = "125.00",
            currentValuationMinor = 125_000L,
            lastPriceUpdateDate = 1_751_900_000_000L,
          ),
        ),
      investmentTransactions = emptyList(),
      investmentPriceHistory = emptyList(),
      shoppingLists = emptyList(),
      shoppingItems = emptyList(),
      nowMillis = 1_752_000_000_000L,
    )

  @Test
  fun `csv export contains financial summaries formula neutralization and no sensitive identifiers`() {
    val snapshot = buildSampleSnapshot()
    val csvExport = exportGenerator.generateExport(snapshot, ReportExportFormat.CSV)
    val csvText = String(csvExport.bytes, StandardCharsets.UTF_8)

    assertTrue(csvExport.fileName.endsWith(".csv"))
    assertEquals("text/csv", csvExport.mimeType)
    assertTrue(csvText.contains("SKYPLUS MONEY") || csvText.contains("July 2025"))
    assertTrue(csvText.contains("Cash & Bank Assets"))
    assertTrue(csvText.contains("'=CMD_INJECTION"))
    assertFalse("UID must never appear in CSV export", csvText.contains("secret_uid_999"))
    assertFalse("Account number must never appear in CSV export", csvText.contains("9876"))
  }

  @Test
  fun `investment exports include realized return and quote freshness details`() {
    val snapshot = buildSampleSnapshot()
    val csvText = String(exportGenerator.generateExport(snapshot, ReportExportFormat.CSV).bytes, StandardCharsets.UTF_8)
    assertTrue(csvText.contains("Realized"))
    assertTrue(csvText.contains("Return 30.00%"))
    assertTrue(csvText.contains("Price as of"))
    assertTrue(csvText.contains("Stale false"))

    val docx = exportGenerator.generateExport(snapshot, ReportExportFormat.DOCX)
    val entries = mutableMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(docx.bytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        entries[entry.name] = String(zis.readBytes(), StandardCharsets.UTF_8)
        entry = zis.nextEntry
      }
    }
    val documentXml = entries.getValue("word/document.xml")
    assertTrue(documentXml.contains("Realized"))
    assertTrue(documentXml.contains("Price as of"))
  }

  @Test
  fun `docx export creates valid OOXML zip package with document xml and no UIDs`() {
    val snapshot = buildSampleSnapshot()
    val docxExport = exportGenerator.generateExport(snapshot, ReportExportFormat.DOCX)

    assertTrue(docxExport.fileName.endsWith(".docx"))
    assertTrue(docxExport.bytes.size > 200)
    // ZIP magic header 'P' 'K'
    assertEquals('P'.code.toByte(), docxExport.bytes[0])
    assertEquals('K'.code.toByte(), docxExport.bytes[1])

    val entries = mutableMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(docxExport.bytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        entries[entry.name] = String(zis.readBytes(), StandardCharsets.UTF_8)
        entry = zis.nextEntry
      }
    }

    assertTrue(entries.containsKey("[Content_Types].xml"))
    assertTrue(entries.containsKey("_rels/.rels"))
    assertTrue(entries.containsKey("word/document.xml"))
    val docXml = entries.getValue("word/document.xml")
    assertTrue(docXml.contains("SKYPLUS MONEY"))
    assertTrue(docXml.contains("Net Worth"))
    assertFalse("UID must never leak in DOCX", docXml.contains("secret_uid_999"))
  }

  @Test
  fun `pdf export generates valid PDF binary signature`() {
    val snapshot = buildSampleSnapshot()
    val pdfExport = exportGenerator.generateExport(snapshot, ReportExportFormat.PDF)

    assertTrue(pdfExport.fileName.endsWith(".pdf"))
    assertEquals("application/pdf", pdfExport.mimeType)
    assertTrue(pdfExport.bytes.size > 100)
    val header = String(pdfExport.bytes.copyOfRange(0, 5), StandardCharsets.US_ASCII)
    assertEquals("%PDF-", header)
  }

  @Test
  fun `email summary body formats period metrics without exposing UID or account numbers`() {
    val snapshot = buildSampleSnapshot()
    val body = exportGenerator.buildEmailSummaryBody(snapshot)

    assertTrue(body.contains("SKYPLUS MONEY"))
    assertTrue(body.contains("INR Summary"))
    assertTrue(body.contains("Net Worth"))
    assertFalse(body.contains("secret_uid_999"))
    assertFalse(body.contains("9876"))
  }
}
