package app.mismeet.android.ui

import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.mismeet.android.AppModel
import app.mismeet.protocol.EstimateResult
import java.io.File
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap tiles through osmdroid: no Google dependency, and the tile policy wants a user
 * agent. Opens framed around everyone with a known position, like Find My.
 */
@Composable
fun MapScreen(model: AppModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        true
    }
    var estimates by remember { mutableStateOf<Map<String, EstimateResult.Estimate>>(emptyMap()) }
    val points = state.contacts.mapNotNull { contact -> contact.lastPayload?.let { GeoPoint(it.latitude, it.longitude) } } +
        estimates.values.map { GeoPoint(it.point.latitude, it.point.longitude) } +
        listOfNotNull(state.lastFix?.let { GeoPoint(it.latitude, it.longitude) })
    val framingKey = state.contacts.filter { it.lastPayload != null }.joinToString(",") { it.id } + ":" + (state.lastFix != null)
    val framed = remember { arrayOf("") }
    val map = remember { arrayOfNulls<MapView>(1) }
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
                    controller.setCenter(points.firstOrNull() ?: GeoPoint(60.17, 24.94))
                    addOnFirstLayoutListener { _, _, _, _, _ -> frame(this, points) }
                    onResume()
                    map[0] = this
                }
            },
            update = { view ->
                view.overlays.clear()
                state.contacts.forEach { contact ->
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
                        snippet = "Seen ${DateUtils.getRelativeTimeSpanString(payload.fixTime * 1000)}, within ${payload.accuracy} m" +
                            movement(payload)?.let { ", $it" }.orEmpty()
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
                if (framed[0] != framingKey && view.width > 0) {
                    framed[0] = framingKey
                    frame(view, points)
                }
            },
            onRelease = { view -> view.onDetach() },
        )
        Button(
            onClick = { map[0]?.let { frame(it, points) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("Everyone") }
    }
}

private const val ESTIMATE_REFRESH_MILLIS = 10_000L
private const val ESTIMATE_COLOUR = 0xCC5E35B1.toInt()

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
