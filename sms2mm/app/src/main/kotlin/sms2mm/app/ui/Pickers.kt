package sms2mm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sms2mm.core.TxnType
import sms2mm.core.UserRules

/** Accounts for pickers: Money Manager export first (most-used first), then any the rules mention. */
fun accountOptions(rules: UserRules): List<String> =
    (rules.catalog.accounts + knownAccounts(rules)).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Category → subcategories for pickers: the export's list plus categories already used in rules. */
fun categoryOptions(rules: UserRules, type: TxnType?): Map<String, List<String>> {
    val out = LinkedHashMap<String, MutableList<String>>()
    val fromExport = when (type) {
        TxnType.INCOME -> rules.catalog.income
        TxnType.EXPENSE -> rules.catalog.expense
        else -> rules.catalog.expense + rules.catalog.income
    }
    fromExport.forEach { (cat, subs) -> out.getOrPut(cat) { mutableListOf() }.addAll(subs) }
    rules.keywords.filter { type == null || it.type == null || it.type == type }.forEach { r ->
        val list = out.getOrPut(r.category.name) { mutableListOf() }
        r.category.subcategory?.takeIf { it.isNotBlank() && it !in list }?.let(list::add)
    }
    return out.filterKeys { it.isNotBlank() && it != "Uncategorized" }
}

/**
 * A field that opens a searchable list. [allowCustom] adds "Use 'typed text'" for values
 * not in the list; [optional] adds a "None" choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickField(
    label: String,
    value: String,
    options: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    allowCustom: Boolean = true,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.SurfaceHigh)
            .border(1.dp, if (value.isBlank()) Palette.Outline else Palette.Cyan.copy(alpha = 0.55f), shape)
            .clickable(enabled = enabled) { open = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value.ifBlank { if (optional) "None" else "Tap to choose" },
                color = if (value.isBlank()) Palette.TextDim else Palette.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = Palette.Cyan)
        }
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet, containerColor = Palette.Surface) {
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Search ${options.size} options") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val shown = options.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = 8.dp)) {
                    if (optional) item { Option("None", selected = value.isBlank(), dim = true) { onPick(""); open = false } }
                    if (allowCustom && query.isNotBlank() && options.none { it.equals(query.trim(), true) }) {
                        item { Option("+ Use \"${query.trim()}\"", selected = false, accent = true) { onPick(query.trim()); open = false } }
                    }
                    items(shown) { o -> Option(o, selected = o == value) { onPick(o); open = false } }
                    if (shown.isEmpty() && !allowCustom) item { Text("No match", color = Palette.TextDim, modifier = Modifier.padding(12.dp)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Option(text: String, selected: Boolean, dim: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = when { accent -> Palette.Cyan; dim -> Palette.TextDim; else -> Palette.Text },
        )
        if (selected) Text("✓", color = Palette.Cyan)
    }
    HorizontalDivider(color = Palette.Outline.copy(alpha = 0.4f))
}

/** Category then subcategory, from the export catalog (filtered by transaction type). */
@Composable
fun CategoryPickers(
    rules: UserRules,
    type: TxnType?,
    category: String,
    subcategory: String,
    onCategory: (String) -> Unit,
    onSubcategory: (String) -> Unit,
) {
    val options = remember(rules, type) { categoryOptions(rules, type) }
    PickField("Category", category, options.keys.toList(), { picked ->
        if (picked != category) onSubcategory("")
        onCategory(picked)
    })
    val subs = options[category].orEmpty()
    PickField(
        "Subcategory", subcategory, subs, onSubcategory,
        optional = true,
        enabled = category.isNotBlank(),
    )
}
