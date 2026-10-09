package sms2mm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import sms2mm.app.ui.BanksScreen
import sms2mm.app.ui.EditTxnDialog
import sms2mm.app.ui.KeywordsScreen
import sms2mm.app.ui.Palette
import sms2mm.app.ui.PendingScreen
import sms2mm.app.ui.SetupScreen
import sms2mm.app.ui.Sms2mmTheme

class MainActivity : ComponentActivity() {

    private var editId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        editId = intent.getStringExtra(EXTRA_EDIT)
        setContent { App(editId) { editId = it } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_EDIT)?.let { editId = it }
    }

    companion object {
        const val EXTRA_EDIT = "edit_id"
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Rounded.Home),
    Tab("Rules", Icons.AutoMirrored.Rounded.List),
    Tab("Banks", Icons.Rounded.Email),
    Tab("Setup", Icons.Rounded.Settings),
)

@Composable
private fun App(editId: String?, setEditId: (String?) -> Unit) {
    Sms2mmTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            containerColor = Palette.Background,
            topBar = { Header() },
            bottomBar = {
                NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Cyan,
                                selectedTextColor = Palette.Cyan,
                                indicatorColor = Palette.SurfaceHigh,
                                unselectedIconColor = Palette.TextDim,
                                unselectedTextColor = Palette.TextDim,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                0 -> PendingScreen(modifier, onEdit = setEditId)
                1 -> KeywordsScreen(modifier)
                2 -> BanksScreen(modifier)
                else -> SetupScreen(modifier)
            }
        }
        if (editId != null) EditTxnDialog(editId, onClose = { setEditId(null) })
    }
}

@Composable
private fun Header() {
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            buildAnnotatedString {
                append("SMS ")
                withStyle(SpanStyle(color = Palette.Cyan)) { append("→") }
                append(" Money Manager")
            },
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Text,
        )
        Text("Offline · OTP-safe · you tap Save", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
    }
}
