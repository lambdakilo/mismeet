package app.mismeet.android.ui

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.mismeet.android.AppModel
import app.mismeet.android.location.Countries
import app.mismeet.android.store.Contact
import app.mismeet.protocol.EstimateResult
import app.mismeet.protocol.LatLon
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap tiles through osmdroid: no Google dependency, and the tile policy wants a user
 * agent. Opens framed around the people in this phone's country, like Find My; the button at the
 * bottom returns there, and a second tap widens to everyone, abroad included. The [focused]
 * contact, tapped in the list, is framed with a card at the bottom until it is closed.
 */
@Composable
fun MapScreen(model: AppModel, focused: String?, onUnfocus: () -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val placeNames by model.placeNames.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        true
    }
    var estimates by remember { mutableStateOf<Map<String, EstimateResult.Estimate>>(emptyMap()) }
    val countries by produceState<Countries?>(null) { value = withContext(Dispatchers.Default) { model.countries } }

    val located = state.contacts.filter { it.lastPayload != null }
    val own = state.lastFix?.let { LatLon(it.latitude, it.longitude) }
    val home = countries?.home(own, located.map { it.position() })
    val inHome = { point: LatLon -> home == null || countries?.code(point) == home }
    val everyone = points(located, own, estimates) { true }
    val nearby = points(located, own, estimates, inHome).ifEmpty { everyone }
    val someoneAbroad = nearby.size != everyone.size
    val focusedContact = located.firstOrNull { it.id == focused }
    val focusKey = focusedContact?.lastPayload?.let { "${focusedContact.id}:${it.latitude},${it.longitude},${it.accuracy}" }

    var framing by remember { mutableStateOf(Framing.NEARBY) }
    var framedAt by remember { mutableStateOf<Pair<GeoPoint, Double>?>(null) }
    var moved by remember { mutableStateOf(false) }
    val frameKey = focusKey
        ?: (located.joinToString(",") { it.id } + ":" + estimates.keys.sorted().joinToString(",") + ":" + (own != null) + ":" + home)
    val framedKey = remember { arrayOf("") }
    val map = remember { arrayOfNulls<MapView>(1) }
    val show by rememberUpdatedState { view: MapView, target: Framing ->
        framing = target
        frame(view, if (target == Framing.NEARBY) nearby else everyone)
        framedAt = GeoPoint(view.mapCenter.latitude, view.mapCenter.longitude) to view.zoomLevelDouble
        moved = false
        if (target == Framing.NEARBY) framedKey[0] = frameKey
    }
    val reframe by rememberUpdatedState { view: MapView ->
        val payload = focusedContact?.lastPayload
        if (payload == null) {
            show(view, Framing.NEARBY)
        } else {
            frameAround(view, GeoPoint(payload.latitude, payload.longitude), payload.accuracy)
            framedAt = GeoPoint(view.mapCenter.latitude, view.mapCenter.longitude) to view.zoomLevelDouble
            moved = false
            framedKey[0] = frameKey
        }
    }
    LaunchedEffect(state.contacts) {
        while (true) {
            estimates = estimates.filterKeys { id -> state.contacts.any { it.id == id } }
            state.contacts.forEach { contact ->
                val estimate = contact.lastPayload?.let { model.roads.estimate(it, System.currentTimeMillis() / 1000) }
                estimates = if (estimate == null) estimates - contact.id else estimates + (contact.id to estimate)
            }
            delay(ESTIMATE_REFRESH_MILLIS)
        }
    }
    val markerSize = with(LocalDensity.current) { 44.dp.roundToPx() }

    Box(modifier.fillMaxSize()) {
        // osmdroid draws the partial tiles at its edges outside its bounds; Compose does not clip a hosted view.
        AndroidView(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(12.0)
                    controller.setCenter(nearby.firstOrNull() ?: GeoPoint(60.17, 24.94))
                    addOnFirstLayoutListener { _, _, _, _, _ -> reframe(this) }
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?) = noteMove(this@apply)
                        override fun onZoom(event: ZoomEvent?) = noteMove(this@apply)

                        private fun noteMove(view: MapView): Boolean {
                            val (center, zoom) = framedAt ?: return false
                            moved = abs(view.mapCenter.latitude - center.latitude) > 1e-5 ||
                                abs(view.mapCenter.longitude - center.longitude) > 1e-5 ||
                                abs(view.zoomLevelDouble - zoom) > 1e-3
                            return false
                        }
                    })
                    onResume()
                    map[0] = this
                }
            },
            update = { view ->
                view.overlayManager.tilesOverlay.setColorFilter(if (dark) DARK_TILES else null)
                view.overlayManager.tilesOverlay.loadingBackgroundColor = if (dark) DARK_LOADING else LIGHT_LOADING
                view.overlays.clear()
                focusedContact?.lastPayload?.let { payload ->
                    view.overlays += Polygon(view).apply {
                        setPoints(Polygon.pointsAsCircle(GeoPoint(payload.latitude, payload.longitude), payload.accuracy.toDouble()))
                        fillPaint.color = FOCUS_FILL
                        outlinePaint.color = Color.WHITE
                        outlinePaint.strokeWidth = markerSize / 14f
                        infoWindow = null
                    }
                }
                located.forEach { contact ->
                    val payload = contact.lastPayload ?: return@forEach
                    val avatar = avatarBitmap(contact, model.photo(contact), markerSize)
                    estimates[contact.id]?.let { estimate ->
                        view.overlays += Polyline(view).apply {
                            setPoints(estimate.path.map { GeoPoint(it.latitude, it.longitude) })
                            outlinePaint.color = ESTIMATE_COLOUR
                            outlinePaint.strokeWidth = markerSize / 8f
                            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(markerSize / 4f, markerSize / 5f), 0f)
                        }
                        view.overlays += Marker(view).apply {
                            position = GeoPoint(estimate.point.latitude, estimate.point.longitude)
                            icon = BitmapDrawable(view.resources, avatar)
                            alpha = 0.45f
                            title = "${contact.name}, estimated"
                            snippet = "Guessed along the road from the last fix, ${movement(payload)}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                    }
                    view.overlays += Marker(view).apply {
                        position = GeoPoint(payload.latitude, payload.longitude)
                        icon = BitmapDrawable(view.resources, avatar)
                        title = contact.name
                        snippet = status(contact, placeNames[contact.id])
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }
                state.lastFix?.let { fix ->
                    view.overlays += Marker(view).apply {
                        position = GeoPoint(fix.latitude, fix.longitude)
                        icon = BitmapDrawable(view.resources, ownPositionBitmap(markerSize / 2))
                        title = "You"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }
                view.invalidate()
                if (framedKey[0] != frameKey && view.width > 0) reframe(view)
            },
            onRelease = { view -> view.onDetach() },
        )
        val widen = focusedContact == null && someoneAbroad && framing == Framing.NEARBY && !moved
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Button(
                onClick = { if (focusedContact != null) onUnfocus() else map[0]?.let { show(it, if (widen) Framing.EVERYONE else Framing.NEARBY) } },
            ) { Text(if (someoneAbroad && !widen) countryName(home) else "Everyone") }
            focusedContact?.let { contact ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact, model.photo(contact), 44.dp, Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.titleMedium)
                            Text(status(contact, placeNames[contact.id]), style = MaterialTheme.typography.bodySmall)
                        }
                        distance(contact, state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        IconButton(onClick = onUnfocus) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    }
                }
            }
        }
    }
}

