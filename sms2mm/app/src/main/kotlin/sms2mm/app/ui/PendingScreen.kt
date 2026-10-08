package sms2mm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sms2mm.app.ActionReceiver
import sms2mm.app.Notifier
import sms2mm.app.PendingTxn
import sms2mm.app.SkipEvent
import sms2mm.app.Status
import sms2mm.app.Store
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
private val RED = Color(0xFFC62828)
private val GREEN = Color(0xFF2E7D32)

@Composable
fun PendingScreen(modifier: Modifier, onEdit: (String) -> Unit) {
    val context = ctx()
    val version = storeVersion()
    var showDone by rememberSaveable { mutableStateOf(false) }
    val all = remember(version) { Store.txns(context) }
    val skips = remember(version) { Store.skips(context) }
    val open = setOf(Status.PENDING, Status.FILLED, Status.FAILED)
    val shown = if (showDone) all else all.filter { it.status in open }

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TieOut(all, skips) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showDone, onClick = { showDone = false }, label = { Text("To add") })
                FilterChip(selected = showDone, onClick = { showDone = true }, label = { Text("All") })
            }
        }
        if (shown.isEmpty()) item { Text("Nothing waiting. New bank SMS will appear here and as notifications.", modifier = Modifier.padding(vertical = 24.dp)) }
        items(shown, key = { it.id }) { t -> TxnCard(t, onEdit) }
    }
}

/** This month, per currency: captured − entered − ignored = still to add (must reach 0). */
@Composable
private fun TieOut(all: List<PendingTxn>, skips: List<SkipEvent>) {
    val month = YearMonth.now()
    val inMonth = all.filter { YearMonth.from(it.time) == month }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Tie-out · ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", style = MaterialTheme.typography.titleMedium)
            if (inMonth.isEmpty()) Text("No transactions captured this month yet.")
            inMonth.groupBy { it.currency }.forEach { (currency, list) ->
                fun sum(f: (PendingTxn) -> Boolean) = list.filter(f).fold(BigDecimal.ZERO) { a, t -> a + t.amountValue }
                val captured = sum { true }
                val entered = sum { it.status == Status.ENTERED }
                val ignored = sum { it.status == Status.IGNORED }
                val outstanding = captured - entered - ignored
                Spacer(Modifier.padding(top = 6.dp))
                Text(currency, fontWeight = FontWeight.Bold)
                Line("Captured (${list.size})", captured)
                Line("Entered in Money Manager", entered)
                Line("Ignored", ignored)
                Line(
                    if (outstanding.signum() == 0) "Still to add — ties out ✔" else "Still to add (${list.count { it.status !in setOf(Status.ENTERED, Status.IGNORED) }})",
                    outstanding,
                    if (outstanding.signum() == 0) GREEN else RED,
                )
            }
            val monthSkips = skips.filter { YearMonth.from(LocalDateTime.parse(it.at)) == month }
            if (monthSkips.isNotEmpty()) {
                val c = monthSkips.groupingBy { it.kind }.eachCount()
                Text(
                    "Bank SMS skipped: ${c[SkipEvent.OTP] ?: 0} OTP (not stored), ${c[SkipEvent.IGNORED] ?: 0} ignored, ${c[SkipEvent.UNPARSED] ?: 0} not recognised",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((c[SkipEvent.UNPARSED] ?: 0) > 0) RED else Color.Unspecified,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Line(label: String, amount: BigDecimal, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = color)
        Text("%,.2f".format(amount), color = color, fontWeight = if (color != Color.Unspecified) FontWeight.Bold else null)
    }
}

@Composable
private fun TxnCard(t: PendingTxn, onEdit: (String) -> Unit) {
    val context = ctx()
    val warn = t.status == Status.FAILED || t.needsReview
    Card(
        Modifier.fillMaxWidth(),
        colors = if (warn) CardDefaults.cardColors(containerColor = Color(0x22C62828)) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(Notifier.title(t), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(t.status.name.lowercase(), style = MaterialTheme.typography.labelMedium)
            }
            Text(Notifier.summary(t), style = MaterialTheme.typography.bodyMedium)
            Text("${t.time.format(TIME)} · ${t.bank}" + (t.merchant?.let { " · SMS: $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
            t.failReason?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            Row {
                if (t.status != Status.ENTERED && t.status != Status.IGNORED) {
                    if (!t.needsReview) TextButton(onClick = { ActionReceiver.addToMoneyManager(context, t) }) { Text("Add") }
                    TextButton(onClick = { onEdit(t.id) }) { Text(if (t.needsReview) "Choose category" else "Edit") }
                    if (t.status == Status.FILLED || t.status == Status.FAILED) {
                        TextButton(onClick = { Store.update(context, t.id) { it.copy(status = Status.ENTERED) }; Notifier.cancel(context, t) }) { Text("Saved ✔") }
                    }
                    TextButton(onClick = { Store.update(context, t.id) { it.copy(status = Status.IGNORED) }; Notifier.cancel(context, t) }) { Text("Ignore") }
                } else {
                    Spacer(Modifier.width(1.dp))
                    TextButton(onClick = { Store.update(context, t.id) { it.copy(status = Status.PENDING) } }) { Text("Undo") }
                }
            }
        }
    }
}
