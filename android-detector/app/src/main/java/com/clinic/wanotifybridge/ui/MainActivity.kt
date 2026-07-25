package com.clinic.wanotifybridge.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.clinic.wanotifybridge.data.BridgeSettings
import com.clinic.wanotifybridge.notify.WaNotificationListener
import com.clinic.wanotifybridge.service.BridgeForegroundService
import com.clinic.wanotifybridge.util.FailureLog
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* advisory */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ needs this for the foreground service's own persistent notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from system settings may have changed permission state; refresh so the
        // persistent notification reflects it.
        BridgeForegroundService.refresh(this)
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { BridgeSettings.get(context) }

    var enabled by remember { mutableStateOf(settings.enabled) }
    var url by remember { mutableStateOf(settings.webhookUrl) }
    var secret by remember { mutableStateOf(settings.webhookSecret) }
    var secretVisible by remember { mutableStateOf(false) }
    var forwardGroups by remember { mutableStateOf(settings.forwardGroups) }
    var hoursEnabled by remember { mutableStateOf(settings.businessHoursEnabled) }
    var startText by remember { mutableStateOf(minutesToText(settings.businessStartMinute)) }
    var endText by remember { mutableStateOf(minutesToText(settings.businessEndMinute)) }
    var days by remember { mutableStateOf(settings.businessDays) }
    var allow by remember { mutableStateOf(settings.allowList.joinToString("\n")) }
    var block by remember { mutableStateOf(settings.blockList.joinToString("\n")) }
    var failures by remember { mutableStateOf(FailureLog.read(context)) }
    var saved by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("WA Notify Bridge", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Detects new WhatsApp messages and forwards them to your Routine webhook. " +
                    "This app never sends replies itself.",
                style = MaterialTheme.typography.bodyMedium,
            )

            PermissionCard(context)

            SectionTitle("Master switch")
            SwitchRow(
                label = if (enabled) "Forwarding is ON" else "Forwarding is OFF",
                checked = enabled,
                onChange = {
                    enabled = it
                    settings.enabled = it
                    BridgeForegroundService.refresh(context)
                },
            )

            SectionTitle("Webhook")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Webhook URL (https)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("Shared secret (X-Webhook-Secret)") },
                singleLine = true,
                visualTransformation = if (secretVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { secretVisible = !secretVisible }) {
                        Text(if (secretVisible) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Stored in EncryptedSharedPreferences and excluded from backup. " +
                    "Must match the secret your Routine validates.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Scope")
            SwitchRow(
                label = "Also forward group chats",
                checked = forwardGroups,
                onChange = { forwardGroups = it },
            )
            Text(
                "Off by default. The Routine never auto-replies in groups regardless — " +
                    "turning this on only gets group messages logged.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Business hours")
            SwitchRow(
                label = "Only forward during business hours",
                checked = hoursEnabled,
                onChange = { hoursEnabled = it },
            )
            if (hoursEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("From (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("To (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                DayPicker(days) { days = it }
                Text(
                    "Outside this window nothing is forwarded at all — the message stays " +
                        "in WhatsApp for a human to handle the next morning.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionTitle("Allow-list")
            OutlinedTextField(
                value = allow,
                onValueChange = { allow = it },
                label = { Text("One name or number per line") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Leave empty to forward from everyone, including new unknown numbers " +
                    "(current setup). Add entries only to narrow it down.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Block-list")
            OutlinedTextField(
                value = block,
                onValueChange = { block = it },
                label = { Text("One name or number per line") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Always wins over the allow-list. Put staff, suppliers and personal " +
                    "contacts here.",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                onClick = {
                    settings.webhookUrl = url
                    settings.webhookSecret = secret
                    settings.forwardGroups = forwardGroups
                    settings.businessHoursEnabled = hoursEnabled
                    textToMinutes(startText)?.let { settings.businessStartMinute = it }
                    textToMinutes(endText)?.let { settings.businessEndMinute = it }
                    settings.businessDays = days
                    settings.allowList = allow.lines()
                    settings.blockList = block.lines()
                    BridgeForegroundService.refresh(context)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save settings")
            }
            if (saved) {
                Text("Saved.", style = MaterialTheme.typography.bodySmall)
            }

            SectionTitle("Delivery failures")
            if (failures.isEmpty()) {
                Text("None recorded.", style = MaterialTheme.typography.bodySmall)
            } else {
                failures.take(15).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    FailureLog.clear(context)
                    failures = emptyList()
                }) {
                    Text("Clear failure log")
                }
            }
            Text(
                "Message contents are never stored on this device — only the fact that a " +
                    "delivery failed, with the sender partially redacted.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(context: Context) {
    val hasAccess = WaNotificationListener.hasNotificationAccess(context)
    val exempt = isIgnoringBatteryOptimizations(context)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Setup steps", fontWeight = FontWeight.Bold)
            Text(
                "Neither of these can be granted by the app — Android requires you to " +
                    "flip them yourself.",
                style = MaterialTheme.typography.bodySmall,
            )

            Divider()

            Text(
                "1. Notification access ${if (hasAccess) "— GRANTED" else "— NOT GRANTED"}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Settings → Notifications → Notification access (some phones: " +
                    "Special app access → Notification access) → find \"WA Notify Bridge\" " +
                    "→ turn it on → confirm the warning dialog.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { openNotificationAccessSettings(context) }) {
                Text("Open notification access settings")
            }

            Divider()

            Text(
                "2. Battery optimisation ${if (exempt) "— EXEMPT" else "— NOT EXEMPT"}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Without an exemption, Doze eventually kills the listener and messages " +
                    "stop being detected. Grant \"Unrestricted\" / \"Don't optimise\" for " +
                    "this app. On Xiaomi/Oppo/Vivo/Huawei also enable Autostart and lock " +
                    "the app in recents.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { requestBatteryExemption(context) }) {
                Text("Open battery settings")
            }

            Divider()

            Text("3. Keep WhatsApp notifications on", fontWeight = FontWeight.SemiBold)
            Text(
                "This app can only see what WhatsApp posts. If WhatsApp notifications are " +
                    "muted for a chat, or the chat is open on screen when the message " +
                    "arrives, no notification is posted and nothing is forwarded.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            val value = day.value
            val isOn = value in selected
            OutlinedButton(
                onClick = {
                    onChange(if (isOn) selected - value else selected + value)
                },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
            ) {
                Text(
                    day.name.take(1) + if (isOn) "✓" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun openNotificationAccessSettings(context: Context) {
    // Some OEM builds bury or rename this screen; falling back to the settings root is
    // better than crashing on an unresolved intent.
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun minutesToText(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private fun textToMinutes(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..24 || minute !in 0..59) return null
    return hour * 60 + minute
}
