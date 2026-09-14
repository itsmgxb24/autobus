package pl.ruby.lubiechowlabs.autobus.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Decodes the archive body, which MyBus returns as a GZIP payload rather than HTTP encoding. */
object ScheduleArchiveDecoder {
    private const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024
    private val sqliteHeader = "SQLite format 3\u0000".encodeToByteArray()

    fun decodeSqlite(gzipPayload: ByteArray): ByteArray {
        require(gzipPayload.size >= 2 && gzipPayload[0] == 0x1f.toByte() && gzipPayload[1] == 0x8b.toByte()) {
            "Pobrany plik nie jest archiwum GZIP."
        }
        val decoded = ByteArrayOutputStream()
        try {
            GZIPInputStream(ByteArrayInputStream(gzipPayload)).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (decoded.size() + count > MAX_DECOMPRESSED_BYTES) {
                        throw IllegalArgumentException("Rozpakowana baza przekracza bezpieczny limit.")
                    }
                    decoded.write(buffer, 0, count)
                }
            }
        } catch (error: IOException) {
            throw IllegalArgumentException("Nie można rozpakować bazy rozkładów.", error)
        }
        return decoded.toByteArray().also {
            require(it.size >= sqliteHeader.size && it.copyOfRange(0, sqliteHeader.size).contentEquals(sqliteHeader)) {
                "Rozpakowany plik nie jest bazą SQLite."
            }
        }
    }
}
