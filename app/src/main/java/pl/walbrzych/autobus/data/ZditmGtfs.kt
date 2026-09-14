package pl.walbrzych.autobus.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import androidx.core.content.edit
import com.google.transit.realtime.GtfsRealtime
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Official ZDiTM Szczecin GTFS and GTFS-Realtime sources. */
internal object ZditmGtfsEndpoints {
    const val STATIC = "https://www.zditm.szczecin.pl/storage/gtfs/gtfs.zip"
    const val TRIP_UPDATES = "https://www.zditm.szczecin.pl/storage/gtfs/gtfs-rt-trips.pb"
    const val VEHICLES = "https://www.zditm.szczecin.pl/storage/gtfs/gtfs-rt-vehicles.pb"
}

internal data class GtfsResponse(
    val bytes: ByteArray?,
    val eTag: String?,
    val lastModified: String?,
    val notModified: Boolean,
)

/** Cancellable, bounded client for the public ZDiTM files. */
internal class ZditmGtfsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun schedule(eTag: String?, lastModified: String?): GtfsResponse =
        get(ZditmGtfsEndpoints.STATIC, eTag, lastModified)

    suspend fun tripUpdates(): GtfsRealtime.FeedMessage =
        GtfsRealtime.FeedMessage.parseFrom(get(ZditmGtfsEndpoints.TRIP_UPDATES, null, null).bytes)

    suspend fun vehicles(): GtfsRealtime.FeedMessage =
        GtfsRealtime.FeedMessage.parseFrom(get(ZditmGtfsEndpoints.VEHICLES, null, null).bytes)

    private suspend fun get(url: String, eTag: String?, lastModified: String?): GtfsResponse {
        var lastFailure: IOException? = null
        repeat(2) { attempt ->
            try {
                return execute(url, eTag, lastModified)
            } catch (failure: ZditmHttpException) {
                if (failure.code !in 500..599 || attempt == 1) throw failure
                lastFailure = failure
            } catch (failure: IOException) {
                if (attempt == 1) throw failure
                lastFailure = failure
            }
            delay(250)
        }
        throw lastFailure ?: IOException("Nie udało się pobrać danych ZDiTM.")
    }

    private suspend fun execute(url: String, eTag: String?, lastModified: String?): GtfsResponse =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url).header("User-Agent", "autoBus/1.1")
                .apply {
                    eTag?.takeIf(String::isNotBlank)?.let { header("If-None-Match", it) }
                    lastModified?.takeIf(String::isNotBlank)?.let { header("If-Modified-Since", it) }
                }
                .get()
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { reply ->
                        val metadata = { bytes: ByteArray?, unchanged: Boolean ->
                            GtfsResponse(bytes, reply.header("ETag"), reply.header("Last-Modified"), unchanged)
                        }
                        when {
                            reply.code == 304 -> if (continuation.isActive) continuation.resume(metadata(null, true))
                            !reply.isSuccessful -> if (continuation.isActive) continuation.resumeWithException(
                                ZditmHttpException(reply.code, "HTTP ${reply.code} z ZDiTM."),
                            )
                            else -> {
                                val body = try {
                                    reply.body?.bytes() ?: throw IOException("ZDiTM zwrócił pustą odpowiedź.")
                                } catch (error: IOException) {
                                    if (continuation.isActive) continuation.resumeWithException(error)
                                    return
                                }
                                if (continuation.isActive) continuation.resume(metadata(body, false))
                            }
                        }
                    }
                }
            })
        }
}

private class ZditmHttpException(val code: Int, message: String) : IOException(message)

internal data class GtfsTripAtStop(
    val tripId: String,
    val departureSeconds: Int,
    val stopSequence: Int,
    val line: String,
    val direction: String,
    val directionCode: String,
    val variant: String,
)

internal data class GtfsTripMetadata(
    val line: String,
    val direction: String,
    val directionCode: String,
    val variant: String,
)

/**
 * A separate two-version cache for GTFS. The preference pointer is committed only
 * after the staged SQLite database has passed a complete SQLite and schema check.
 */
