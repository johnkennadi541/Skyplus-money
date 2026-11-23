package com.example.domain.usecase

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.CurrencyFinancialReport
import com.example.domain.model.ExportedReportFile
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.Money
import com.example.domain.model.ReportExportFormat
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates sanitized, authentic PDF, CSV, and DOCX financial reports.
 *
 * Security hygiene:
 * - Never includes Firebase UIDs, SQLCipher keys, full account numbers, or raw internal IDs.
 * - Neutralizes CSV spreadsheet formula injection on text fields.
 */
@Singleton
class ReportExportGenerator @Inject constructor() {

  private val moneyFormatter = FormatMoneyUseCase()

  fun generateExport(
    snapshot: FinancialReportSnapshot,
    format: ReportExportFormat,
    generatedAtMillis: Long = snapshot.generatedAtMillis,
  ): ExportedReportFile {
    val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(generatedAtMillis))
    val fileName = "skyplus_money_report_${snapshot.window.periodType.name.lowercase()}_$fileStamp.${format.extension}"
    val bytes =
      when (format) {
        ReportExportFormat.CSV -> buildCsvBytes(snapshot, generatedAtMillis)
        ReportExportFormat.DOCX -> buildDocxBytes(snapshot, generatedAtMillis)
        ReportExportFormat.PDF -> buildPdfBytes(snapshot, generatedAtMillis)
      }
    return ExportedReportFile(
      format = format,
      fileName = fileName,
      mimeType = format.mimeType,
      bytes = bytes,
      generatedAtMillis = generatedAtMillis,
    )
  }

  fun buildEmailSummaryBody(
    snapshot: FinancialReportSnapshot,
    generatedAtMillis: Long = snapshot.generatedAtMillis,
  ): String {
    val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return buildString {
      appendLine("SKYPLUS MONEY — Personal Financial Report")
      appendLine("Period: ${snapshot.window.periodType.label} (${snapshot.window.label})")
      appendLine("Generated: ${dateTimeFmt.format(Date(generatedAtMillis))}")
      appendLine()
      if (snapshot.currencies.isEmpty()) {
        appendLine("No financial records found in this period.")
      } else {
        snapshot.currencies.forEach { currency ->
          val report = snapshot.reportsByCurrency[currency] ?: return@forEach
          appendLine("=== $currency Summary ===")
          appendLine("Total Income: ${fmt(report.incomeExpense.totalIncomeMinor, currency)}")
          appendLine("Net Expenses: ${fmt(report.incomeExpense.netExpensesMinor, currency)} (Gross ${fmt(report.incomeExpense.grossExpensesMinor, currency)}, Refunds ${fmt(report.incomeExpense.totalRefundsAndReversalsMinor, currency)})")
          appendLine("Net Savings: ${fmt(report.incomeExpense.netSavingsMinor, currency)} (${report.incomeExpense.savingsPercentage.toPlainString()}%)")
          appendLine("Cash & Bank Assets: ${fmt(report.netWorth.cashAndBankAssetsMinor, currency)}")
          appendLine("Investment Valuation: ${fmt(report.netWorth.investmentAssetsMinor, currency)}")
          appendLine("Total Liabilities: ${fmt(report.netWorth.totalLiabilitiesMinor, currency)}")
          appendLine("Net Worth: ${fmt(report.netWorth.netWorthMinor, currency)}")
          report.budgets?.let { b ->
            appendLine("Budgets: Allocated ${fmt(b.totalAllocatedMinor, currency)}, Spent ${fmt(b.totalSpentMinor, currency)}, Overspent Count ${b.overspentBudgetCount}")
          }
          report.loans?.let { l ->
            appendLine("Loans: Outstanding ${fmt(l.totalOutstandingMinor, currency)}, Period Interest Paid ${fmt(l.periodInterestPaidMinor, currency)}")
          }
          report.shopping?.let { s ->
            appendLine("Shopping: Estimated ${fmt(s.totalEstimatedMinor, currency)}, Period Actual ${fmt(s.actualSpentInPeriodMinor, currency)}")
          }
          appendLine()
        }
      }
      appendLine("Note: Sensitive identifiers, account numbers, and credentials are intentionally omitted.")
    }
  }

  internal fun buildCsvBytes(
    snapshot: FinancialReportSnapshot,
    generatedAtMillis: Long,
  ): ByteArray {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val sb = StringBuilder()

    fun appendRow(vararg cols: String) {
      sb.append(cols.joinToString(",") { csvEscape(it) }).append("\r\n")
    }

    appendRow("Section", "Currency", "MetricOrCategory", "PrimaryValue", "SecondaryValue", "NotesOrTimestamp")
    appendRow(
      "Metadata",
      "ALL",
      "Report Period",
      "${snapshot.window.periodType.label}: ${snapshot.window.label}",
      "Window ${dateFmt.format(Date(snapshot.window.startDateMillis))} to ${dateFmt.format(Date(snapshot.window.endDateMillis))}",
      "Generated ${dateFmt.format(Date(generatedAtMillis))}",
    )

    snapshot.currencies.forEach { currency ->
      val report = snapshot.reportsByCurrency[currency] ?: return@forEach
      val nw = report.netWorth
      val ie = report.incomeExpense

      appendRow("NetWorth", currency, "Cash & Bank Assets", fmt(nw.cashAndBankAssetsMinor, currency), "${nw.activeAccountCount} active accounts", "Separated from investments")
      appendRow("NetWorth", currency, "Investment Valuation", fmt(nw.investmentAssetsMinor, currency), "${nw.activeInvestmentCount} active holdings", "Latest recorded valuation")
      appendRow("NetWorth", currency, "Total Assets", fmt(nw.totalAssetsMinor, currency), "", "")
      appendRow("NetWorth", currency, "Loan & Card Liabilities", fmt(nw.totalLiabilitiesMinor, currency), "${nw.activeOrOverdueLoanCount} active/overdue loans", "")
      appendRow("NetWorth", currency, "Net Worth", fmt(nw.netWorthMinor, currency), "", "Assets minus Liabilities")

      appendRow("CashFlow", currency, "Total Income", fmt(ie.totalIncomeMinor, currency), "${ie.completedIncomeCount} txns", "")
      appendRow("CashFlow", currency, "Gross Expenses", fmt(ie.grossExpensesMinor, currency), "${ie.completedExpenseCount} txns", "")
      appendRow("CashFlow", currency, "Refunds & Reversals", fmt(ie.totalRefundsAndReversalsMinor, currency), "${ie.completedRefundCount} txns", "Offset against expenses")
      appendRow("CashFlow", currency, "Net Expenses", fmt(ie.netExpensesMinor, currency), "", "")
      appendRow("CashFlow", currency, "Net Savings", fmt(ie.netSavingsMinor, currency), "${ie.savingsPercentage.toPlainString()}%", "Transfers excluded: ${ie.excludedTransferCount}")

      ie.expenseByCategory.forEach { cat ->
        appendRow("ExpenseCategory", currency, sanitizeFormula(cat.category), fmt(cat.totalMinor, currency), "${cat.percentage.toPlainString()}%", "${cat.transactionCount} entries")
      }

      report.budgets?.let { b ->
        appendRow("Budgets", currency, "Budget Totals", fmt(b.totalSpentMinor, currency), "Allocated ${fmt(b.totalAllocatedMinor, currency)}", "Utilization ${b.utilizationPercent.toPlainString()}%")
        b.historicalPerformance.forEach { row ->
          appendRow("BudgetHistory", currency, sanitizeFormula(row.category), "Spent ${fmt(row.spentMinor, currency)}", "Allocated ${fmt(row.allocatedMinor, currency)}", "Overspent ${fmt(row.overspendingMinor, currency)}")
        }
      }

      report.loans?.let { l ->
        appendRow("Loans", currency, "Outstanding Principal", fmt(l.totalOutstandingMinor, currency), "Original ${fmt(l.totalPrincipalMinor, currency)}", "Overdue: ${l.overdueCount}")
        appendRow("Loans", currency, "Period EMI & Interest Paid", fmt(l.periodTotalEmiPaidMinor, currency), "Interest ${fmt(l.periodInterestPaidMinor, currency)}", "All-time interest ${fmt(l.allTimeInterestPaidMinor, currency)}")
      }

      report.investments?.let { inv ->
        val p = inv.portfolioSummary
        appendRow("Investments", currency, "Portfolio Summary", "Val ${fmt(p.totalValuationMinor, currency)}", "Invested ${fmt(p.totalInvestedMinor, currency)}", "Unrealized ${fmt(p.unrealizedPnlMinor, currency)} | Realized ${fmt(p.realizedPnlMinor, currency)}")
        inv.holdingRows.forEach { h ->
          appendRow(
            "InvestmentHolding",
            currency,
            sanitizeFormula("${h.name} (${h.category.displayName})"),
            fmt(h.valuationMinor, currency),
            "Invested ${fmt(h.investedMinor, currency)}",
            "Unrealized ${fmt(h.unrealizedPnlMinor, currency)} | Realized ${fmt(h.realizedPnlMinor, currency)} | Return ${h.returnPercent.toPlainString()}% | Price as of ${if (h.lastPriceUpdateDate > 0L) dateFmt.format(Date(h.lastPriceUpdateDate)) else "Awaiting first price"} | Stale ${h.isStalePrice}",
          )
        }
      }

      report.shopping?.let { shop ->
        appendRow("Shopping", currency, "Shopping Overview", "Actual ${fmt(shop.actualSpentInPeriodMinor, currency)}", "Estimated ${fmt(shop.totalEstimatedMinor, currency)}", "Savings/Variance ${fmt(shop.savingsOrOverspendMinor, currency)}")
        shop.storeBreakdown.forEach { st ->
          appendRow("ShoppingStore", currency, sanitizeFormula(st.storeName), fmt(st.actualSpentMinor, currency), "${st.purchasedItemCount} items", "Variance ${fmt(st.savingsOrOverspendMinor, currency)}")
        }
      }

      ie.transactionsInRange.forEach { txn ->
        appendRow(
          "Transaction",
          currency,
          sanitizeFormula("${txn.type.name} - ${txn.category}"),
          fmt(txn.amountMinor, currency),
          sanitizeFormula(txn.merchantOrPayee ?: ""),
          dateFmt.format(Date(txn.transactionDate)),
        )
      }
    }

    return sb.toString().toByteArray(StandardCharsets.UTF_8)
  }

  internal fun buildDocxBytes(
    snapshot: FinancialReportSnapshot,
    generatedAtMillis: Long,
  ): ByteArray {
    val lines = buildStructuredReportLines(snapshot, generatedAtMillis)
    val contentTypesXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    val relsXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    val paragraphsXml =
      lines.joinToString("\n") { line ->
        val boldTag = if (line.isHeading) "<w:rPr><w:b/></w:rPr>" else ""
        """    <w:p><w:r>$boldTag<w:t xml:space="preserve">${xmlEscape(line.text)}</w:t></w:r></w:p>"""
      }

    val documentXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
$paragraphsXml
    <w:sectPr/>
  </w:body>
</w:document>"""

    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      writeZipEntry(zos, "[Content_Types].xml", contentTypesXml)
      writeZipEntry(zos, "_rels/.rels", relsXml)
      writeZipEntry(zos, "word/document.xml", documentXml)
    }
    return baos.toByteArray()
  }

  internal fun buildPdfBytes(
    snapshot: FinancialReportSnapshot,
    generatedAtMillis: Long,
  ): ByteArray {
    val lines = buildStructuredReportLines(snapshot, generatedAtMillis)
    val nativePdf =
      runCatching {
        renderWithAndroidPdfDocument(lines)
      }.getOrNull()
    if (nativePdf != null && nativePdf.size > 16) {
      return nativePdf
    }
    return renderPurePdf14(lines)
  }

  private fun renderWithAndroidPdfDocument(lines: List<ReportLine>): ByteArray {
    val pageWidth = 595
    val pageHeight = 842
    val marginX = 40f
    val topStartY = 52f
    val maxY = pageHeight - 48f

    val document = PdfDocument()
    val headerBgPaint = Paint().apply { color = Color.parseColor("#1E3A8A") }
    val headerTextPaint =
      Paint().apply {
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
      }
    val headingPaint =
      Paint().apply {
        color = Color.parseColor("#1E3A8A")
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
      }
    val bodyPaint =
      Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = 10f
        isAntiAlias = true
      }
    val footerPaint =
      Paint().apply {
        color = Color.parseColor("#64748B")
        textSize = 8.5f
        isAntiAlias = true
      }

    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas: Canvas = page.canvas

    fun drawPageBanner(targetCanvas: Canvas, isFirstPage: Boolean) {
      targetCanvas.drawRect(0f, 0f, pageWidth.toFloat(), 44f, headerBgPaint)
      val bannerTitle = if (isFirstPage) "SKYPLUS MONEY — Financial Report" else "SKYPLUS MONEY — Financial Report (cont.)"
      targetCanvas.drawText(bannerTitle, marginX, 28f, headerTextPaint)
    }

    drawPageBanner(canvas, isFirstPage = true)
    var y = topStartY + 12f

    for (line in lines) {
      val lineHeight = if (line.isHeading) 20f else 15f
      if (y + lineHeight > maxY) {
        canvas.drawText("Page $pageNumber • Confidential Personal Finance Export", marginX, pageHeight - 20f, footerPaint)
        document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        drawPageBanner(canvas, isFirstPage = false)
        y = topStartY + 12f
      }
      if (line.text.isBlank()) {
        y += 8f
      } else {
        val paint = if (line.isHeading) headingPaint else bodyPaint
        val safeText = line.text.take(96)
        canvas.drawText(safeText, marginX, y, paint)
        y += lineHeight
      }
    }

    canvas.drawText("Page $pageNumber • Confidential Personal Finance Export", marginX, pageHeight - 20f, footerPaint)
    document.finishPage(page)

    val out = ByteArrayOutputStream()
    try {
      document.writeTo(out)
    } finally {
      document.close()
    }
    return out.toByteArray()
  }

  private fun renderPurePdf14(lines: List<ReportLine>): ByteArray {
    // Paginate into A4 (595x842 pt) pages with real PDF 1.4 catalog, font, content streams & xref table.
    val pagesContent = mutableListOf<String>()
    val currentStream = StringBuilder()
    var y = 780

    fun flushPage() {
      if (currentStream.isNotEmpty()) {
        pagesContent += currentStream.toString()
        currentStream.clear()
      }
      y = 780
    }

    fun startNewStream() {
      currentStream.append("0.118 0.227 0.541 rg 0 802 595 40 re f\n")
      currentStream.append("BT /F2 14 Tf 1 1 1 rg 40 816 Td (SKYPLUS MONEY - Financial Report) Tj ET\n")
      currentStream.append("0 0 0 rg\n")
      y = 776
    }

    startNewStream()
    for (line in lines) {
      val step = if (line.isHeading) 18 else 14
      if (y - step < 48) {
        flushPage()
        startNewStream()
      }
      if (line.text.isBlank()) {
        y -= 8
      } else {
        val font = if (line.isHeading) "/F2 11 Tf" else "/F1 9.5 Tf"
        val escaped = pdfTextEscape(line.text.take(96))
        currentStream.append("BT $font 40 $y Td ($escaped) Tj ET\n")
        y -= step
      }
    }
    flushPage()

    // Objects: 1=Catalog, 2=Pages, 3=Font F1 (Helvetica), 4=Font F2 (Helvetica-Bold), then per-page (Page obj + Content obj)
    val objects = mutableListOf<String>()
    val pageObjIds = mutableListOf<Int>()
    var nextId = 5
    pagesContent.forEach { _ ->
      pageObjIds += nextId
      nextId += 2
    }
    val kidsRefs = pageObjIds.joinToString(" ") { "$it 0 R" }

    objects += "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
    objects += "2 0 obj\n<< /Type /Pages /Kids [$kidsRefs] /Count ${pageObjIds.size} >>\nendobj\n"
    objects += "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
    objects += "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n"

    pagesContent.forEachIndexed { idx, streamBody ->
      val pageId = pageObjIds[idx]
      val contentId = pageId + 1
      val streamBytesLen = streamBody.toByteArray(StandardCharsets.ISO_8859_1).size
      objects += "$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
      objects += "$contentId 0 obj\n<< /Length $streamBytesLen >>\nstream\n${streamBody}endstream\nendobj\n"
    }

    val pdf = ByteArrayOutputStream()
    val header = "%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII)
    pdf.write(header)

    val offsets = mutableListOf<Int>()
    objects.forEach { objStr ->
      offsets += pdf.size()
      pdf.write(objStr.toByteArray(StandardCharsets.ISO_8859_1))
    }

    val xrefOffset = pdf.size()
    val xrefSb = StringBuilder()
    xrefSb.append("xref\n0 ${objects.size + 1}\n")
    xrefSb.append("0000000000 65535 f \n")
    offsets.forEach { off ->
      xrefSb.append(String.format(Locale.US, "%010d 00000 n \n", off))
    }
    xrefSb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
    pdf.write(xrefSb.toString().toByteArray(StandardCharsets.US_ASCII))
    return pdf.toByteArray()
  }

  private fun pdfTextEscape(raw: String): String {
    val asciiSafe =
      buildString {
        raw.forEach { ch ->
          val mapped = if (ch.code in 32..126) ch else '-'
          when (mapped) {
            '\\' -> append("\\\\")
            '(' -> append("\\(")
            ')' -> append("\\)")
            else -> append(mapped)
          }
        }
      }
    return asciiSafe
  }

  private data class ReportLine(
    val text: String,
    val isHeading: Boolean = false,
  )

  private fun buildStructuredReportLines(
    snapshot: FinancialReportSnapshot,
    generatedAtMillis: Long,
  ): List<ReportLine> {
    val dtFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val lines = mutableListOf<ReportLine>()

    lines += ReportLine("SKYPLUS MONEY — Comprehensive Financial Report", isHeading = true)
    lines += ReportLine("Date Range: ${snapshot.window.periodType.label} (${snapshot.window.label})")
    lines += ReportLine("Window: ${dFmt.format(Date(snapshot.window.startDateMillis))} to ${dFmt.format(Date(snapshot.window.endDateMillis))}")
    lines += ReportLine("Generated At: ${dtFmt.format(Date(generatedAtMillis))}")
    lines += ReportLine("")

    if (snapshot.currencies.isEmpty()) {
      lines += ReportLine("No financial records available for this user/window.")
      return lines
    }

    snapshot.currencies.forEach { currency ->
      val report: CurrencyFinancialReport = snapshot.reportsByCurrency[currency] ?: return@forEach
      val nw = report.netWorth
      val ie = report.incomeExpense

      lines += ReportLine("Currency Portfolio: $currency", isHeading = true)
      lines += ReportLine("Net Worth Summary", isHeading = true)
      lines += ReportLine("  Cash & Bank Balances (${nw.activeAccountCount} accounts): ${fmt(nw.cashAndBankAssetsMinor, currency)}")
      lines += ReportLine("  Investment Valuation (${nw.activeInvestmentCount} active): ${fmt(nw.investmentAssetsMinor, currency)}")
      lines += ReportLine("  Total Assets: ${fmt(nw.totalAssetsMinor, currency)}")
      lines += ReportLine("  Total Liabilities (Loans + Card/Overdraft): ${fmt(nw.totalLiabilitiesMinor, currency)}")
      lines += ReportLine("  Net Worth: ${fmt(nw.netWorthMinor, currency)}")
      lines += ReportLine("")

      lines += ReportLine("Income & Expense Analysis ($currency)", isHeading = true)
      lines += ReportLine("  Total Income (${ie.completedIncomeCount} entries): ${fmt(ie.totalIncomeMinor, currency)}")
      lines += ReportLine("  Gross Expenses (${ie.completedExpenseCount} entries): ${fmt(ie.grossExpensesMinor, currency)}")
      lines += ReportLine("  Refunds & Reversals (${ie.completedRefundCount} entries): ${fmt(ie.totalRefundsAndReversalsMinor, currency)}")
      lines += ReportLine("  Net Expenses: ${fmt(ie.netExpensesMinor, currency)}")
      lines += ReportLine("  Net Savings: ${fmt(ie.netSavingsMinor, currency)} (${ie.savingsPercentage.toPlainString()}%)")
      lines += ReportLine("  Excluded Transfers: ${ie.excludedTransferCount} • Non-Completed: ${ie.excludedNonCompletedCount}")

      if (ie.expenseByCategory.isNotEmpty()) {
        lines += ReportLine("  Category Breakdown:")
        ie.expenseByCategory.forEach { cat ->
          lines += ReportLine("    • ${cat.category}: ${fmt(cat.totalMinor, currency)} (${cat.percentage.toPlainString()}%)")
        }
      }
      lines += ReportLine("")

      report.budgets?.let { b ->
        lines += ReportLine("Budget Performance ($currency)", isHeading = true)
        lines += ReportLine("  Allocated: ${fmt(b.totalAllocatedMinor, currency)} | Spent: ${fmt(b.totalSpentMinor, currency)} | Remaining: ${fmt(b.totalRemainingMinor, currency)}")
        lines += ReportLine("  Overspent Total: ${fmt(b.totalOverspendingMinor, currency)} (${b.overspentBudgetCount} overspent budgets)")
        lines += ReportLine("")
      }

      report.loans?.let { l ->
        lines += ReportLine("Loans & EMI Summary ($currency)", isHeading = true)
        lines += ReportLine("  Original Principal: ${fmt(l.totalPrincipalMinor, currency)} | Outstanding: ${fmt(l.totalOutstandingMinor, currency)}")
        lines += ReportLine("  Period EMI Paid: ${fmt(l.periodTotalEmiPaidMinor, currency)} | Period Interest Paid: ${fmt(l.periodInterestPaidMinor, currency)}")
        lines += ReportLine("  Active: ${l.activeCount}, Overdue: ${l.overdueCount}, Completed: ${l.completedCount}")
        lines += ReportLine("")
      }

      report.investments?.let { inv ->
        val p = inv.portfolioSummary
        lines += ReportLine("Investments Report ($currency • India Policy Compliant)", isHeading = true)
        lines += ReportLine("  Invested Capital: ${fmt(p.totalInvestedMinor, currency)} | Valuation: ${fmt(p.totalValuationMinor, currency)}")
        lines += ReportLine("  Unrealized P&L: ${fmt(p.unrealizedPnlMinor, currency)} | Realized P&L: ${fmt(p.realizedPnlMinor, currency)}")
        inv.holdingRows.take(12).forEach { h ->
          lines += ReportLine(
            "    • ${h.name} [${h.category.displayName}]: Val ${fmt(h.valuationMinor, currency)} (Cost ${fmt(h.investedMinor, currency)}) | Unrealized ${fmt(h.unrealizedPnlMinor, currency)} | Realized ${fmt(h.realizedPnlMinor, currency)} | Return ${h.returnPercent.toPlainString()}% | Price as of ${if (h.lastPriceUpdateDate > 0L) dFmt.format(Date(h.lastPriceUpdateDate)) else "Awaiting first price"} | Stale ${h.isStalePrice}",
          )
        }
        lines += ReportLine("")
      }

      report.shopping?.let { shop ->
        lines += ReportLine("Shopping Report ($currency)", isHeading = true)
        lines += ReportLine("  Estimated Total: ${fmt(shop.totalEstimatedMinor, currency)} | Actual Spent: ${fmt(shop.actualSpentInPeriodMinor, currency)}")
        lines += ReportLine("  Savings / Variance: ${fmt(shop.savingsOrOverspendMinor, currency)} (${shop.purchasedItemsInPeriod} purchased)")
        lines += ReportLine("")
      }

      if (ie.transactionsInRange.isNotEmpty()) {
        lines += ReportLine("Completed Transactions in Window ($currency)", isHeading = true)
        ie.transactionsInRange.take(30).forEach { txn ->
          val payee = txn.merchantOrPayee?.takeIf { it.isNotBlank() } ?: txn.category
          lines += ReportLine("  • ${dFmt.format(Date(txn.transactionDate))} | ${txn.type.name} | ${txn.category} | $payee | ${fmt(txn.amountMinor, currency)}")
        }
        lines += ReportLine("")
      }
    }

    return lines
  }

  private fun fmt(minorUnits: Long, currencyCode: String): String =
    moneyFormatter.format(Money.fromMinorUnits(minorUnits, currencyCode))

  private fun writeZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
    zos.putNextEntry(ZipEntry(entryName))
    zos.write(content.toByteArray(StandardCharsets.UTF_8))
    zos.closeEntry()
  }

  private fun csvEscape(raw: String): String {
    val clean = raw.replace("\r", " ").replace("\n", " ")
    val needsQuotes = clean.contains(',') || clean.contains('"')
    return if (needsQuotes) "\"${clean.replace("\"", "\"\"")}\"" else clean
  }

  private fun sanitizeFormula(text: String): String {
    if (text.isEmpty()) return text
    val first = text.first()
    return if (first == '=' || first == '+' || first == '-' || first == '@') "'$text" else text
  }

  private fun xmlEscape(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
