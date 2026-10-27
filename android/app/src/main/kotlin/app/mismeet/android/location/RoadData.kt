package app.mismeet.android.location

import android.content.Context
import android.util.Log
import app.mismeet.protocol.EstimateResult
import app.mismeet.protocol.LatLon
import app.mismeet.protocol.LocationPayload
import app.mismeet.protocol.Overpass
import app.mismeet.protocol.ProtocolConstants
import app.mismeet.protocol.RoadCell
import app.mismeet.protocol.RoadEstimator
import app.mismeet.protocol.RoadNetwork
import app.mismeet.protocol.RoadWay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Road cells for the estimates of spec section 8.6, from Overpass and cached in the cache directory. */
class RoadData(private val context: Context) {
    private val directory = File(context.cacheDir, "roads")
    private val cells = LinkedHashMap<RoadCell, List<RoadWay>>()
    private val failedAt = mutableMapOf<RoadCell, Long>()
    private var network = RoadNetwork(emptyList())
    private val lock = Mutex()

    suspend fun estimate(payload: LocationPayload, nowSeconds: Long): EstimateResult.Estimate? = lock.withLock { estimateLocked(payload, nowSeconds) }

    private suspend fun estimateLocked(payload: LocationPayload, nowSeconds: Long): EstimateResult.Estimate? {
        val fix = LatLon(payload.latitude, payload.longitude)
        val skipped = mutableSetOf<RoadCell>()
        var loads = 0
        while (true) {
            val unavailable = skipped + failedAt.filterValues { nowSeconds - it < ProtocolConstants.RATE_LIMIT_BACKOFF_SECONDS }.keys
            when (val result = RoadEstimator.estimate(fix, payload.speed, payload.course, nowSeconds - payload.fixTime, network, cells.keys, unavailable)) {
                is EstimateResult.Estimate -> return result
                EstimateResult.None -> return null
                is EstimateResult.NeedsCell ->
                    if (loads < ProtocolConstants.ROAD_CELLS_PER_ESTIMATE && load(result.cell, nowSeconds)) loads++ else skipped += result.cell
            }
        }
    }

    private suspend fun load(cell: RoadCell, nowSeconds: Long): Boolean = withContext(Dispatchers.IO) {
        val file = File(directory, "${cell.row}_${cell.col}.json")
        val cached = file.takeIf { it.exists() && nowSeconds - it.lastModified() / 1000 < ProtocolConstants.ROAD_CELL_CACHE_SECONDS }
        val ways = cached?.let { runCatching { Overpass.parse(it.readText()) }.getOrNull() }
            ?: fetchOverpass(context, Overpass.query(cell), "road cell ${cell.row}_${cell.col}")?.let { text ->
                runCatching { Overpass.parse(text) }.getOrNull()?.also {
                    directory.mkdirs()
                    file.writeText(text)
                }
            }
        if (ways == null) {
            failedAt[cell] = nowSeconds
            return@withContext false
        }
        cells[cell] = ways
        while (cells.size > MAX_CELLS_IN_MEMORY) cells.remove(cells.keys.first())
        network = RoadNetwork(cells.values.flatten())
        true
    }

    private companion object {
        const val MAX_CELLS_IN_MEMORY = 48
    }
}

/** Posts a query to Overpass, null unless it answers 200; [what] names the cell in debug logs. */
internal fun fetchOverpass(context: Context, query: String, what: String): String? = runCatching {
    val connection = URL(Overpass.URL).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 90_000
        connection.setRequestProperty("User-Agent", "${context.packageName} (https://github.com/zululima/mismeet)")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
        if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            // The cell says roughly where a contact is, so it stays at debug level.
            Log.d("Mismeet", "Overpass answered ${connection.responseCode} for $what")
            null
        }
    } finally {
        connection.disconnect()
    }
}.onFailure { Log.d("Mismeet", "Overpass failed for $what: $it") }.getOrNull()
