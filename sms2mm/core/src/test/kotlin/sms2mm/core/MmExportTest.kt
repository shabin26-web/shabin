package sms2mm.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MmExportTest {

    /** A minimal .xlsx shaped like Money Manager's export (shared strings, serial dates, repeated "Account"). */
    private fun xlsx(rows: List<List<Any>>): ByteArray {
        val strings = LinkedHashMap<String, Int>()
        fun s(v: String) = strings.getOrPut(v) { strings.size }
        val sheet = StringBuilder("<worksheet><sheetData>")
        rows.forEachIndexed { r, row ->
            sheet.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, v ->
                val ref = ('A' + c).toString() + (r + 1)
                if (v is Number) sheet.append("<c r=\"$ref\" s=\"2\" t=\"n\"><v>$v</v></c>")
                else sheet.append("<c r=\"$ref\" t=\"s\"><v>${s(v.toString())}</v></c>")
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        val sst = strings.keys.joinToString("", "<sst>", "</sst>") {
            "<si><t xml:space=\"preserve\">" + it.replace("&", "&amp;").replace("<", "&lt;") + "</t></si>"
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(sst.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toString().toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    private val header = listOf("Date", "Account", "Category", "Subcategory", "Note", "SAR", "Income/Expense", "Description", "Amount", "Currency", "Account")

    // 46302.9447... = 2026-10-07 22:40 (Excel serial)
    private val sample = xlsx(
        listOf(
            header,
            listOf(46302.94444444, "STC Pay 💳", "🏘️ Household", "Home Stationery", "Lulu", 25.42, "Expense", "", "25.42", "SAR", 25.42),
            listOf(46302.5, "SNB 💸", "🍜 Food & Dining", "Restaurant Bills", "Al baik", 13.5, "Expense", "", "13.5", "SAR", 13.5),
            listOf(46301.5, "SNB 💸", "🍜 Food & Dining", "Snacks & Beverages", "Tea", 2, "Expense", "", "2.0", "SAR", 2),
            listOf(46300.5, "SNB 💸", "🍜 Food & Dining", "Restaurant Bills", "", 9, "Expense", "", "9.0", "SAR", 9),
            listOf(46299.5, "Federal Bank ", "Salary", "Basic Salary", "", 100, "Income", "", "2000.0", "INR", 100),
            listOf(46298.5, "SNB 💸", "Modified Bal.", "", "", 1, "Expense", "", "1.0", "SAR", 1),
            listOf(46297.5, "SNB 💸", "SR Cash 💷", "", "", 300, "Transfer-Out", "", "300.0", "SAR", 300),
        ),
    )

    @Test
    fun `reads rows by header name with shared strings and serial dates`() {
        val rows = MmExport.read(ByteArrayInputStream(sample))
        assertEquals(7, rows.size)
        val lulu = rows.first()
        assertEquals("STC Pay 💳", lulu.account)
        assertEquals("🏘️ Household", lulu.category)
        assertEquals("Home Stationery", lulu.subcategory)
        assertEquals("Lulu", lulu.note)
        assertEquals("Expense", lulu.kind)
        assertEquals(BigDecimal("25.42"), lulu.amount)
        assertEquals(LocalDateTime.of(2026, 10, 7, 22, 40), lulu.date.withSecond(0).withNano(0))
        assertEquals("Federal Bank", rows[4].account) // trimmed
        assertEquals(BigDecimal("2000.0"), rows[4].amount) // account currency, not the SAR column
    }

    @Test
    fun `catalog lists accounts and categories most-used first, skipping balance adjustments`() {
        val cat = Catalog.from(MmExport.read(ByteArrayInputStream(sample)))
        assertEquals("SNB 💸", cat.accounts.first())
        assertTrue("Federal Bank" in cat.accounts)
        assertEquals(listOf("🍜 Food & Dining", "🏘️ Household"), cat.expense.keys.toList())
        assertEquals(listOf("Restaurant Bills", "Snacks & Beverages"), cat.expense["🍜 Food & Dining"])
        assertEquals(mapOf("Salary" to listOf("Basic Salary")), cat.income)
        assertTrue("Modified Bal." !in cat.expense)
        assertTrue("SR Cash 💷" !in cat.expense) // transfers are not categories
    }

    @Test
    fun `reconcile matches account, amount and date within a day, each row once`() {
        val rows = MmExport.read(ByteArrayInputStream(sample))
        val at = LocalDateTime.of(2026, 10, 7, 22, 42)
        val ids = Reconciler.match(
            listOf(
                Reconciler.Candidate("lulu", "STC Pay 💳", BigDecimal("25.42"), at),
                Reconciler.Candidate("lulu-again", "STC Pay", BigDecimal("25.42"), at), // same row cannot match twice
                Reconciler.Candidate("wrong-amount", "SNB 💸", BigDecimal("13.00"), at),
                Reconciler.Candidate("wrong-account", "D360", BigDecimal("13.50"), at),
                Reconciler.Candidate("too-old", "SNB 💸", BigDecimal("13.50"), at.plusDays(3)),
                Reconciler.Candidate("albaik", "snb", BigDecimal("13.50"), LocalDateTime.of(2026, 10, 7, 9, 0)),
            ),
            rows,
        )
        assertEquals(setOf("lulu", "albaik"), ids)
    }

    @Test
    fun `merge keeps my rules and adds new ones`() {
        val food = Category("🍜 Food & Dining", "Restaurant Bills")
        val mine = UserRules(
            banks = listOf(BankSetup(KnownBanks.Bank.SNB, listOf("SNB-A"))),
            accounts = mapOf("1111" to "SNB 💸"),
            keywords = listOf(KeywordRule(listOf("LULU"), Category("🏘️ Household", "Home Stationery"))),
        )
        val file = UserRules(
            banks = listOf(BankSetup(KnownBanks.Bank.SNB, listOf("SNB-B")), BankSetup(KnownBanks.Bank.D360, listOf("D360"))),
            accounts = mapOf("1111" to "OTHER", "2222" to "D360"),
            keywords = listOf(KeywordRule(listOf("lulu"), food), KeywordRule(listOf("AL BAIK"), food)),
            catalog = Catalog(accounts = listOf("SNB 💸")),
        )
        val merged = mine.merge(file)
        assertEquals(listOf("SNB-A", "SNB-B"), merged.banks.first { it.bank == KnownBanks.Bank.SNB }.senders)
        assertTrue(merged.banks.any { it.bank == KnownBanks.Bank.D360 })
        assertEquals("SNB 💸", merged.accounts["1111"]) // mine wins
        assertEquals("D360", merged.accounts["2222"])
        assertEquals(2, merged.keywords.size) // LULU kept as mine, AL BAIK added
        assertEquals("🏘️ Household", merged.keywords.first().category.name)
        assertEquals(listOf("SNB 💸"), merged.catalog.accounts)
    }

    @Test
    fun `old rules files without a catalog still load`() {
        val loaded = UserRules.fromJson("""{"keywords":[{"keywords":["LULU"],"category":{"name":"X"}}]}""")
        assertTrue(loaded.catalog.isEmpty)
        assertEquals(loaded, UserRules.fromJson(loaded.toJson()))
    }
}
