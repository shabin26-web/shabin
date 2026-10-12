package sms2mm.core

import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XlsxWriterTest {

    private val rows = listOf(
        ExportRow(LocalDateTime.of(2026, 10, 7, 14, 5), "Expense", "Card A", "Household", "Stationery", "Shop & Co", BigDecimal("25.42"), "SAR", "Pending"),
        ExportRow(LocalDateTime.of(2026, 10, 8, 9, 30), "Expense", "Bank B", "Food", "", "Cafe", BigDecimal("13.50"), "SAR", "Pending", "Possible duplicate"),
        ExportRow(LocalDateTime.of(2026, 10, 9, 20, 0), "Expense", "Bank C", "Bills", "Phone", "Recharge", BigDecimal("1999"), "INR", "Pending"),
    )

    @Test fun `round-trips through the Money Manager reader`() {
        val back = MmExport.read(XlsxWriter.write(rows))
        assertEquals(3, back.size) // totals block has no date, so it is not read as data
        assertEquals(LocalDateTime.of(2026, 10, 7, 14, 5), back[0].date)
        assertEquals("Card A", back[0].account)
        assertEquals("Shop & Co", back[0].note)
        assertEquals(0, BigDecimal("13.50").compareTo(back[1].amount))
        assertEquals("INR", back[2].currency)
        // Tie-out: rows read back sum to the source rows, per currency.
        for (c in listOf("SAR", "INR")) {
            val src = rows.filter { it.currency == c }.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
            val got = back.filter { it.currency == c }.fold(BigDecimal.ZERO) { a, r -> a + r.amount!! }
            assertEquals(0, src.compareTo(got), c)
        }
    }

    @Test fun `has live totals, Arial and conditional formatting`() {
        val files = MmExport.unzip(XlsxWriter.write(rows)) { true }
        val sheet = files.getValue("xl/worksheets/sheet1.xml").decodeToString()
        assertTrue("<f>SUMIF(\$H\$2:\$H\$4,\"SAR\",\$G\$2:\$G\$4)</f><v>38.92</v>" in sheet)
        assertTrue("<f>SUMIF(\$H\$2:\$H\$4,\"INR\",\$G\$2:\$G\$4)</f><v>1999</v>" in sheet)
        assertTrue("<f>COUNTA(\$G\$2:\$G\$4)</f><v>3</v>" in sheet)
        assertTrue("COUNTA(\$G\$2:\$G\$4)-(COUNTIF(\$H\$2:\$H\$4,\"INR\")+COUNTIF(\$H\$2:\$H\$4,\"SAR\"))" in sheet)
        assertTrue("<conditionalFormatting sqref=\"A2:J4\">" in sheet)
        assertTrue("Arial" in files.getValue("xl/styles.xml").decodeToString())
        assertTrue("[Content_Types].xml" in files)
    }

    @Test fun `empty export still opens`() {
        assertEquals(0, MmExport.read(XlsxWriter.write(emptyList())).size)
    }

    @Test fun `serial dates`() {
        assertEquals("46302.5", XlsxWriter.serial(LocalDateTime.of(2026, 10, 7, 12, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 7, 14, 5), MmExport.parseDate(XlsxWriter.serial(LocalDateTime.of(2026, 10, 7, 14, 5))))
    }
}

class DuplicatesTest {
    private val t0 = LocalDateTime.of(2026, 10, 7, 14, 0)
    private fun k(id: String, amt: String, m: String?, minutes: Long, cur: String = "SAR") =
        Duplicates.Key(id, BigDecimal(amt), cur, m, t0.plusMinutes(minutes))

    @Test fun `same amount and merchant within window`() {
        assertEquals("a", Duplicates.find(k("b", "25.40", "LULU HYPER", 4), listOf(k("a", "25.4", "lulu  hyper", 0))))
    }

    @Test fun `outside window, other amount, other merchant or currency`() {
        val old = listOf(k("a", "25.40", "LULU", 0))
        assertNull(Duplicates.find(k("b", "25.40", "LULU", 11), old))
        assertNull(Duplicates.find(k("b", "25.41", "LULU", 2), old))
        assertNull(Duplicates.find(k("b", "25.40", "PANDA", 2), old))
        assertNull(Duplicates.find(k("b", "25.40", "LULU", 2, "INR"), old))
        assertNull(Duplicates.find(k("a", "25.40", "LULU", 2), old)) // itself
        assertEquals("a", Duplicates.find(k("b", "25.40", "LULU", 30), old, Duration.ofHours(1)))
    }
}