private enum class Framing { NEARBY, EVERYONE }

private const val ESTIMATE_REFRESH_MILLIS = 10_000L
private const val ESTIMATE_COLOUR = 0xCC5E35B1.toInt()
private const val FOCUS_FILL = 0x402979FF
private const val LIGHT_LOADING = 0xFFD8D0D0.toInt()
private const val DARK_LOADING = 0xFF202124.toInt()

/** Inverted tiles with the hue turned back 180 degrees: dark land, while water stays blue and parks green. */
private val DARK_TILES = ColorMatrixColorFilter(
    floatArrayOf(
        0.574f, -1.43f, -0.144f, 0f, 255f,
        -0.426f, -0.43f, -0.144f, 0f, 255f,
        -0.426f, -1.43f, 0.856f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private fun Contact.position() = lastPayload!!.let { LatLon(it.latitude, it.longitude) }

/** The contacts and this phone that pass [keep], each contact with its estimated position too. */
private fun points(
    located: List<Contact>,
    own: LatLon?,
    estimates: Map<String, EstimateResult.Estimate>,
    keep: (LatLon) -> Boolean,
): List<GeoPoint> =
    (located.filter { keep(it.position()) }.flatMap { listOfNotNull(it.position(), estimates[it.id]?.point) } + listOfNotNull(own?.takeIf(keep)))
        .map { GeoPoint(it.latitude, it.longitude) }

private fun countryName(code: String?): String =
    code?.let { runCatching { Locale.Builder().setRegion(it).build().displayCountry }.getOrNull() }?.ifBlank { null } ?: "Home"

/** Frames a focused contact with the circle of its accuracy, and at least 800 m across. */
private fun frameAround(map: MapView, point: GeoPoint, accuracy: Int) {
    val half = maxOf(accuracy * 1.5, 400.0)
    val dLat = half / 110_574.0
    val dLon = half / (cos(Math.toRadians(point.latitude)) * 111_320.0)
    map.zoomToBoundingBox(BoundingBox(point.latitude + dLat, point.longitude + dLon, point.latitude - dLat, point.longitude - dLon), false)
}

private fun frame(map: MapView, points: List<GeoPoint>) {
    when (points.size) {
        0 -> return
        1 -> {
            map.controller.setZoom(15.0)
            map.controller.setCenter(points.first())
        }
        else -> map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 96)
    }
}
