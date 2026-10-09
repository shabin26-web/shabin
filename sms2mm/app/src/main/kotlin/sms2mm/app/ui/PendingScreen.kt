package sms2mm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sms2mm.app.ActionReceiver
import sms2mm.app.Notifier
import sms2mm.app.PendingTxn
import sms2mm.app.SkipEvent
import sms2mm.app.Status
import sms2mm.app.Store
import sms2mm.app.label
import sms2mm.core.TxnType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("dd MMM · HH:mm")
private val OPEN = setOf(Status.PENDING, Status.FILLED, Status.FAILED)

private fun money(v: BigDecimal) = "%,.2f".format(v)

/** Colour of a transaction's status bar and label. */
private fun PendingTxn.accent(): Color = when {
    status == Status.ENTERED -> Palette.Green
    status == Status.IGNORED -> Palette.TextDim
    status == Status.FAILED || needsReview -> Palette.Red
    status == Status.FILLED -> Palette.Amber
    else -> Palette.Cyan
}

private fun PendingTxn.statusText(): String = when {
    status == Status.ENTERED -> "Saved"
    status == Status.IGNORED -> "Ignored"
    status == Status.FAILED -> "Needs you"
    status == Status.FILLED -> "Filled · tap Save"
    needsReview -> "Pick category"
    else -> "Ready"
}

@Composable
fun PendingScreen(modifier: Modifier, onEdit: (String) -> Unit) {
    val context = ctx()
    val version = storeVersion()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val all = remember(version) { Store.txns(context) }
    val skips = remember(version) { Store.skips(context) }
    val shown = remember(all, showAll) { if (showAll) all else all.filter { it.status in OPEN } }

    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Summary(all, skips) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Filter("To add (${all.count { it.status in OPEN }})", !showAll) { showAll = false }
                Filter("All (${all.size})", showAll) { showAll = true }
            }
        }
        if (shown.isEmpty()) item {
            Text(
                "All caught up. New bank SMS appear here and as a notification.",
                color = Palette.TextDim,
                modifier = Modifier.padding(vertical = 32.dp),
            )
        }
        items(shown, key = { it.id }) { t -> TxnCard(t, onEdit) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Filter(text: String, selected: Boolean, onClick: () -> Unit) = FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text) },
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Palette.SurfaceHigh,
        selectedLabelColor = Palette.Cyan,
        labelColor = Palette.TextDim,
    ),
)

/** This month, per currency: captured − entered − ignored = still to add (must reach 0). */
@Composable
private fun Summary(all: List<PendingTxn>, skips: List<SkipEvent>) {
    val month = YearMonth.now()
    val inMonth = all.filter { YearMonth.from(it.time) == month }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Accent, shape)
            .padding(18.dp),
    ) {
        Text(
            "STILL TO ADD · ${month.month.name.take(3)} ${month.year}",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim,
        )
        if (inMonth.isEmpty()) {
            Text("0.00", style = AmountStyle, color = Palette.Text)
            Text("Nothing captured this month yet", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        inMonth.groupBy { it.currency }.forEach { (currency, list) ->
            fun sum(f: (PendingTxn) -> Boolean) = list.filter(f).fold(BigDecimal.ZERO) { a, t -> a + t.amountValue }
            val captured = sum { true }
            val entered = sum { it.status == Status.ENTERED }
            val ignored = sum { it.status == Status.IGNORED }
            val outstanding = captured - entered - ignored
            val clear = outstanding.signum() == 0
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text(money(outstanding), style = AmountStyle, color = if (clear) Palette.Green else Palette.Text)
                Text(" $currency", color = Palette.TextDim, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    if (clear) "Ties out ✔" else "${list.count { it.status in OPEN }} open",
                    color = if (clear) Palette.Green else Palette.Amber,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Captured", captured)
                Stat("Entered", entered)
                Stat("Ignored", ignored)
            }
        }
        val monthSkips = skips.filter { YearMonth.from(LocalDateTime.parse(it.at)) == month }
        if (monthSkips.isNotEmpty()) {
            val c = monthSkips.groupingBy { it.kind }.eachCount()
            val unparsed = c[SkipEvent.UNPARSED] ?: 0
            Text(
                "🔒 ${c[SkipEvent.OTP] ?: 0} OTP dropped · ${c[SkipEvent.IGNORED] ?: 0} ignored" + if (unparsed > 0) " · $unparsed not recognised" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (unparsed > 0) Palette.Red else Palette.TextDim,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: BigDecimal) = Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
    Text(money(value), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
}

@Composable
private fun TxnCard(t: PendingTxn, onEdit: (String) -> Unit) {
    val context = ctx()
    val accent = t.accent()
    val open = t.status in OPEN
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(Palette.Surface),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.note ?: t.merchant ?: t.bank,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (t.type == TxnType.TRANSFER) "${t.account} → ${t.toAccount}" else "${t.category.label()} · ${t.account}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (t.type == TxnType.INCOME) "+" else "") + money(t.amountValue),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (t.type == TxnType.INCOME) Palette.Green else Palette.Text,
                    )
                    Text(t.currency, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(t.statusText(), color = accent, style = MaterialTheme.typography.labelMedium)
                Text("  ·  ${t.time.format(TIME)} · ${t.bank}", color = Palette.TextDim, style = MaterialTheme.typography.labelSmall)
            }
            t.failReason?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (open) {
                    if (!t.needsReview) {
                        Button(
                            onClick = { ActionReceiver.addToMoneyManager(context, t) },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Color(0xFF00222A)),
                            modifier = Modifier.height(36.dp),
                        ) { Text("Add") }
                        Spacer(Modifier.width(8.dp))
                    }
                    OutlinedButton(onClick = { onEdit(t.id) }, modifier = Modifier.height(36.dp)) {
                        Text(if (t.needsReview) "Pick category" else "Edit")
                    }
                    Spacer(Modifier.weight(1f))
                    if (t.status == Status.FILLED || t.status == Status.FAILED) {
                        TextButton(onClick = { Store.update(context, t.id) { it.copy(status = Status.ENTERED) }; Notifier.cancel(context, t) }) {
                            Text("Saved ✔", color = Palette.Green)
                        }
                    }
                    TextButton(onClick = { Store.update(context, t.id) { it.copy(status = Status.IGNORED) }; Notifier.cancel(context, t) }) {
                        Text("Ignore", color = Palette.TextDim)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { Store.update(context, t.id) { it.copy(status = Status.PENDING) } }) {
                        Text("Undo", color = Palette.TextDim)
                    }
                }
            }
        }
    }
}
