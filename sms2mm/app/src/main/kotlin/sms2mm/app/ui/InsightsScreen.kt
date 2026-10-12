package sms2mm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sms2mm.app.PendingTxn
import sms2mm.app.Status
import sms2mm.app.Store
import sms2mm.core.Category
import sms2mm.core.TxnType
import java.math.BigDecimal
import java.time.YearMonth

/** This month (or any month) from captured bank SMS: spending by category, top merchants, in vs out. */
@Composable
fun InsightsScreen(modifier: Modifier) {
    val context = ctx()
    val version = storeVersion()
    val all = remember(version) { Store.txns(context).filter { it.status != Status.IGNORED } }
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val month = YearMonth.now().minusMonths(monthOffset)
    val inMonth = all.filter { YearMonth.from(it.time) == month }
    val currencies = inMonth.map { it.currency }.distinct().ifEmpty { listOf("SAR") }
    var currency by rememberSaveable { mutableStateOf(currencies.first()) }
    if (currency !in currencies) currency = currencies.first()
    val list = inMonth.filter { it.currency == currency }

    fun sum(f: (PendingTxn) -> Boolean) = list.filter(f).fold(BigDecimal.ZERO) { a, t -> a + t.amountValue }
    val spent = sum { it.type == TxnType.EXPENSE }
    val received = sum { it.type == TxnType.INCOME }
    val moved = sum { it.type == TxnType.TRANSFER }

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = { monthOffset++ }) { Text("‹", color = Palette.Accent, style = MaterialTheme.typography.titleLarge) }
            Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(enabled = monthOffset > 0, onClick = { monthOffset-- }) { Text("›", color = if (monthOffset > 0) Palette.Accent else Palette.TextDim.copy(alpha = 0.35f), style = MaterialTheme.typography.titleLarge) }
        }
        if (currencies.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            currencies.forEach { c ->
                FilterChip(
                    selected = c == currency, onClick = { currency = c }, label = { Text(c) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.AccentSoft, selectedLabelColor = Palette.Accent),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SPENT", spent, currency, Palette.Text, Modifier.weight(1f))
            Tile("RECEIVED", received, currency, Palette.Green, Modifier.weight(1f))
        }
        if (moved.signum() > 0) Tile("TRANSFERRED", moved, currency, Palette.Accent, Modifier.fillMaxWidth())

        if (list.isEmpty()) {
            Text("No bank SMS captured for this month yet.", color = Palette.TextDim, modifier = Modifier.padding(vertical = 24.dp))
            return@Column
        }

        val byCategory = list.filter { it.type == TxnType.EXPENSE }
            .groupBy { if (it.category == Category.UNCATEGORIZED) "Uncategorized" else it.category.name }
            .mapValues { (_, v) -> v.fold(BigDecimal.ZERO) { a, t -> a + t.amountValue } }
            .entries.sortedByDescending { it.value }
        if (byCategory.isNotEmpty()) {
            Panel("Spending by category") {
                val max = byCategory.first().value
                byCategory.forEach { (cat, amount) -> Bar(cat, amount, max, currency) }
            }
        }

        val merchants = list.filter { it.type == TxnType.EXPENSE }
            .groupBy { it.note ?: it.merchant ?: it.bank }
            .mapValues { (_, v) -> v.size to v.fold(BigDecimal.ZERO) { a, t -> a + t.amountValue } }
            .entries.sortedByDescending { it.value.second }.take(5)
        if (merchants.isNotEmpty()) {
            Panel("Top merchants") {
                merchants.forEachIndexed { i, (name, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text("${i + 1}", color = Palette.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${v.first} purchase${if (v.first == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                        }
                        Text("${money(v.second)} $currency", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val byAccount = list.groupBy { it.account }.mapValues { (_, v) -> v.size }.entries.sortedByDescending { it.value }
        Panel("By account") {
            byAccount.forEach { (acc, n) ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(acc, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$n", color = Palette.TextDim)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Tile(label: String, amount: BigDecimal, currency: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.card(RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        Text(money(amount), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        Text(currency, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Accent, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun Bar(label: String, amount: BigDecimal, max: BigDecimal, currency: String) {
    val fraction = if (max.signum() == 0) 0f else (amount.toDouble() / max.toDouble()).toFloat().coerceIn(0.02f, 1f)
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("${money(amount)} $currency", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).padding(top = 3.dp).clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(Palette.Accent))
        }
    }
}
