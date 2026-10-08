package app.mismeet.android.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.mismeet.android.AppModel
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** OpenStreetMap tiles through osmdroid: no Google dependency, and the tile policy wants a user agent. */
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
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
                val start = state.lastFix?.let { GeoPoint(it.latitude, it.longitude) }
                    ?: state.contacts.firstNotNullOfOrNull { contact -> contact.lastPayload?.let { GeoPoint(it.latitude, it.longitude) } }
                    ?: GeoPoint(60.17, 24.94)
                controller.setCenter(start)
                onResume()
            }
        },
        update = { map ->
            map.overlays.clear()
            state.contacts.forEach { contact ->
                contact.lastPayload?.let { payload ->
                    map.overlays += Marker(map).apply {
                        position = GeoPoint(payload.latitude, payload.longitude)
                        title = contact.name
                        snippet = "Seen ${DateUtils.getRelativeTimeSpanString(payload.fixTime * 1000)}, within ${payload.accuracy} m"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                }
            }
            state.lastFix?.let { fix ->
                map.overlays += Marker(map).apply {
                    position = GeoPoint(fix.latitude, fix.longitude)
                    title = "You"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            }
            map.invalidate()
        },
        onRelease = { map -> map.onDetach() },
    )
}
