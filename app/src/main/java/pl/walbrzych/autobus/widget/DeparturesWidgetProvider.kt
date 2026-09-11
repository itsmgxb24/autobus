package pl.walbrzych.autobus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.walbrzych.autobus.MainActivity
import pl.walbrzych.autobus.R
import pl.walbrzych.autobus.data.CityCatalog
import pl.walbrzych.autobus.data.RealTimeDeparture
import pl.walbrzych.autobus.data.ScheduleFileStore
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.ScheduledDeparture
import pl.walbrzych.autobus.data.TransitRepository
import pl.walbrzych.autobus.data.nextScheduledDepartures

/** Material-styled 4×2 home-screen widget for four nearest departures at one stop. */
class DeparturesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateInBackground(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DeparturesWidgetProvider::class.java))
            updateInBackground(context, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = DepartureWidgetConfigurationStore(context)
        appWidgetIds.forEach(store::remove)
        requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        DeparturesWidgetRefreshScheduler.cancel(context)
    }

    private fun updateInBackground(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        val registeredWidgetIds = manager.getAppWidgetIds(ComponentName(context, DeparturesWidgetProvider::class.java))
        val allWidgetIds = if (registeredWidgetIds.isNotEmpty()) registeredWidgetIds else appWidgetIds
        if (allWidgetIds.isEmpty()) {
            DeparturesWidgetRefreshScheduler.cancel(context)
            return
        }
        val pendingResult = goAsync()
        providerScope.launch {
            try {
                val store = DepartureWidgetConfigurationStore(context)
                // A launcher can host several widgets for the same stop. Share both
                // the local database read and the optional real-time request between
                // them instead of multiplying network traffic.
                val snapshots = mutableMapOf<Int, ScheduleSnapshot?>()
                val realtimeDepartures = mutableMapOf<WidgetStopKey, List<RealTimeDeparture>?>()
                val refreshTimes = allWidgetIds.map { widgetId ->
                    val content = store.read(widgetId)?.let { configuration ->
                        resolveContent(context, configuration, snapshots, realtimeDepartures)
                    } ?: WidgetContent(
                        title = "AutoBUS",
                        subtitle = "Wybierz przystanek w konfiguracji",
                        updatedAt = "",
                        departures = listOf(WidgetDeparture("", "Otwórz konfigurację widgetu", "")),
                        refreshAt = null,
                    )
                    render(context, manager, widgetId, content)
                    content.refreshAt
                }
                DeparturesWidgetRefreshScheduler.schedule(context, refreshTimes.filterNotNull().minOrNull())
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun resolveContent(
        context: Context,
        configuration: DepartureWidgetConfiguration,
        snapshots: MutableMap<Int, ScheduleSnapshot?>,
        realtimeCache: MutableMap<WidgetStopKey, List<RealTimeDeparture>?>,
    ): WidgetContent {
        val snapshot = if (snapshots.containsKey(configuration.cityId)) snapshots[configuration.cityId] else {
            ScheduleFileStore(context, configuration.cityId).cachedSnapshot().also {
                snapshots[configuration.cityId] = it
            }
        } ?: return WidgetContent(
            title = "AutoBUS",
            subtitle = "Brak zapisanego rozkładu",
            updatedAt = "",
            departures = listOf(WidgetDeparture("", "Otwórz AutoBUS i pobierz rozkład", "")),
            refreshAt = null,
        )
        val stop = snapshot.stops.firstOrNull { it.id == configuration.stopId }
            ?: return WidgetContent(
                title = "AutoBUS",
                subtitle = "Przystanek niedostępny",
                updatedAt = "",
                departures = listOf(WidgetDeparture("", "Otwórz AutoBUS i wybierz przystanek", "")),
                refreshAt = null,
            )
        val now = LocalDateTime.now()
        val scheduled = selectWidgetScheduledDepartures(snapshot, stop, configuration.lines, now)
        val stopKey = WidgetStopKey(configuration.cityId, stop.id)
        val realtime = if (realtimeCache.containsKey(stopKey)) realtimeCache[stopKey] else {
            CityCatalog.byId(configuration.cityId)?.let { city ->
            withTimeoutOrNull(8_000) {
                TransitRepository(context, city).realTimeDepartures(stop.id).getOrNull()
                    ?.departures
            }
            }.also { realtimeCache[stopKey] = it }
        }
        val liveCandidates = realtime
            ?.asSequence()
            ?.filter { configuration.followsAllLines || it.line in configuration.lines }
            ?.toList()
            .orEmpty()
        // A vehicle without a server connection is absent from GetTimeTableReal.
        // Start from the complete static timetable and overlay only matching live
        // rows, so "Pomiń" still means four departures rather than fewer rows.
        val rows = if (scheduled.isNotEmpty()) {
            scheduled.map { scheduledDeparture ->
                val scheduledSeconds = scheduledDeparture.scheduledAt.toLocalTime().toSecondOfDay()
                val liveDeparture = liveCandidates.firstOrNull {
                    it.line == scheduledDeparture.timetable.line &&
                        it.scheduledSeconds == scheduledSeconds &&
                        it.direction == scheduledDeparture.timetable.direction
                } ?: liveCandidates.firstOrNull {
                    it.line == scheduledDeparture.timetable.line && it.scheduledSeconds == scheduledSeconds
                }
                WidgetDeparture(
                    line = scheduledDeparture.timetable.line,
                    direction = scheduledDeparture.timetable.direction,
                    // Retain the wording used by the stop screen: an ETA is shown
                    // only when the server supplied one, otherwise it is HH:mm.
                    time = liveDeparture?.arrivalLabel
                        ?: "Przyjazd: ${scheduledDeparture.scheduledAt.format(TIME_FORMAT)}",
                )
            }
        } else {
            liveCandidates.take(MAX_DEPARTURES).map { departure ->
                WidgetDeparture(
                    line = departure.line,
                    direction = departure.direction,
                    time = departure.arrivalLabel,
                )
            }
        }
        val refreshAt = when {
            liveCandidates.any { it.etaMinutes != null } -> Instant.now().plusSeconds(60)
            scheduled.isNotEmpty() -> scheduled.first().scheduledAt.atZone(ZoneId.systemDefault()).toInstant().plusSeconds(2)
            else -> Instant.now().plusSeconds(NO_DEPARTURE_RETRY_SECONDS)
        }
        return WidgetContent(
            title = stop.name,
            subtitle = buildString {
                stop.publicNumber?.let { append("nr $it · ") }
                append(
                    if (configuration.followsAllLines) "Kierunek: wszystkie"
                    else "Linie: ${configuration.lines.sorted().joinToString(" · ")}",
                )
            },
            updatedAt = "Akt. ${now.format(UPDATE_TIME_FORMAT)}",
            departures = rows.ifEmpty { listOf(WidgetDeparture("", "Brak kolejnych kursów", "")) },
            refreshAt = refreshAt,
        )
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, content: WidgetContent) {
        val views = RemoteViews(context.packageName, R.layout.widget_departure).apply {
            setTextViewText(R.id.widget_stop_name, content.title)
            setTextViewText(R.id.widget_stop_summary, content.subtitle)
            setTextViewText(R.id.widget_updated_at, content.updatedAt)
            WIDGET_ROW_IDS.forEachIndexed { index, rowIds ->
                val departure = content.departures.getOrNull(index)
                setViewVisibility(rowIds.root, if (departure == null) View.GONE else View.VISIBLE)
                if (departure != null) {
                    setViewVisibility(rowIds.line, if (departure.line.isBlank()) View.GONE else View.VISIBLE)
                    setTextViewText(rowIds.line, departure.line)
                    setTextViewText(rowIds.direction, departure.direction)
                    setTextViewText(rowIds.time, departure.time)
                }
            }
            setContentDescription(
                R.id.widget_root,
                (listOf(content.title, content.subtitle) + content.departures.map { "${it.line} ${it.direction} ${it.time}" })
                    .joinToString(". "),
            )
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        manager.updateAppWidget(widgetId, views)
    }

    private data class WidgetContent(
        val title: String,
        val subtitle: String,
        val updatedAt: String,
        val departures: List<WidgetDeparture>,
        val refreshAt: Instant?,
    )

    private data class WidgetDeparture(
        val line: String,
        val direction: String,
        val time: String,
    )

    private data class WidgetStopKey(val cityId: Int, val stopId: String)

    private data class WidgetRowIds(val root: Int, val line: Int, val direction: Int, val time: Int)

    companion object {
        private const val ACTION_REFRESH = "pl.walbrzych.autobus.widget.DEPARTURES_REFRESH"
        private const val NO_DEPARTURE_RETRY_SECONDS = 30 * 60L
        private const val MAX_DEPARTURES = 4
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val UPDATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val WIDGET_ROW_IDS = listOf(
            WidgetRowIds(R.id.widget_departure_row_1, R.id.widget_line_1, R.id.widget_direction_1, R.id.widget_time_1),
            WidgetRowIds(R.id.widget_departure_row_2, R.id.widget_line_2, R.id.widget_direction_2, R.id.widget_time_2),
            WidgetRowIds(R.id.widget_departure_row_3, R.id.widget_line_3, R.id.widget_direction_3, R.id.widget_time_3),
            WidgetRowIds(R.id.widget_departure_row_4, R.id.widget_line_4, R.id.widget_direction_4, R.id.widget_time_4),
        )

        fun requestRefresh(context: Context, appWidgetIds: IntArray? = null) {
            context.sendBroadcast(
                Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH).apply {
                    appWidgetIds?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it) }
                },
            )
        }
    }
}

/** Chooses the same four chronologically nearest static departures as the local timetable. */
internal fun selectWidgetScheduledDepartures(
    snapshot: ScheduleSnapshot,
    stop: pl.walbrzych.autobus.data.StopData,
    lines: Set<String>,
    now: LocalDateTime,
): List<ScheduledDeparture> =
    nextScheduledDepartures(snapshot, stop, from = now, limit = 64)
        .asSequence()
        .filter { lines.isEmpty() || it.timetable.line in lines }
        .take(4)
        .toList()

private object DeparturesWidgetRefreshScheduler {
    private const val REQUEST_CODE = 7022

    fun schedule(context: Context, refreshAt: Instant?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        refreshAt ?: return
        val triggerAt = refreshAt.toEpochMilli().coerceAtLeast(System.currentTimeMillis() + 5_000)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            // A launcher still refreshes the widget periodically; do not request an exact-alarm permission.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, DeparturesWidgetProvider::class.java).setAction("pl.walbrzych.autobus.widget.DEPARTURES_REFRESH"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
