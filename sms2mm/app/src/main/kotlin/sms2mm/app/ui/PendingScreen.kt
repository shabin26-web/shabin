package sms2mm.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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

fun money(v: BigDecimal): String = "%,.2f".format(v)

/** Colour of a transaction's status. */
private fun PendingTxn.accent(): Color = when {
    status == Status.ENTERED -> Palette.Green
    status == Status.IGNORED -> Palette.TextDim
    needsReview -> Palette.Amber
    else -> Palette.Cyan
}

private fun PendingTxn.statusText(): String = when {
    status == Status.ENTERED -> "RECORDED"
    status == Status.IGNORED -> "IGNORED"
    needsReview -> "PICK CATEGORY"
    else -> "PENDING"
}

private enum class Show { PENDING, RECORDED, ALL }

@Composable
fun PendingScreen(modifier: Modifier, onEdit: (String) -> Unit) {
    val context = ctx()
    val version = storeVersion()
    var show by rememberSaveable { mutableStateOf(Show.PENDING) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val all = remember(version) { Store.txns(context) }
    val skips = remember(version) { Store.skips(context) }
    val shown = remember(all, show, query) {
        all.filter { t ->
            when (show) {
                Show.PENDING -> t.status.isOpen
                Show.RECORDED -> t.status == Status.ENTERED
                Show.ALL -> true
            } && (query.isBlank() || listOfNotNull(t.note, t.merchant, t.account, t.toAccount, t.category.label(), t.amount, t.bank)
                .any { it.contains(query.trim(), ignoreCase = true) })
        }
    }

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Summary(all, skips) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filter("Pending ${all.count { it.status.isOpen }}", show == Show.PENDING) { show = Show.PENDING }
                Filter("Recorded", show == Show.RECORDED) { show = Show.RECORDED }
                Filter("All", show == Show.ALL) { show = Show.ALL }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(Icons.Rounded.Search, "Search", tint = if (searching) Palette.Cyan else Palette.TextDim)
                }
            }
            if (searching) {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Merchant, note, category, account, amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (shown.isEmpty()) item {
            Text(
                when {
                    query.isNotBlank() -> "No match for \"$query\"."
                    show == Show.PENDING -> "All caught up ✔  New bank SMS appear here and as a notification."
                    else -> "Nothing here yet."
                },
                color = Palette.TextDim,
                modifier = Modifier.padding(vertical = 28.dp),
            )
        } else if (show == Show.PENDING) item {
            Text("Swipe → Recorded · swipe ← Ignore · tap the amount to copy it", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        }
        items(shown, key = { it.id }) { t -> SwipeCard(t, onEdit) }
        item {
            TextButton(onClick = { ActionReceiver.openMoneyManager(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Money Manager ↗", color = Palette.Cyan)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Filter(text: String, selected: Boolean, onClick: () -> Unit) = FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text) },
    shape = RoundedCornerShape(50),
    colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Palette.Cyan.copy(alpha = 0.16f),
        selectedLabelColor = Palette.Cyan,
        labelColor = Palette.TextDim,
    ),
    border = FilterChipDefaults.filterChipBorder(
        enabled = true, selected = selected,
        borderColor = Palette.Outline, selectedBorderColor = Palette.Cyan.copy(alpha = 0.6f),
    ),
)

/** This month, per currency: captured − recorded − ignored = pending (reaches 0 when all recorded). */
@Composable
private fun Summary(all: List<PendingTxn>, skips: List<SkipEvent>) {
    val month = YearMonth.now()
    val inMonth = all.filter { YearMonth.from(it.time) == month }
    val handled = inMonth.count { !it.status.isOpen }
    val fraction = if (inMonth.isEmpty()) 1f else handled.toFloat() / inMonth.size
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).glass().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction, Modifier.size(92.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("done", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("PENDING · ${month.month.name.take(3)} ${month.year}", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                if (inMonth.isEmpty()) Text("0.00", style = AmountStyle, color = Palette.Text)
                inMonth.groupBy { it.currency }.forEach { (currency, list) ->
                    val pending = list.filter { it.status.isOpen }.fold(BigDecimal.ZERO) { a, t -> a + t.amountValue }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(pending), style = if (currency == inMonth.first().currency) AmountStyle else MaterialTheme.typography.titleLarge, color = if (pending.signum() == 0) Palette.Green else Palette.Text)
                        Text(" $currency", color = Palette.TextDim, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Text(
                    if (inMonth.isEmpty()) "Nothing captured yet this month"
                    else if (handled == inMonth.size) "All ${inMonth.size} recorded ✔"
                    else "${inMonth.size - handled} of ${inMonth.size} still to record",
                    color = if (handled == inMonth.size && inMonth.isNotEmpty()) Palette.Green else Palette.Amber,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        val monthSkips = skips.filter { YearMonth.from(LocalDateTime.parse(it.at)) == month }
        if (monthSkips.isNotEmpty()) {
            val c = monthSkips.groupingBy { it.kind }.eachCount()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
                Pill("🔒 ${c[SkipEvent.OTP] ?: 0} OTP dropped", Palette.TextDim)
                (c[SkipEvent.IGNORED] ?: 0).takeIf { it > 0 }?.let { Pill("$it ignored", Palette.TextDim) }
                (c[SkipEvent.UNPARSED] ?: 0).takeIf { it > 0 }?.let { Pill("$it not recognised", Palette.Red) }
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color) = Text(
    text,
    color = color,
    style = MaterialTheme.typography.labelSmall,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeCard(t: PendingTxn, onEdit: (String) -> Unit) {
    val context = ctx()
    if (!t.status.isOpen) { TxnCard(t, onEdit); return }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> ActionReceiver.setStatus(context, t.id, Status.ENTERED)
            SwipeToDismissBoxValue.EndToStart -> ActionReceiver.setStatus(context, t.id, Status.IGNORED)
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false // the list refreshes from the store; snap the card back meanwhile
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toRecord = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .background((if (toRecord) Palette.Green else Palette.TextDim).copy(alpha = 0.18f))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (toRecord) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(if (toRecord) "✔ Recorded" else "Ignore ✕", color = if (toRecord) Palette.Green else Palette.TextDim, fontWeight = FontWeight.Bold)
            }
        },
    ) { TxnCard(t, onEdit) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TxnCard(t: PendingTxn, onEdit: (String) -> Unit) {
    val context = ctx()
    val clipboard = LocalClipboardManager.current
    val accent = t.accent()
    val open = t.status.isOpen
    val title = t.note ?: t.merchant ?: t.bank
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), alpha = 0.9f)
            .combinedClickable(
                onClick = { onEdit(t.id) },
                onLongClick = {
                    clipboard.setText(AnnotatedString(Notifier.details(t)))
                    Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
                },
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), Palette.Violet.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.trim().firstOrNull()?.uppercase() ?: "•", color = Palette.Background, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (t.type == TxnType.TRANSFER) "${t.account} → ${t.toAccount}" else "${if (t.needsReview) "Pick a category" else t.category.label()} · ${t.account}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {
                    clipboard.setText(AnnotatedString(t.amount))
                    Toast.makeText(context, "${t.amount} copied", Toast.LENGTH_SHORT).show()
                }).padding(6.dp),
            ) {
                Text(
                    (if (t.type == TxnType.INCOME) "+" else "") + money(t.amountValue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (t.type == TxnType.INCOME) Palette.Green else Palette.Text,
                )
                Text(t.currency, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Pill(t.statusText(), accent)
            Text("  ${t.time.format(TIME)} · ${t.bank}", color = Palette.TextDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
            if (open) {
                IconButton(onClick = { onEdit(t.id) }) { Icon(Icons.Rounded.Edit, "Edit", tint = Palette.TextDim) }
                IconButton(onClick = { ActionReceiver.setStatus(context, t.id, Status.IGNORED) }) { Icon(Icons.Rounded.Close, "Ignore", tint = Palette.TextDim) }
                Button(
                    onClick = { ActionReceiver.setStatus(context, t.id, Status.ENTERED) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Color(0xFF00222A)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(36.dp),
                ) { Text("✔ Recorded", fontWeight = FontWeight.SemiBold) }
            } else {
                IconButton(onClick = { ActionReceiver.setStatus(context, t.id, Status.PENDING) }) {
                    Icon(Icons.Rounded.Refresh, "Back to pending", tint = Palette.TextDim)
                }
            }
        }
    }
}
