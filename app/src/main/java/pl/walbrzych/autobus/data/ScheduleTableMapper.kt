package pl.walbrzych.autobus.data

/** Pure mapping rules for MyBus table values; Android SQLite access remains in ScheduleDatabaseReader. */
object ScheduleTableMapper {
    fun departureTimes(raw: String): List<String> = raw.split(',')
        .chunked(2)
        .mapNotNull { pair -> pair.firstOrNull()?.trim()?.toIntOrNull() }
        .filter { it in 0 until 24 * 60 * 60 }
        .map { seconds -> "%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60) }

    fun routeStopIds(raw: String?): List<String> = raw
        ?.split(',')
        ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .orEmpty()

    fun legacyDayType(code: String): DayType = when (code) {
        "RS" -> DayType.WORKING
        "SO" -> DayType.VACATION
        "NS" -> DayType.SUNDAY_HOLIDAY
        else -> DayType.WORKING
    }
}
