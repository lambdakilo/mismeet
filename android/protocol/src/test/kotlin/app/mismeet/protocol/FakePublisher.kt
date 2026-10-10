package app.mismeet.protocol

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.time.Duration
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.nostrdevkit.sdk.AckPolicy
import org.nostrdevkit.sdk.ClientBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.SendEventTarget

/**
 * Publishes one location for one watching phone from a throwaway key that persists under
 * build/, so the phone adds the publisher once and later runs update the same contact.
 * MISMEET_FAKE_NAME in the environment selects another key, for a second fake contact.
 * Run through the fakePublish Gradle task.
 */
fun main(args: Array<String>) = runBlocking {
    if (args.size < 3) {
        System.err.println("usage: fakePublish <reader invite[,reader invite...]> <lat> <lon> [relay ...]")
        exitProcess(2)
    }
    val readers = args[0].split(",").map { Invite.parse(it).publicKey }
    val latitude = args[1].toDouble()
    val longitude = args[2].toDouble()
    val relays = if (args.size > 3) args.drop(3) else listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

    val name = System.getenv("MISMEET_FAKE_NAME")?.takeIf { it.isNotBlank() } ?: "fake-publisher"
    val keyFile = File("build/$name.key")
    val keys = if (keyFile.exists()) {
        Keys.parse(keyFile.readText().trim())
    } else {
        Keys.generate().also {
            keyFile.parentFile.mkdirs()
            keyFile.writeText(it.secretKey().toHex())
        }
    }
    val createdAtFile = File("build/$name.created-at")
    val previous = createdAtFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
    val now = System.currentTimeMillis() / 1000
    val payload = LocationPayload(fixTime = now, latitude = latitude, longitude = longitude, accuracy = 15, battery = 0.8)
    val location = LocationEventBuilder.build(payload, readers, keys, now, previous)
    val relayList = RelayList.event(relays, keys, now)

    val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
    relays.forEach { client.addRelay(RelayUrl.parse(it), null, false, null) }
    client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
    try {
        for (event in listOf(relayList, location)) {
            val output = client.sendEvent(event, SendEventTarget.broadcast(), AckPolicy.all(), Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), null)
            println("kind ${event.kind().asU16()}: accepted by ${output.success.size} relays, rejected by ${output.failed}")
            if (event.kind().asU16() == ProtocolConstants.LOCATION_KIND && output.success.isNotEmpty()) {
                createdAtFile.writeText(event.createdAt().asSecs().toString())
            }
        }
    } finally {
        client.shutdown()
    }

    val invite = Invite(keys.publicKey(), relays.map { RelayUrl.parse(it) }).uri()
    println()
    println("Published $latitude, $longitude for ${readers.size} reader(s). Add this publisher on the watching phone:")
    println(invite)
    println()
    printQr(invite)
}

private fun printQr(text: String) {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    for (y in 0 until matrix.height) {
        val row = StringBuilder()
        for (x in 0 until matrix.width) row.append(if (matrix[x, y]) "██" else "  ")
        println(row)
    }
}
