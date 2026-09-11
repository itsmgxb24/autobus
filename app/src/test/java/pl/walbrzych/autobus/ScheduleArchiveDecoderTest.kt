package pl.walbrzych.autobus

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import pl.walbrzych.autobus.data.ScheduleArchiveDecoder

class ScheduleArchiveDecoderTest {
    @Test
    fun decompressesSqlitePayload() {
        val sqlite = "SQLite format 3\u0000fixture".encodeToByteArray()
        assertArrayEquals(sqlite, ScheduleArchiveDecoder.decodeSqlite(gzip(sqlite)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonGzipResponse() {
        ScheduleArchiveDecoder.decodeSqlite("<html>error</html>".encodeToByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGzipThatIsNotSqlite() {
        ScheduleArchiveDecoder.decodeSqlite(gzip("not a database".encodeToByteArray()))
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }
}
