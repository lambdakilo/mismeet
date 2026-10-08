package app.mismeet.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mismeet.android.AppModel
import app.mismeet.android.location.LocationProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun MeScreen(model: AppModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val log by model.log.collectAsState()
    val context = LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionVersion++ }
    val foreground = remember(permissionVersion) { LocationProvider.hasForegroundPermission(context) }
    val background = remember(permissionVersion) { LocationProvider.hasBackgroundPermission(context) }
    val notifications = remember(permissionVersion) { LocationProvider.hasNotificationPermission(context) }
    val unrestrictedBattery = remember(permissionVersion) { LocationProvider.isIgnoringBatteryOptimizations(context) }
    LifecycleResumeEffect(Unit) {
        permissionVersion++
        onPauseOrDispose {}
    }
    val inviteUri = remember(state.relays) { model.inviteUri() }
    val qr = remember(inviteUri) { qrBitmap(inviteUri) }
    var newRelay by remember { mutableStateOf("") }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Invite", style = MaterialTheme.typography.titleMedium)
        Image(qr.asImageBitmap(), contentDescription = "Invite QR code", modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally))
        Text(model.npub, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Mismeet invite", inviteUri))
        }) { Text("Copy invite") }

        Text("Location", style = MaterialTheme.typography.titleMedium)
        if (!foreground) {
            Button(onClick = { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                Text("Allow location")
            }
        } else if (!background) {
            Button(onClick = { permissions.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) }) {
                Text("Allow location all the time")
            }
        }
        if (!notifications) {
            Button(onClick = { permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Allow notifications") }
        }
        if (!unrestrictedBattery) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            }) { Text("Allow unrestricted battery use") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Share in the background")
            Switch(checked = state.backgroundSharing, onCheckedChange = { model.setBackgroundSharing(it) }, enabled = foreground)
        }
        Button(onClick = { model.publishNow() }, enabled = foreground) { Text("Publish now") }
        state.lastPublishedAt?.let { Text("Last published ${DateFormat.getTimeFormat(context).format(it)}", style = MaterialTheme.typography.bodySmall) }

        Text("Relays", style = MaterialTheme.typography.titleMedium)
        state.relays.forEach { relay ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(relay, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.removeRelay(relay) }) { Text("Remove") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newRelay, onValueChange = { newRelay = it }, label = { Text("wss://") }, modifier = Modifier.weight(1f))
            Button(onClick = { model.addRelay(newRelay); newRelay = "" }, enabled = newRelay.isNotBlank()) { Text("Add") }
        }

        Text("Log", style = MaterialTheme.typography.titleMedium)
        log.asReversed().forEach { line ->
            Text("${DateFormat.getTimeFormat(context).format(line.time)}  ${line.text}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun qrBitmap(text: String, size: Int = 480): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}
