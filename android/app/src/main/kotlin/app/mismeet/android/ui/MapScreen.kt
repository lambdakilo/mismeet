package app.mismeet.android.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.mismeet.android.AppModel
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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
    val points = state.contacts.mapNotNull { contact -> contact.lastPayload?.let { GeoPoint(it.latitude, it.longitude) } } +
        listOfNotNull(state.lastFix?.let { GeoPoint(it.latitude, it.longitude) })
    val framingKey = state.contacts.filter { it.lastPayload != null }.joinToString(",") { it.id } + ":" + (state.lastFix != null)
    val framed = remember { arrayOf("") }
    val map = remember { arrayOfNulls<MapView>(1) }

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
                    contact.lastPayload?.let { payload ->
                        view.overlays += Marker(view).apply {
                            position = GeoPoint(payload.latitude, payload.longitude)
                            title = contact.name
                            snippet = "Seen ${DateUtils.getRelativeTimeSpanString(payload.fixTime * 1000)}, within ${payload.accuracy} m"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    }
                }
                state.lastFix?.let { fix ->
                    view.overlays += Marker(view).apply {
                        position = GeoPoint(fix.latitude, fix.longitude)
                        title = "You"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
