package app.mismeet.android.location

import android.content.Context
import app.mismeet.protocol.LatLon
import app.mismeet.protocol.Place
import app.mismeet.protocol.PlaceCell
import app.mismeet.protocol.PlaceNames
import app.mismeet.protocol.ProtocolConstants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Place cells for the names of spec section 8.7, from Overpass and cached in the cache directory. */
class PlaceData(private val context: Context) {
    private val directory = File(context.cacheDir, "places")
    private val cells = mutableMapOf<PlaceCell, List<Place>>()
    private val failedAt = mutableMapOf<PlaceCell, Long>()
    private val lock = Mutex()

    suspend fun name(point: LatLon, nowSeconds: Long): String? = lock.withLock {
        val places = PlaceCell.around(point).flatMap { load(it, nowSeconds) ?: return@withLock null }
        PlaceNames.name(point, places)
    }

    private suspend fun load(cell: PlaceCell, nowSeconds: Long): List<Place>? = withContext(Dispatchers.IO) {
        cells[cell]?.let { return@withContext it }
        if (failedAt[cell]?.let { nowSeconds - it < ProtocolConstants.RATE_LIMIT_BACKOFF_SECONDS } == true) return@withContext null
        val file = File(directory, "${cell.row}_${cell.col}.json")
        val cached = file.takeIf { it.exists() && nowSeconds - it.lastModified() / 1000 < ProtocolConstants.PLACE_CELL_CACHE_SECONDS }
        val places = cached?.let { runCatching { PlaceNames.parse(it.readText()) }.getOrNull() }
            ?: fetchOverpass(context, PlaceNames.query(cell), "place cell ${cell.row}_${cell.col}")?.let { text ->
                runCatching { PlaceNames.parse(text) }.getOrNull()?.also {
                    directory.mkdirs()
                    file.writeText(text)
                }
            }
        if (places == null) failedAt[cell] = nowSeconds else cells[cell] = places
        places
    }
}
