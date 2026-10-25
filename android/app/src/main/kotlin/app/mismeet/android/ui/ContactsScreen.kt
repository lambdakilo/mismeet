package app.mismeet.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.unit.dp
import app.mismeet.android.AppModel
import app.mismeet.android.store.Contact
import app.mismeet.protocol.LocationPayload
import app.mismeet.protocol.ProtocolConstants
import app.mismeet.protocol.TravelMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(model: AppModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var photoFor by remember { mutableStateOf<Contact?>(null) }
    var choosingFor by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = choosingFor
        choosingFor = null
        if (uri != null && id != null) scope.launch { model.loadPhoto(uri)?.let { cropping = id to it } }
    }
    val choosePhoto = { id: String ->
        choosingFor = id
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Contacts", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.refreshNow() }) { Text("Refresh") }
                Button(onClick = { showAdd = true }) { Text("Add") }
            }
        }
        if (state.contacts.isEmpty()) {
            Text("Add a contact from the invite they sent you.", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.contacts, key = { it.id }) { contact ->
                ContactRow(
                    contact = contact,
                    photo = model.photo(contact),
                    onPhoto = { if (contact.photoSetAt == null) choosePhoto(contact.id) else photoFor = contact },
                    onShare = if (state.sharingEnabled) ({ model.setShare(contact.id, it) }) else null,
                    onRemove = { model.removeContact(contact.id) },
                    onOpenMap = { openMap(context, contact) },
                )
            }
        }
    }

    photoFor?.let { contact ->
        AlertDialog(
            onDismissRequest = { photoFor = null },
            title = { Text("Photo of ${contact.name}") },
            text = { Text("The photo stays on this phone and shows on the map in place of the initial.") },
            confirmButton = {
                TextButton(onClick = {
                    photoFor = null
                    choosePhoto(contact.id)
                }) { Text("Choose another") }
            },
            dismissButton = {
                TextButton(onClick = {
                    photoFor = null
                    model.removePhoto(contact.id)
                }) { Text("Remove photo") }
            },
        )
    }

    cropping?.let { (id, bitmap) ->
        PhotoCropDialog(bitmap, onDismiss = { cropping = null }, onChoose = { square ->
            cropping = null
            model.setPhoto(id, square)
        })
    }

    if (showAdd) {
        AddContactDialog(
            onDismiss = { showAdd = false },
            onAdd = { invite, name -> model.addContact(invite, name) },
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    photo: Bitmap?,
    onPhoto: () -> Unit,
    onShare: ((Boolean) -> Unit)?,
    onRemove: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact, photo, 48.dp, Modifier.size(48.dp).clickable(onClick = onPhoto))
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(status(contact), style = MaterialTheme.typography.bodySmall)
                }
                if (onShare != null) Switch(checked = contact.share, onCheckedChange = onShare)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contact.lastPayload != null) TextButton(onClick = onOpenMap) { Text("Open in map") }
                TextButton(onClick = onPhoto) { Text("Photo") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

private fun status(contact: Contact): String {
    val payload = contact.lastPayload
    if (payload != null) {
        val age = DateUtils.getRelativeTimeSpanString(payload.fixTime * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        val stale = if (contact.sharing == Contact.SharingStatus.NOT_SHARING) ", no longer sharing" else ""
        val moving = movement(payload)?.let { ", $it" }.orEmpty()
        return "Seen $age, within ${payload.accuracy} m$moving$stale"
    }
    return when (contact.sharing) {
        Contact.SharingStatus.NOT_SHARING -> "Not sharing with you"
        else -> "No location yet"
    }
}

/** How a contact was moving at the fix, such as "by bike at 18 km/h", or null when it was not. */
fun movement(payload: LocationPayload): String? {
    val speed = payload.speed?.takeIf { it >= ProtocolConstants.ESTIMATE_MIN_SPEED } ?: return null
    val mode = when (TravelMode.of(speed)) {
        TravelMode.FOOT -> "on foot"
        TravelMode.BIKE -> "by bike"
        TravelMode.CAR -> "by car"
    }
    return "$mode at ${(speed * 3.6).roundToInt()} km/h"
}

private fun openMap(context: Context, contact: Contact) {
    val payload = contact.lastPayload ?: return
    val point = "${payload.latitude},${payload.longitude}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(${Uri.encode(contact.name)})"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No map app installed; the coordinates stay visible in the row.
    }
}

@Composable
private fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var invite by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { invite = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invite,
                    onValueChange = { invite = it },
                    label = { Text("Invite") },
                    placeholder = { Text("nostr:nprofile1…") },
                    minLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        invite = context.getSystemService(ClipboardManager::class.java)
                            ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    }) { Text("Paste") }
                    TextButton(onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan the invite")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    }) { Text("Scan QR code") }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { onAdd(invite, name) }
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message ?: "Not a valid invite" }
                },
                enabled = invite.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