internal class GtfsScheduleStore(context: Context, private val cityId: Int) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("zditm_gtfs_schedule_$cityId", Context.MODE_PRIVATE)

    fun cachedSnapshot(): ScheduleSnapshot? = activeFile()?.let { file ->
        runCatching { read(file, lastUpdate()) }.getOrNull()
    }

    fun activeIntegrity(): ScheduleIntegrityReport? = activeFile()?.let { file ->
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(::verify)
        }.getOrNull()
    }

    fun isCurrent(response: GtfsResponse): Boolean = response.notModified ||
        (response.eTag != null && response.eTag == preferences.getString(ETAG, null)) ||
        (response.lastModified != null && response.lastModified == preferences.getString(LAST_MODIFIED, null))

    fun requestValidators(): Pair<String?, String?> =
        preferences.getString(ETAG, null) to preferences.getString(LAST_MODIFIED, null)

    fun install(zipPayload: ByteArray, metadata: GtfsResponse): ScheduleSnapshot {
        require(zipPayload.isNotEmpty()) { "Pobrany GTFS jest pusty." }
        val staging = File(appContext.filesDir, "zditm-gtfs-$cityId-staging-${UUID.randomUUID()}.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(staging, null).use { database -> buildDatabase(database, zipPayload) }
            val now = Instant.now()
            val snapshot = read(staging, now)
            val finalName = "zditm-gtfs-$cityId-${snapshot.version.version}-${UUID.randomUUID()}.db"
            val finalFile = File(appContext.filesDir, finalName)
            require(staging.renameTo(finalFile)) { "Nie można zatwierdzić pobranego GTFS." }
            preferences.edit(commit = true) {
                putString(ACTIVE_FILE, finalName)
                putLong(LAST_UPDATED, now.toEpochMilli())
                metadata.eTag?.let { putString(ETAG, it) } ?: remove(ETAG)
                metadata.lastModified?.let { putString(LAST_MODIFIED, it) } ?: remove(LAST_MODIFIED)
            }
            appContext.filesDir.listFiles()?.asSequence()
                ?.filter { it.name.startsWith("zditm-gtfs-$cityId-") && it.name.endsWith(".db") && it.name != finalName }
                ?.forEach(File::delete)
            return snapshot
        } finally {
            if (staging.exists()) staging.delete()
        }
    }

    fun tripsAtStop(stopId: String, tripIds: Collection<String>): Map<String, GtfsTripAtStop> {
        if (tripIds.isEmpty()) return emptyMap()
        val file = activeFile() ?: return emptyMap()
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            buildMap {
                tripIds.distinct().chunked(SQLITE_ARGUMENT_LIMIT - 1).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    val args = arrayOf(stopId, *chunk.toTypedArray())
                    database.rawQuery(
                        """
                        SELECT st.trip_id, st.departure_seconds, st.sequence, r.line, t.headsign, t.direction_id, t.shape_id
                        FROM stop_times st
                        JOIN trips t ON t.id=st.trip_id
                        JOIN routes r ON r.id=t.route_id
                        WHERE st.stop_id=? AND st.trip_id IN ($placeholders)
                        """.trimIndent(),
                        args,
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            put(cursor.getString(0), GtfsTripAtStop(
                                tripId = cursor.getString(0),
                                departureSeconds = cursor.getInt(1),
                                stopSequence = cursor.getInt(2),
                                line = cursor.getString(3),
                                direction = cursor.getString(4).ifBlank { "Brak opisu kierunku" },
                                directionCode = cursor.getString(5),
                                variant = cursor.getString(6),
                            ))
                        }
                    }
                }
            }
        }
    }

    fun tripMetadata(tripIds: Collection<String>): Map<String, GtfsTripMetadata> {
        if (tripIds.isEmpty()) return emptyMap()
        val file = activeFile() ?: return emptyMap()
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            buildMap {
                tripIds.distinct().chunked(SQLITE_ARGUMENT_LIMIT).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    database.rawQuery(
                        """
                        SELECT t.id,r.line,t.headsign,t.direction_id,t.shape_id
                        FROM trips t JOIN routes r ON r.id=t.route_id
                        WHERE t.id IN ($placeholders)
                        """.trimIndent(), chunk.toTypedArray(),
                    ).use { cursor ->
                        while (cursor.moveToNext()) put(cursor.getString(0), GtfsTripMetadata(
                            line = cursor.getString(1), direction = cursor.getString(2).ifBlank { "Brak opisu kierunku" },
                            directionCode = cursor.getString(3), variant = cursor.getString(4),
                        ))
                    }
                }
            }
        }
    }

    private fun activeFile(): File? = preferences.getString(ACTIVE_FILE, null)
        ?.let { File(appContext.filesDir, it) }
        ?.takeIf(File::isFile)

    private fun lastUpdate(): Instant = Instant.ofEpochMilli(preferences.getLong(LAST_UPDATED, 0))

    private fun buildDatabase(database: SQLiteDatabase, zip: ByteArray) {
        database.execSQL("PRAGMA journal_mode=DELETE")
        listOf(
            "CREATE TABLE stops(id TEXT PRIMARY KEY, name TEXT NOT NULL, code TEXT, latitude REAL NOT NULL, longitude REAL NOT NULL)",
            "CREATE TABLE routes(id TEXT PRIMARY KEY, line TEXT NOT NULL)",
            "CREATE TABLE trips(id TEXT PRIMARY KEY, route_id TEXT NOT NULL, service_id TEXT NOT NULL, headsign TEXT NOT NULL, direction_id TEXT NOT NULL, shape_id TEXT NOT NULL)",
            "CREATE TABLE stop_times(trip_id TEXT NOT NULL, stop_id TEXT NOT NULL, departure_seconds INTEGER NOT NULL, sequence INTEGER NOT NULL)",
            "CREATE TABLE route_stops(trip_id TEXT PRIMARY KEY, stops TEXT NOT NULL)",
            "CREATE TABLE services(id TEXT PRIMARY KEY, label TEXT NOT NULL, sort_order INTEGER NOT NULL)",
            "CREATE TABLE calendar(service_date TEXT PRIMARY KEY, active_services TEXT NOT NULL)",
            "CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        ).forEach(database::execSQL)
        database.beginTransaction()
        try {
            parseStops(database, zip)
            parseRoutes(database, zip)
            parseTrips(database, zip)
            parseStopTimes(database, zip)
            parseCalendar(database, zip)
            database.execSQL("""
                INSERT INTO route_stops(trip_id, stops)
                SELECT trip_id, group_concat(stop_id, '|')
                FROM (SELECT trip_id, stop_id FROM stop_times ORDER BY trip_id, sequence)
                GROUP BY trip_id
            """.trimIndent())
            database.execSQL("CREATE INDEX stop_times_stop ON stop_times(stop_id)")
            database.execSQL("CREATE INDEX stop_times_trip ON stop_times(trip_id)")
            database.execSQL("CREATE INDEX trips_service ON trips(service_id)")
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun parseStops(database: SQLiteDatabase, zip: ByteArray) = database.withStatement("INSERT OR REPLACE INTO stops VALUES(?,?,?,?,?)") { statement ->
        csvRows(zip, "stops.txt") { row ->
            val id = row["stop_id"].orEmpty().trim()
            val name = row["stop_name"].orEmpty().trim()
            val latitude = row["stop_lat"].orEmpty().toDoubleOrNull()
            val longitude = row["stop_lon"].orEmpty().toDoubleOrNull()
            val validLatitude = latitude ?: return@csvRows
            val validLongitude = longitude ?: return@csvRows
            if (id.isNotEmpty() && name.isNotEmpty() && validLatitude in -90.0..90.0 && validLongitude in -180.0..180.0) {
                statement.useBindings(id, name, row["stop_code"].orEmpty(), validLatitude, validLongitude).executeInsert()
            }
        }
    }

    private fun parseRoutes(database: SQLiteDatabase, zip: ByteArray) = database.withStatement("INSERT OR REPLACE INTO routes VALUES(?,?)") { statement ->
        csvRows(zip, "routes.txt") { row ->
            val id = row["route_id"].orEmpty().trim()
            val line = row["route_short_name"].orEmpty().trim()
            if (id.isNotEmpty() && line.isNotEmpty()) statement.useBindings(id, line).executeInsert()
        }
    }

    private fun parseTrips(database: SQLiteDatabase, zip: ByteArray) = database.withStatement("INSERT OR REPLACE INTO trips VALUES(?,?,?,?,?,?)") { statement ->
        csvRows(zip, "trips.txt") { row ->
            val id = row["trip_id"].orEmpty().trim()
            val routeId = row["route_id"].orEmpty().trim()
            val serviceId = row["service_id"].orEmpty().trim()
            if (id.isNotEmpty() && routeId.isNotEmpty() && serviceId.isNotEmpty()) {
                statement.useBindings(id, routeId, serviceId, row["trip_headsign"].orEmpty().trim(),
                    row["direction_id"].orEmpty().trim(), row["shape_id"].orEmpty().trim()).executeInsert()
            }
        }
    }

    private fun parseStopTimes(database: SQLiteDatabase, zip: ByteArray) = database.withStatement("INSERT INTO stop_times VALUES(?,?,?,?)") { statement ->
        csvRows(zip, "stop_times.txt") { row ->
            val tripId = row["trip_id"].orEmpty().trim()
            val stopId = row["stop_id"].orEmpty().trim()
            val departure = parseGtfsTimeSeconds(row["departure_time"])
            val sequence = row["stop_sequence"]?.trim()?.toIntOrNull()
            if (tripId.isNotEmpty() && stopId.isNotEmpty() && departure != null && sequence != null) {
                statement.useBindings(tripId, stopId, departure, sequence).executeInsert()
            }
        }
    }

    private fun parseCalendar(database: SQLiteDatabase, zip: ByteArray) {
        data class Rule(val id: String, val days: BooleanArray, val from: LocalDate, val to: LocalDate)
        val rules = mutableListOf<Rule>()
        csvRows(zip, "calendar.txt") { row ->
            val id = row["service_id"].orEmpty().trim()
            val from = parseGtfsDate(row["start_date"])
            val to = parseGtfsDate(row["end_date"])
            val days = BooleanArray(7) { index -> row[DAY_COLUMNS[index]] == "1" }
            if (id.isNotEmpty() && from != null && to != null && !to.isBefore(from)) rules += Rule(id, days, from, to)
        }
        val active = linkedMapOf<LocalDate, MutableSet<String>>()
        rules.forEach { rule ->
            var date = rule.from
            while (!date.isAfter(rule.to)) {
                if (rule.days[date.dayOfWeek.value - 1]) active.getOrPut(date, ::linkedSetOf).add(rule.id)
                date = date.plusDays(1)
            }
        }
        csvRows(zip, "calendar_dates.txt") { row ->
            val id = row["service_id"].orEmpty().trim()
            val date = parseGtfsDate(row["date"])
            when (row["exception_type"]?.trim()?.toIntOrNull()) {
                1 -> if (id.isNotEmpty() && date != null) active.getOrPut(date, ::linkedSetOf).add(id)
                2 -> if (id.isNotEmpty() && date != null) active[date]?.remove(id)
            }
        }
        val serviceIds = linkedSetOf<String>().apply { rules.forEach { add(it.id) }; active.values.forEach(::addAll) }
        database.withStatement("INSERT OR REPLACE INTO services VALUES(?,?,?)") { statement ->
            serviceIds.forEachIndexed { index, id -> statement.useBindings(id, "Usługa $id", index).executeInsert() }
        }
        database.withStatement("INSERT OR REPLACE INTO calendar VALUES(?,?)") { statement ->
            active.filterValues { it.isNotEmpty() }.forEach { (date, ids) -> statement.useBindings(date.toString(), ids.joinToString(SERVICE_CODE_SEPARATOR)).executeInsert() }
        }
        val version = active.keys.maxOrNull()?.toString().orEmpty()
        database.withStatement("INSERT OR REPLACE INTO metadata VALUES(?,?)") { it.useBindings("version", version).executeInsert() }
    }

    private fun read(file: File, updated: Instant): ScheduleSnapshot =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val report = verify(database)
            val services = database.rawQuery("SELECT id,label,sort_order FROM services ORDER BY sort_order", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(ServiceDay(cursor.getString(0), cursor.getString(1), cursor.getInt(2))) }
            }
            val calendar = database.rawQuery("SELECT service_date,active_services FROM calendar", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(LocalDate.parse(cursor.getString(0)), cursor.getString(1)) }
            }
            val tablesByStop = linkedMapOf<String, MutableList<TimetableData>>()
            database.rawQuery(
                """
                SELECT st.stop_id, st.sequence, t.service_id, r.line, t.headsign, t.direction_id, t.shape_id,
                       group_concat(st.departure_seconds, ','), max(rs.stops)
                FROM stop_times st
                JOIN trips t ON t.id=st.trip_id
                JOIN routes r ON r.id=t.route_id
                JOIN route_stops rs ON rs.trip_id=st.trip_id
                GROUP BY st.stop_id, st.sequence, t.service_id, r.line, t.headsign, t.direction_id, t.shape_id
                """.trimIndent(), null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val seconds = cursor.getString(7).split(',').mapNotNull(String::toIntOrNull).sorted()
                    if (seconds.isEmpty()) continue
                    val stopId = cursor.getString(0)
                    tablesByStop.getOrPut(stopId, ::mutableListOf).add(TimetableData(
                        line = cursor.getString(3), direction = cursor.getString(4).ifBlank { "Brak opisu kierunku" },
                        dayType = DayType.WORKING, times = seconds.map(::gtfsTimeText), serviceDayCode = cursor.getString(2),
                        serviceDayLabel = "Usługa ${cursor.getString(2)}", variant = cursor.getString(6),
                        directionCode = cursor.getString(5), routeStopIds = cursor.getString(8).split('|').filter(String::isNotEmpty),
                        stopOrder = cursor.getInt(1), departureSeconds = seconds,
                    ))
                }
            }
            val stops = database.rawQuery("SELECT id,name,code,latitude,longitude FROM stops ORDER BY name COLLATE NOCASE", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(StopData(
                        id = cursor.getString(0), name = cursor.getString(1), publicNumber = cursor.getString(2).ifBlank { null },
                        latitude = cursor.getDouble(3), longitude = cursor.getDouble(4),
                        timetables = tablesByStop[cursor.getString(0)].orEmpty(),
                    ))
                }
            }
            ScheduleSnapshot(stops, services, calendar, report.version, updated)
        }

    private fun verify(database: SQLiteDatabase): ScheduleIntegrityReport {
        require(database.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) { "GTFS: PRAGMA quick_check nie przeszedł." }
        require(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) { "GTFS: PRAGMA integrity_check nie przeszedł." }
            val names = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            require(names.containsAll(REQUIRED_TABLES)) { "GTFS: brakuje wymaganych tabel." }
            val stops = database.scalarCount("stops")
            val lines = database.rawQuery("SELECT count(DISTINCT line) FROM routes", null).use { it.moveToFirst(); it.getInt(0) }
            val variants = database.scalarCount("trips")
            val timetables = database.scalarCount("stop_times")
            ScheduleIntegrityRules.requireCompleteCounts(stops, lines, variants, timetables)
            val orphanStops = database.rawQuery(
                "SELECT count(*) FROM stop_times st LEFT JOIN stops s ON s.id=st.stop_id WHERE s.id IS NULL", null,
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            require(orphanStops == 0) { "GTFS zawiera odjazdy dla nieznanych przystanków." }
            val orphanTrips = database.rawQuery(
                "SELECT count(*) FROM stop_times st LEFT JOIN trips t ON t.id=st.trip_id WHERE t.id IS NULL", null,
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            require(orphanTrips == 0) { "GTFS zawiera czasy nieznanych kursów." }
            val versionText = database.rawQuery("SELECT value FROM metadata WHERE key='version'", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
        return ScheduleIntegrityReport(stops, lines, variants, timetables, ScheduleVersion(
                version = versionText.hashCode().and(Int.MAX_VALUE), validFrom = versionText, generation = 1,
        ))
    }

    private companion object {
        const val ACTIVE_FILE = "active_file"
        const val LAST_UPDATED = "last_updated"
        const val ETAG = "etag"
        const val LAST_MODIFIED = "last_modified"
        const val SQLITE_ARGUMENT_LIMIT = 999
        val DAY_COLUMNS = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val REQUIRED_TABLES = setOf("stops", "routes", "trips", "stop_times", "route_stops", "services", "calendar", "metadata")
    }
}

private fun SQLiteDatabase.scalarCount(table: String): Int = rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
    cursor.moveToFirst(); cursor.getInt(0)
}

private inline fun SQLiteDatabase.withStatement(sql: String, block: (SQLiteStatement) -> Unit) {
    val statement = compileStatement(sql)
    try { block(statement) } finally { statement.close() }
}

private fun SQLiteStatement.useBindings(vararg values: Any?): SQLiteStatement = apply {
    clearBindings()
    values.forEachIndexed { index, value ->
        when (value) {
            null -> bindNull(index + 1)
            is String -> bindString(index + 1, value)
            is Int -> bindLong(index + 1, value.toLong())
            is Long -> bindLong(index + 1, value)
            is Double -> bindDouble(index + 1, value)
            else -> bindString(index + 1, value.toString())
        }
    }
}

private fun csvRows(zip: ByteArray, entryName: String, action: (Map<String, String>) -> Unit) {
    ZipInputStream(ByteArrayInputStream(zip)).use { input ->
        var entry = input.nextEntry
        while (entry != null && entry.name != entryName) entry = input.nextEntry
        require(entry != null) { "GTFS nie zawiera $entryName." }
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            val header = reader.readLine()?.removePrefix("\uFEFF")?.let(::parseGtfsCsvLine)
                ?: throw IllegalArgumentException("GTFS $entryName nie ma nagłówka.")
            reader.lineSequence().filter(String::isNotBlank).forEach { line ->
                val values = parseGtfsCsvLine(line)
                action(header.indices.associate { index -> header[index] to values.getOrElse(index) { "" } })
            }
        }
    }
}

