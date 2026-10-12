package sms2mm.core

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One captured transaction as it goes into the Excel export (parsed fields only, never SMS text). */
data class ExportRow(
    val date: LocalDateTime,
    /** "Expense", "Income" or "Transfer". */
    val kind: String,
    val account: String,
    val category: String,
    val subcategory: String,
    val note: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    /** e.g. "Possible duplicate"; rows with a flag are highlighted amber. */
    val flag: String = "",
)

/**
 * Writes a small, formal .xlsx without a spreadsheet library: Arial, bold header, frozen top row,
 * filter, live SUMIF totals per currency and a row-count check, and conditional formatting that
 * highlights flagged rows. Headers match Money Manager's export, so [MmExport.read] reads it back.
 */
object XlsxWriter {

    val HEADERS = listOf("Date", "Income/Expense", "Account", "Category", "Subcategory", "Note", "Amount", "Currency", "Status", "Flag")
    private val WIDTHS = listOf(17, 15, 22, 22, 22, 26, 13, 10, 11, 20)

    fun write(rows: List<ExportRow>, sheetName: String = "Pending"): ByteArray {
        val last = rows.size + 1 // last data row (header is row 1)
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sheet.append("<cols>")
        WIDTHS.forEachIndexed { i, w -> sheet.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sheet.append("</cols><sheetData>")

        sheet.append("""<row r="1">""")
        HEADERS.forEachIndexed { i, h -> sheet.append(str(col(i) + 1, h, STYLE_HEADER)) }
        sheet.append("</row>")

        rows.forEachIndexed { i, r ->
            val n = i + 2
            sheet.append("""<row r="$n">""")
            sheet.append(num("A$n", serial(r.date), STYLE_DATE))
            sheet.append(str("B$n", r.kind))
            sheet.append(str("C$n", r.account))
            sheet.append(str("D$n", r.category))
            sheet.append(str("E$n", r.subcategory))
            sheet.append(str("F$n", r.note))
            sheet.append(num("G$n", r.amount.toPlainString(), STYLE_AMOUNT))
            sheet.append(str("H$n", r.currency))
            sheet.append(str("I$n", r.status))
            sheet.append(str("J$n", r.flag))
            sheet.append("</row>")
        }

        // Totals block: live formulas, with cached values so viewers that don't recalculate still show them.
        var n = last + 2
        val currencies = rows.map { it.currency }.distinct().sorted()
        for (c in currencies) {
            val cached = rows.filter { it.currency == c }.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
            sheet.append("""<row r="$n">""")
            sheet.append(str("F$n", "Total $c", STYLE_TOTAL_LABEL))
            sheet.append(formula("G$n", "SUMIF(\$H\$2:\$H\$$last,\"${esc(c)}\",\$G\$2:\$G\$$last)", cached.toPlainString(), STYLE_TOTAL_AMOUNT))
            sheet.append(str("H$n", c, STYLE_TOTAL_LABEL))
            sheet.append("</row>")
            n++
        }
        sheet.append("""<row r="$n">""")
        sheet.append(str("F$n", "Rows", STYLE_TOTAL_LABEL))
        sheet.append(formula("G$n", "COUNTA(\$G\$2:\$G\$$last)", rows.size.toString(), STYLE_TOTAL_COUNT))
        sheet.append("</row>")
        n++
        // Tie-out: total rows across currencies must equal the row count (0 = OK).
        sheet.append("""<row r="$n">""")
        sheet.append(str("F$n", "Check (should be 0)", STYLE_TOTAL_LABEL))
        val countParts = currencies.joinToString("+") { "COUNTIF(\$H\$2:\$H\$$last,\"${esc(it)}\")" }.ifEmpty { "0" }
        sheet.append(formula("G$n", "COUNTA(\$G\$2:\$G\$$last)-($countParts)", "0", STYLE_TOTAL_COUNT))
        sheet.append("</row>")

        sheet.append("</sheetData>")
        if (rows.isNotEmpty()) {
            sheet.append("""<autoFilter ref="A1:J$last"/>""")
            sheet.append("""<conditionalFormatting sqref="A2:J$last"><cfRule type="expression" dxfId="0" priority="1"><formula>LEN(${'$'}J2)&gt;0</formula></cfRule></conditionalFormatting>""")
        }
        sheet.append("</worksheet>")

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheetName, last))
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
        return out.toByteArray()
    }

    /** Excel serial date: days since 1899-12-30, time as a fraction of a day. */
    internal fun serial(t: LocalDateTime): String {
        val minutes = Duration.between(LocalDateTime.of(1899, 12, 30, 0, 0), t.withSecond(0).withNano(0)).toMinutes()
        return BigDecimal(minutes).divide(BigDecimal(1440), 10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun col(i: Int) = ('A' + i).toString()
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun str(ref: String, v: String, style: Int = STYLE_TEXT) =
        """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${esc(v)}</t></is></c>"""
    private fun num(ref: String, v: String, style: Int) = """<c r="$ref" s="$style"><v>$v</v></c>"""
    private fun formula(ref: String, f: String, cached: String, style: Int) = """<c r="$ref" s="$style"><f>$f</f><v>$cached</v></c>"""

    private const val STYLE_TEXT = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_DATE = 2
    private const val STYLE_AMOUNT = 3
    private const val STYLE_TOTAL_LABEL = 4
    private const val STYLE_TOTAL_AMOUNT = 5
    private const val STYLE_TOTAL_COUNT = 6

    private fun workbook(name: String, last: Int) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="${esc(name)}" sheetId="1" r:id="rId1"/></sheets>""" +
        (if (last > 1) """<definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">'${esc(name)}'!${'$'}A${'$'}1:${'$'}J${'$'}$last</definedName></definedNames>""" else "") +
        """<calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>"""

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        """</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        """</Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
        """</Relationships>"""

    // Fonts: 0 Arial 10, 1 Arial 10 bold. Fills: 0 none, 1 gray125 (required), 2 light-grey header.
    // Borders: 0 none, 1 thin bottom, 2 thin top. dxf 0: amber fill for flagged rows.
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<numFmts count="2"><numFmt numFmtId="164" formatCode="dd/mm/yyyy hh:mm"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts>""" +
        """<fonts count="2"><font><sz val="10"/><name val="Arial"/><family val="2"/></font><font><b/><sz val="10"/><name val="Arial"/><family val="2"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFD9E1F2"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="3"><border><left/><right/><top/><bottom/><diagonal/></border>""" +
        """<border><left/><right/><top/><bottom style="thin"><color rgb="FF808080"/></bottom><diagonal/></border>""" +
        """<border><left/><right/><top style="thin"><color rgb="FF808080"/></top><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="7">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="2" xfId="0" applyFont="1" applyBorder="1"/>""" +
        """<xf numFmtId="165" fontId="1" fillId="0" borderId="2" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1"/>""" +
        """<xf numFmtId="1" fontId="1" fillId="0" borderId="2" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """<dxfs count="1"><dxf><font><color rgb="FF9C5700"/></font><fill><patternFill patternType="solid"><bgColor rgb="FFFFEB9C"/></patternFill></fill></dxf></dxfs>""" +
        """</styleSheet>"""
}

/** Possible duplicates: same amount, currency and merchant within a short window (e.g. a double charge). */
object Duplicates {
    data class Key(val id: String, val amount: BigDecimal, val currency: String, val merchant: String?, val at: LocalDateTime)

    private fun norm(s: String?) = s.orEmpty().uppercase().filter(Char::isLetterOrDigit)

    /** The id of an earlier transaction that [new] probably duplicates, or null. */
    fun find(new: Key, existing: List<Key>, window: Duration = Duration.ofMinutes(10)): String? =
        existing.firstOrNull {
            it.id != new.id &&
                it.amount.compareTo(new.amount) == 0 &&
                it.currency.equals(new.currency, ignoreCase = true) &&
                norm(it.merchant) == norm(new.merchant) &&
                Duration.between(it.at, new.at).abs() <= window
        }?.id
}
