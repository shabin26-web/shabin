package sms2mm.core

import kotlinx.serialization.Serializable
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One row of a Money Manager "Export to Excel" file. */
data class MmRow(
    val date: LocalDateTime,
    val account: String,
    val category: String,
    val subcategory: String,
    val note: String,
    /** "Expense", "Income", "Transfer-Out", ... */
    val kind: String,
    /** Amount in the account's own currency. */
    val amount: BigDecimal?,
    val currency: String,
)

/**
 * Reads a Money Manager .xlsx export without any spreadsheet library: an .xlsx is a zip,
 * and only `xl/sharedStrings.xml` and the first sheet are needed. Columns are found by
 * their header names, so the order does not matter.
 */
object MmExport {

    fun read(input: InputStream): List<MmRow> = read(input.readBytes())

    fun read(bytes: ByteArray): List<MmRow> {
        val files = unzip(bytes) { it == "xl/sharedStrings.xml" || it.startsWith("xl/worksheets/sheet") }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it.decodeToString()) } ?: emptyList()
        val sheet = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull()
            ?: throw IllegalArgumentException("Not a Money Manager export: no worksheet found")
        return rows(files.getValue(sheet).decodeToString(), shared)
    }

    /**
     * Minimal zip reader using the central directory. Money Manager writes zips whose local
     * headers say size 0 (sizes only in the central directory), which java.util.zip.ZipInputStream rejects.
     */
    internal fun unzip(bytes: ByteArray, want: (String) -> Boolean): Map<String, ByteArray> {
        fun u16(i: Int) = (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8)
        fun u32(i: Int) = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
        var eocd = bytes.size - 22
        while (eocd >= 0 && u32(eocd) != 0x06054b50L) eocd--
        require(eocd >= 0) { "Not a valid .xlsx file" }
        var p = u32(eocd + 16).toInt()
        val out = LinkedHashMap<String, ByteArray>()
        repeat(u16(eocd + 10)) {
            require(u32(p) == 0x02014b50L) { "Damaged .xlsx file" }
            val method = u16(p + 10)
            val compSize = u32(p + 20).toInt()
            val size = u32(p + 24).toInt()
            val nameLen = u16(p + 28)
            val name = String(bytes, p + 46, nameLen, Charsets.UTF_8)
            val local = u32(p + 42).toInt()
            p += 46 + nameLen + u16(p + 30) + u16(p + 32)
            if (!want(name)) return@repeat
            val start = local + 30 + u16(local + 26) + u16(local + 28)
            out[name] = when (method) {
                0 -> bytes.copyOfRange(start, start + compSize)
                8 -> java.util.zip.Inflater(true).run {
                    setInput(bytes, start, compSize)
                    val buf = ByteArray(size)
                    var n = 0
                    while (n < size && !finished()) n += inflate(buf, n, size - n).also { if (it == 0 && needsInput()) error("Damaged .xlsx file") }
                    end()
                    buf
                }
                else -> error("Unsupported compression in .xlsx")
            }
        }
        return out
    }

    private val SI = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val T = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
    private val ROW = Regex("""<row[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("""<c r="([A-Z]+)\d+"([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
    private val V = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val TYPE = Regex("""t="([^"]+)"""")

    private fun sharedStrings(xml: String): List<String> =
        SI.findAll(xml).map { si -> T.findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) } }.toList()

    private fun rows(xml: String, shared: List<String>): List<MmRow> {
        var header: Map<String, String>? = null // column letter -> header name
        val out = ArrayList<MmRow>()
        for (row in ROW.findAll(xml)) {
            val cells = LinkedHashMap<String, String>()
            for (c in CELL.findAll(row.groupValues[1])) {
                val col = c.groupValues[1]
                val type = TYPE.find(c.groupValues[2])?.groupValues?.get(1)
                val body = c.groupValues[3]
                val value = when (type) {
                    "s" -> V.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let(shared::getOrNull)
                    "inlineStr" -> T.findAll(body).joinToString("") { unescape(it.groupValues[1]) }
                    else -> V.find(body)?.groupValues?.get(1)?.let(::unescape)
                } ?: ""
                cells[col] = value
            }
            if (header == null) {
                // First occurrence wins: Money Manager repeats "Account" as the last column.
                val h = LinkedHashMap<String, String>()
                cells.forEach { (col, name) -> if (name.trim() !in h.values) h[col] = name.trim() }
                header = h
                continue
            }
            val byName = header.entries.associate { (col, name) -> name to (cells[col] ?: "") }
            val date = parseDate(byName["Date"].orEmpty()) ?: continue
            out += MmRow(
                date = date,
                account = byName["Account"].orEmpty().trim(),
                category = byName["Category"].orEmpty().trim(),
                subcategory = byName["Subcategory"].orEmpty().trim(),
                note = byName["Note"].orEmpty().trim(),
                kind = byName["Income/Expense"].orEmpty().trim(),
                amount = (byName["Amount"]?.takeIf { it.isNotBlank() } ?: byName.entries.firstOrNull { it.key.length == 3 && it.key.all(Char::isUpperCase) }?.value)
                    ?.replace(",", "")?.trim()?.toBigDecimalOrNull(),
                currency = byName["Currency"].orEmpty().trim(),
            )
        }
        return out
    }

    /** Excel serial date (days since 1899-12-30) or an ISO date-time string. */
    internal fun parseDate(raw: String): LocalDateTime? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { serial ->
            val millis = Math.round(serial * 86_400_000.0)
            return LocalDateTime.of(1899, 12, 30, 0, 0).plus(millis, ChronoUnit.MILLIS)
        }
        return try { LocalDateTime.parse(s.replace(' ', 'T').take(19)) } catch (_: Exception) { null }
    }

    private fun unescape(s: String): String = s
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        .replace(Regex("""&#(\d+);""")) { String(Character.toChars(it.groupValues[1].toInt())) }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}