/** CSV parser for GTFS' RFC-4180-style quoted fields, including escaped quotes. */
internal fun parseGtfsCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        when (val character = line[index]) {
            '"' -> if (quoted && line.getOrNull(index + 1) == '"') { current.append('"'); index++ } else quoted = !quoted
            ',' -> if (quoted) current.append(character) else { fields += current.toString(); current.clear() }
            else -> current.append(character)
        }
        index++
    }
    require(!quoted) { "Niepoprawne cudzysłowy w CSV GTFS." }
    fields += current.toString()
    return fields
}

internal fun parseGtfsTimeSeconds(value: String?): Int? {
    val parts = value.orEmpty().trim().split(':')
    if (parts.size != 3) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    val second = parts[2].toIntOrNull() ?: return null
    return (hour.takeIf { it in 0..47 } ?: return null) * 3600 +
        (minute.takeIf { it in 0..59 } ?: return null) * 60 +
        (second.takeIf { it in 0..59 } ?: return null)
}

private fun parseGtfsDate(value: String?): LocalDate? = value?.trim()?.takeIf { it.length == 8 }?.let {
    runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
}

private fun gtfsTimeText(seconds: Int): String = "%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60)

internal class ZditmRealtimeRepository(
    private val store: GtfsScheduleStore,
    private val client: ZditmGtfsClient,
) {
    suspend fun departures(stopId: String): RealTimeDepartures {
        val message = client.tripUpdates()
        val updates = message.entityList.mapNotNull { entity -> entity.takeIf { it.hasTripUpdate() }?.tripUpdate }
            .filter { update -> update.hasTrip() && update.trip.tripId.isNotBlank() }
        val static = store.tripsAtStop(stopId, updates.map { it.trip.tripId })
        val now = Instant.now()
        val zone = ZoneId.of("Europe/Warsaw")
        val departures = updates.mapNotNull { update ->
            val trip = static[update.trip.tripId] ?: return@mapNotNull null
            if (update.trip.scheduleRelationship == GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED) return@mapNotNull null
            val stop = update.stopTimeUpdateList.firstOrNull {
                it.stopId == stopId || (it.hasStopSequence() && it.stopSequence == trip.stopSequence)
            } ?: return@mapNotNull null
            if (stop.scheduleRelationship == GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED) return@mapNotNull null
            val event = sequenceOf(stop.takeIf { it.hasDeparture() }?.departure, stop.takeIf { it.hasArrival() }?.arrival)
                .filterNotNull().firstOrNull()
            val predicted = event?.time?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
                ?: projectedGtfsInstant(trip.departureSeconds, now, zone)?.plusSeconds(event?.delay?.toLong() ?: 0)
            val display = predicted?.let { instant ->
                val minutes = java.time.Duration.between(now, instant).toMinutes()
                if (minutes in 0..29) "$minutes min" else instant.atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            } ?: gtfsTimeText(trip.departureSeconds % (24 * 60 * 60))
            val vehicleNumber = update.takeIf { it.hasVehicle() }?.vehicle?.id?.toIntOrNull()?.takeIf { it > 0 }
            RealTimeDeparture(
                departureId = stableTripNumber(trip.tripId), tripId = stableTripNumber(trip.tripId), line = trip.line,
                direction = trip.direction, directionCode = trip.directionCode.takeIf(String::isNotBlank),
                scheduledSeconds = trip.departureSeconds % (24 * 60 * 60), displayValue = display,
                status = predicted?.let { java.time.Duration.between(now, it).toMinutes().toInt() } ?: 0,
                vehicleNumber = vehicleNumber, n = vehicleNumber ?: 0,
            )
        }.distinctBy { it.departureId }.sortedBy(RealTimeDeparture::scheduledSeconds)
        return RealTimeDepartures(LocalTime.now(zone).format(DateTimeFormatter.ofPattern("HH:mm")), null, departures)
    }

    suspend fun vehicles(): List<LiveVehicle> {
        val vehicleEntities = client.vehicles().entityList.mapNotNull { entity ->
            entity.takeIf { it.hasVehicle() }?.vehicle?.let { entity to it }
        }
        val metadata = store.tripMetadata(vehicleEntities.map { (_, vehicle) ->
            vehicle.takeIf { it.hasTrip() }?.trip?.tripId.orEmpty()
        }.filter(String::isNotBlank))
        return vehicleEntities.mapNotNull { (entity, vehicle) ->
            if (!vehicle.hasPosition() || !vehicle.hasVehicle() || !vehicle.vehicle.id.all(Char::isDigit)) return@mapNotNull null
            val latitude = vehicle.position.latitude.toDouble()
            val longitude = vehicle.position.longitude.toDouble()
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
            val tripId = vehicle.takeIf { it.hasTrip() }?.trip?.tripId.orEmpty()
            val trip = metadata[tripId]
            LiveVehicle(
                vehicleId = entity.id.hashCode().toLong().and(Long.MAX_VALUE), sideNumber = vehicle.vehicle.id.toInt(),
                line = trip?.line.orEmpty(), variant = trip?.variant.orEmpty(),
                directionCode = trip?.directionCode.orEmpty(),
                latitude = latitude, longitude = longitude, predictedLatitude = null, predictedLongitude = null,
                destination = trip?.direction.orEmpty(), reportedAt = if (vehicle.hasTimestamp()) Instant.ofEpochSecond(vehicle.timestamp).atZone(ZoneId.of("Europe/Warsaw")).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) else "",
                activeCourseId = tripId.takeIf(String::isNotBlank)?.let(::stableTripNumber)?.toLong(), status = vehicle.currentStatus.name,
            )
        }
    }

    private fun stableTripNumber(value: String): Int = value.hashCode().and(Int.MAX_VALUE).takeIf { it != 0 } ?: 1
}

/** Selects the service-day occurrence nearest to now, including valid GTFS 24:xx times. */
private fun projectedGtfsInstant(seconds: Int, now: Instant, zone: ZoneId): Instant? {
    if (seconds !in 0 until 48 * 60 * 60) return null
    val currentDate = now.atZone(zone).toLocalDate()
    val time = LocalTime.ofSecondOfDay((seconds % (24 * 60 * 60)).toLong())
    val dayOffset = (seconds / (24 * 60 * 60)).toLong()
    return (-1L..1L)
        .map { serviceOffset -> LocalDateTime.of(currentDate.plusDays(serviceOffset), time).plusDays(dayOffset).atZone(zone).toInstant() }
        .minByOrNull { candidate -> kotlin.math.abs(java.time.Duration.between(now, candidate).seconds) }
}
