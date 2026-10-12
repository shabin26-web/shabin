package sms2mm.app

import android.content.Context
import sms2mm.core.Catalog
import sms2mm.core.ExportRow
import sms2mm.core.MmExport
import sms2mm.core.Reconciler
import sms2mm.core.TxnType
import sms2mm.core.XlsxWriter
import java.time.LocalDate

/** Setup → "Import Money Manager export": refresh the pick lists and tick off what's already recorded. */
object ExportImport {

    /** Runs off the main thread (a few seconds for years of data). Returns a one-line summary. */
    fun import(ctx: Context, bytes: ByteArray): String {
        val rows = MmExport.read(bytes)
        require(rows.isNotEmpty()) { "No transactions found in this file" }
        val catalog = Catalog.from(rows, updated = LocalDate.now().toString())
        Store.updateRules(ctx) { it.copy(catalog = catalog) }

        val pending = Store.txns(ctx).filter { it.status.isOpen }
        val matched = Reconciler.match(
            pending.map { Reconciler.Candidate(it.id, it.account, it.amountValue, it.time) },
            rows,
        )
        pending.filter { it.id in matched }.forEach { t ->
            Store.update(ctx, t.id) { it.copy(status = Status.ENTERED, failReason = null) }
            Notifier.cancel(ctx, t)
        }
        val categories = catalog.expense.size + catalog.income.size
        return "${catalog.accounts.size} accounts · $categories categories loaded" +
            if (pending.isEmpty()) "" else " · ${matched.size} of ${pending.size} pending found in Money Manager → Recorded"
    }

    /** Setup → "Export pending (.xlsx)": open items only, oldest first, same fields the app stores. */
    fun exportPending(ctx: Context): Pair<ByteArray, Int> {
        val open = Store.txns(ctx).filter { it.status.isOpen }.sortedBy { it.time }
        val rows = open.map { t ->
            val transfer = t.type == TxnType.TRANSFER
            ExportRow(
                date = t.time,
                kind = t.type.name.lowercase().replaceFirstChar { it.uppercase() },
                account = t.account,
                category = if (transfer) "→ ${t.toAccount.orEmpty()}" else if (t.needsReview) "" else t.category.name,
                subcategory = if (transfer || t.needsReview) "" else t.category.subcategory.orEmpty(),
                note = listOfNotNull(t.note ?: t.merchant, t.description).joinToString(" · "),
                amount = t.amountValue,
                currency = t.currency,
                status = "Pending",
                flag = listOfNotNull(
                    "Possible duplicate".takeIf { t.duplicateOf != null },
                    "Pick a category".takeIf { t.needsReview && !transfer },
                ).joinToString(", "),
            )
        }
        return XlsxWriter.write(rows) to rows.size
    }
}
