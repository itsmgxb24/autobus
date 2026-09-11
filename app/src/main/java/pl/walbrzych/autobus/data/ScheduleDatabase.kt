package pl.walbrzych.autobus.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ScheduleIntegrityReport(
    val stopCount: Int,
    val lineCount: Int,
    val variantCount: Int,
    val timetableCount: Int,
    val version: ScheduleVersion,
)

/** Reads the MyBus SQLite schema verified against live downloaded schedule files. */
class ScheduleDatabaseReader {
    fun read(file: File, lastSuccessfulUpdate: Instant): ScheduleSnapshot {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val report = verify(database)
            val serviceDays = database.queryRows(
                "DNI", arrayOf("typ_dnia", "opis_dnia", "kolej_wydr"), null, null, null, null, "kolej_wydr",
            ) { cursor ->
                ServiceDay(cursor.string("typ_dnia"), cursor.string("opis_dnia"), cursor.int("kolej_wydr"))
            }
            val daysByCode = serviceDays.associateBy { it.code }
            val calendar = database.rawRows(
                "SELECT td_rj, dt_kal FROM KALENDARZ",
            ) { cursor ->
                val rawDate = cursor.string("dt_kal").take(10)
                LocalDate.parse(rawDate) to cursor.string("td_rj")
            }.toMap()
            val rawStops = database.queryRows(
                "PRZYSTANKI", arrayOf("id", "nazwa", "numer", "lat", "lon"), null, null, null, null, "id",
            ) { cursor ->
                RawStop(
                    id = cursor.int("id").toString(),
                    name = cursor.string("nazwa").trim(),
                    publicNumber = cursor.stringOrNull("numer")?.trim()?.takeIf(String::isNotEmpty),
                    latitude = cursor.double("lat"),
                    longitude = cursor.double("lon"),
                )
            }
            val timetablesByStop = rawStops.associate { it.id to mutableListOf<TimetableData>() }
            database.rawQuery(
                """
                SELECT o.bus_stop_id, o.typ_dnia, o.numer_lini, o.war_trasy, o.odjazdy, o.id_krn, o.lp_przyst,
                       k.opis_tabl, k.kierunek, k.trasa
                FROM ODJAZDY o
                LEFT JOIN KIERUNKI k ON k.numer = o.numer_lini
                    AND k.war_trasy = o.war_trasy AND k.id_krn = o.id_krn
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val stopId = cursor.int("bus_stop_id").toString()
                    val code = cursor.string("typ_dnia")
                    val serviceDay = daysByCode[code]
                        ?: throw IllegalArgumentException("ODJAZDY wskazuje nieznany typ dnia $code.")
                    val line = cursor.string("numer_lini").trim()
                    val direction = cursor.stringOrNull("opis_tabl")?.trim()?.takeIf(String::isNotEmpty)
                        ?: "Brak opisu kierunku"
                    val variant = cursor.string("war_trasy").trim()
                    val route = ScheduleTableMapper.routeStopIds(cursor.stringOrNull("trasa"))
                    timetablesByStop[stopId]?.add(
                        TimetableData(
                            line = line,
                            direction = direction,
                        dayType = ScheduleTableMapper.legacyDayType(code),
                        times = ScheduleTableMapper.departureTimes(cursor.string("odjazdy")),
                            serviceDayCode = code,
                            serviceDayLabel = serviceDay.label,
                            variant = variant,
                            directionCode = cursor.stringOrNull("kierunek")?.trim().orEmpty(),
                            routeStopIds = route,
                            stopOrder = cursor.int("lp_przyst"),
                        ),
                    )
                }
            }
            val stops = rawStops.map { stop ->
                StopData(stop.id, stop.name, stop.latitude, stop.longitude, timetablesByStop.getValue(stop.id), stop.publicNumber)
            }
            val ids = stops.mapTo(mutableSetOf()) { it.id }
            ScheduleIntegrityRules.requireKnownRouteStops(
                stops.flatMap { it.timetables }.flatMap { it.routeStopIds }, ids,
            )
            return ScheduleSnapshot(stops, serviceDays, calendar, report.version, lastSuccessfulUpdate)
        }
    }

    fun verify(file: File): ScheduleIntegrityReport =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(::verify)

    private fun verify(database: SQLiteDatabase): ScheduleIntegrityReport {
        require(database.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
            "PRAGMA quick_check nie przeszedł."
        }
        require(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
            "PRAGMA integrity_check nie przeszedł."
        }
        val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0).uppercase()) }
        }
        val required = setOf("WERSJE", "PRZYSTANKI", "KIERUNKI", "ODJAZDY", "DNI", "KALENDARZ")
        require(tables.containsAll(required)) { "W pobranej bazie brakuje wymaganych tabel." }
        val version = database.rawQuery("SELECT id_wersja, wazna_od, generacja FROM WERSJE LIMIT 1", null).use { cursor ->
            require(cursor.moveToFirst()) { "WERSJE jest puste." }
            ScheduleVersion(cursor.getInt(0), cursor.getString(1), cursor.getInt(2))
        }
        val stops = database.count("PRZYSTANKI")
        val variants = database.count("KIERUNKI")
        val timetables = database.count("ODJAZDY")
        val lines = database.rawQuery("SELECT count(DISTINCT trim(numer)) FROM KIERUNKI", null).use { cursor ->
            cursor.moveToFirst(); cursor.getInt(0)
        }
        ScheduleIntegrityRules.requireCompleteCounts(stops, lines, variants, timetables)
        val orphanStopReferences = database.rawQuery(
            "SELECT count(*) FROM ODJAZDY o LEFT JOIN PRZYSTANKI p ON p.id=o.bus_stop_id WHERE p.id IS NULL", null,
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        ScheduleIntegrityRules.requireNoOrphanDepartures(orphanStopReferences)
        return ScheduleIntegrityReport(stops, lines, variants, timetables, version)
    }

    private data class RawStop(
        val id: String,
        val name: String,
        val publicNumber: String?,
        val latitude: Double,
        val longitude: Double,
    )
}

/** Two-version file store: the active pointer changes only after a fully validated download. */
class ScheduleFileStore(
    context: Context,
    private val cityId: Int,
    private val reader: ScheduleDatabaseReader = ScheduleDatabaseReader(),
) : ScheduleCache {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("downloaded_schedule_$cityId", Context.MODE_PRIVATE)

    override fun cachedSnapshot(): ScheduleSnapshot? {
        val name = preferences.getString(ACTIVE_FILE, null) ?: return null
        val file = File(appContext.filesDir, name)
        if (!file.isFile) return null
        return runCatching { reader.read(file, Instant.ofEpochMilli(preferences.getLong(LAST_UPDATED, 0L))) }.getOrNull()
    }

    fun activeIntegrity(): ScheduleIntegrityReport? {
        val name = preferences.getString(ACTIVE_FILE, null) ?: return null
        val file = File(appContext.filesDir, name)
        return file.takeIf(File::isFile)?.let { runCatching { reader.verify(it) }.getOrNull() }
    }

    override fun install(gzipPayload: ByteArray): ScheduleSnapshot {
        val sqlite = ScheduleArchiveDecoder.decodeSqlite(gzipPayload)
        val staging = File(appContext.filesDir, "schedule-$cityId-staging-${UUID.randomUUID()}.db")
        try {
            staging.outputStream().use { it.write(sqlite) }
            val now = Instant.now()
            val snapshot = reader.read(staging, now)
            val finalName = "schedule-$cityId-${snapshot.version.version}-${snapshot.version.generation}-${UUID.randomUUID()}.db"
            val finalFile = File(appContext.filesDir, finalName)
            require(staging.renameTo(finalFile)) { "Nie można zatwierdzić pobranej bazy." }
            // commit is synchronous: until it succeeds, the previous pointer remains active.
            preferences.edit(commit = true) {
                putString(ACTIVE_FILE, finalName)
                putLong(LAST_UPDATED, now.toEpochMilli())
            }
            appContext.filesDir.listFiles()
                ?.filter {
                    it.name.startsWith("schedule-$cityId-") && it.name.endsWith(".db") && it.name != finalName
                }
                ?.forEach(File::delete)
            return snapshot
        } finally {
            if (staging.exists()) staging.delete()
        }
    }

    private companion object {
        const val ACTIVE_FILE = "active_file"
        const val LAST_UPDATED = "last_updated"
    }
}

private fun SQLiteDatabase.count(table: String): Int = rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
    cursor.moveToFirst(); cursor.getInt(0)
}

private fun <T> SQLiteDatabase.queryRows(
    table: String,
    columns: Array<String>,
    selection: String?,
    selectionArgs: Array<String>?,
    groupBy: String?,
    having: String?,
    orderBy: String?,
    map: (Cursor) -> T,
): List<T> = query(table, columns, selection, selectionArgs, groupBy, having, orderBy).use { cursor ->
    buildList { while (cursor.moveToNext()) add(map(cursor)) }
}

private fun <T> SQLiteDatabase.rawRows(sql: String, map: (Cursor) -> T): List<T> = rawQuery(sql, null).use { cursor ->
    buildList { while (cursor.moveToNext()) add(map(cursor)) }
}

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.stringOrNull(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { index ->
    if (isNull(index)) null else getString(index)
}
private fun Cursor.int(name: String): Int = getInt(getColumnIndexOrThrow(name))
private fun Cursor.double(name: String): Double = getDouble(getColumnIndexOrThrow(name))
