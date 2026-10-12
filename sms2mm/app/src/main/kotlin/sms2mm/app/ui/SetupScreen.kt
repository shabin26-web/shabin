package sms2mm.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sms2mm.app.ExportImport
import sms2mm.app.Reminder
import sms2mm.app.Store
import sms2mm.core.UserRules

@Composable
fun SetupScreen(modifier: Modifier) {
    val context = ctx()
    storeVersion() // re-check permissions whenever the app refreshes
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Store.touch() }
    val exportRules = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(Store.rules(context).toJson().toByteArray()) }
        Toast.makeText(context, "Rules exported", Toast.LENGTH_SHORT).show()
    }
    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@rememberLauncherForActivityResult
        try {
            val imported = UserRules.fromJson(text)
            val before = Store.rules(context).keywords.size
            Store.updateRules(context) { it.merge(imported) }
            val added = Store.rules(context).keywords.size - before
            Toast.makeText(context, "Merged: $added new keyword rules added, your existing rules kept", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Not a valid rules file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val importExport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            importResult = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open the file")
                    "✔ " + ExportImport.import(context, bytes)
                } catch (e: Exception) {
                    "✖ Not a Money Manager export: ${e.message}"
                }
            }
            importing = false
        }
    }
    val catalog = Store.rules(context).catalog
    val exportPending = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val (bytes, n) = ExportImport.exportPending(context)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not save the file")
                    "Exported $n pending item${if (n == 1) "" else "s"}"
                } catch (e: Exception) {
                    "Export failed: ${e.message}"
                }
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    var reminder by remember { mutableStateOf(Reminder.settings(context)) }

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("1. Permissions")
        val sms = granted(Manifest.permission.RECEIVE_SMS)
        val notify = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        Text("Receive SMS: ${if (sms) "✔" else "✖"}    Notifications: ${if (notify) "✔" else "✖"}")
        if (!sms || !notify) {
            Button(onClick = {
                askPermissions.launch(buildList {
                    add(Manifest.permission.RECEIVE_SMS)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray())
            }) { Text("Allow") }
            Text(
                "If SMS says \"denied\" or \"restricted setting\": open App info → ⋮ (top right) → Allow restricted settings, then tap Allow here again.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Open app info") }
        }

        SectionTitle("2. Money Manager accounts & categories")
        Text(
            "In Money Manager: Settings → Backup → Export Data to Excel. Then import the file here: " +
                "your accounts, categories and subcategories become pick lists, and pending items " +
                "already in Money Manager are marked Recorded.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(enabled = !importing, onClick = {
            importExport.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*"))
        }) { Text(if (importing) "Reading…" else "Import Money Manager export (.xlsx)") }
        importResult?.let { Text(it, color = if (it.startsWith("✔")) Palette.Green else Palette.Red, style = MaterialTheme.typography.bodySmall) }
        if (!catalog.isEmpty) {
            Text(
                "Loaded: ${catalog.accounts.size} accounts · ${catalog.expense.size} expense + ${catalog.income.size} income categories" +
                    (catalog.updated?.let { " · updated $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
            )
        }

        SectionTitle("3. Banks and keywords")
        Text("Set sender names on the Banks tab and keywords on the Rules tab, or import your rules file:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importRules.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import rules (merge)") }
            OutlinedButton(onClick = { exportRules.launch("sms2mm-rules.json") }) { Text("Export rules") }
        }

        SectionTitle("4. Daily reminder")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remind me about pending items")
                Text(
                    "Every day at %02d:%02d, only when something is still pending".format(reminder.hour, reminder.minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                )
            }
            Switch(checked = reminder.enabled, onCheckedChange = {
                reminder = reminder.copy(enabled = it)
                Reminder.save(context, reminder)
            })
        }
        if (reminder.enabled) {
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m ->
                    reminder = reminder.copy(hour = h, minute = m)
                    Reminder.save(context, reminder)
                }, reminder.hour, reminder.minute, true).show()
            }) { Text("Change time") }
        }

        SectionTitle("5. Export pending to Excel")
        Text(
            "Saves the pending list as .xlsx (Arial, live totals per currency, duplicates and items without a category highlighted) " +
                "— handy for entering several at once or as a checklist.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { exportPending.launch("sms2mm-pending-${java.time.LocalDate.now()}.xlsx") }) { Text("Export pending (.xlsx)") }

        SectionTitle("How it works")
        Text(
            "Each bank SMS becomes a Pending item showing the account, category, amount and note to enter. " +
                "Type it into Money Manager (Home → Open MM ↗), then tap Recorded ✔. The summary shows what is still pending this month.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Security")
        Text(
            "• No internet permission: this app cannot send anything off the phone (check: Settings → Apps → SMS → Money Manager → Permissions).\n" +
                "• No Accessibility access: it never reads or controls other apps.\n" +
                "• Only SMS from your bank senders are read.\n" +
                "• OTP / verification / password messages are dropped before anything else — only a count is kept.\n" +
                "• The SMS text is never saved: only date, amount, merchant, card last-4, account and category.\n" +
                "• Not included in phone backups.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