/** Money Manager accounts and categories, most-used first, for the pickers. */
@Serializable
data class Catalog(
    val accounts: List<String> = emptyList(),
    val expense: Map<String, List<String>> = emptyMap(),
    val income: Map<String, List<String>> = emptyMap(),
    val updated: String? = null,
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && expense.isEmpty() && income.isEmpty()

    fun categories(type: TxnType): Map<String, List<String>> = if (type == TxnType.INCOME) income else expense

    companion object {
        private val SKIP = setOf("modified bal.", "modified bal")

        fun from(rows: List<MmRow>, updated: String? = null): Catalog {
            val accounts = rows.groupingBy { it.account }.eachCount().filterKeys { it.isNotBlank() }
            fun cats(kind: String): Map<String, List<String>> {
                val picked = rows.filter { it.kind.equals(kind, true) && it.category.isNotBlank() && it.category.lowercase() !in SKIP }
                val byCat = picked.groupBy { it.category }
                return byCat.entries.sortedByDescending { it.value.size }.associate { (cat, list) ->
                    cat to list.filter { it.subcategory.isNotBlank() }.groupingBy { it.subcategory }.eachCount()
                        .entries.sortedByDescending { it.value }.map { it.key }
                }
            }
            return Catalog(
                accounts = accounts.entries.sortedByDescending { it.value }.map { it.key },
                expense = cats("Expense"),
                income = cats("Income"),
                updated = updated,
            )
        }
    }
}

/** Marks captured transactions that already appear in a Money Manager export. */
object Reconciler {
    data class Candidate(val id: String, val account: String, val amount: BigDecimal, val at: LocalDateTime)

    /** Ids of [pending] found in [rows]: same account, same amount, within ±1 day. Each row is used once. */
    fun match(pending: List<Candidate>, rows: List<MmRow>): Set<String> {
        val used = HashSet<Int>()
        val matched = HashSet<String>()
        for (p in pending.sortedBy { it.at }) {
            val i = rows.indices.firstOrNull { i ->
                val r = rows[i]
                i !in used && r.amount != null && r.amount.compareTo(p.amount) == 0 &&
                    sameAccount(r.account, p.account) &&
                    kotlin.math.abs(ChronoUnit.MINUTES.between(r.date, p.at)) <= 24 * 60
            } ?: continue
            used += i
            matched += p.id
        }
        return matched
    }

    /** Ignore case, emoji and extra spaces: "SNB 💸" == "snb". */
    fun sameAccount(a: String, b: String) = key(a) == key(b)

    private fun key(s: String) = s.filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex("""\s+"""), " ").lowercase()
}
