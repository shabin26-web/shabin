package sms2mm.app.ui

import android.Manifest
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            val rules = UserRules.fromJson(text)
            Store.saveRules(context, rules)
            Toast.makeText(context, "Imported ${rules.keywords.size} keyword rules", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Not a valid rules file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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

        SectionTitle("2. Banks and keywords")
        Text("Set sender names on the Banks tab and keywords on the Rules tab, or import your rules file:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importRules.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import rules") }
            OutlinedButton(onClick = { exportRules.launch("sms2mm-rules.json") }) { Text("Export rules") }
        }

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
