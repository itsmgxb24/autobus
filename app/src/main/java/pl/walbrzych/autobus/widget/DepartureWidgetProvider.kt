package pl.walbrzych.autobus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.walbrzych.autobus.MainActivity
import pl.walbrzych.autobus.R
import pl.walbrzych.autobus.data.CityCatalog
import pl.walbrzych.autobus.data.cachedScheduleForCity
import pl.walbrzych.autobus.data.TransitRepository
import pl.walbrzych.autobus.data.TransitTime
import pl.walbrzych.autobus.data.nextScheduledDepartures

/** Original compact 2×2 widget: one chosen line and its next departure. */
class DepartureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateInBackground(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DepartureWidgetProvider::class.java))
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
        CompactWidgetRefreshScheduler.cancel(context)
    }

    private fun updateInBackground(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        val registeredIds = manager.getAppWidgetIds(ComponentName(context, DepartureWidgetProvider::class.java))
        val ids = if (registeredIds.isNotEmpty()) registeredIds else appWidgetIds
        if (ids.isEmpty()) {
            CompactWidgetRefreshScheduler.cancel(context)
            return
        }
        val result = goAsync()
        scope.launch {
            try {
                val store = DepartureWidgetConfigurationStore(context)
                val refreshAt = ids.map { widgetId ->
                    val content = store.read(widgetId)?.let { resolveContent(context, it) }
                        ?: CompactContent("autoBus", "Wybierz odjazd", "Otwórz konfigurację widgetu", null)
                    render(context, manager, widgetId, content)
                    content.refreshAt
                }.filterNotNull().minOrNull()
                CompactWidgetRefreshScheduler.schedule(context, refreshAt)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun resolveContent(context: Context, configuration: DepartureWidgetConfiguration): CompactContent {
        val line = configuration.lines.firstOrNull()
            ?: return CompactContent("autoBus", "Wybierz linię", "Otwórz konfigurację widgetu", null)
        val city = CityCatalog.byId(configuration.cityId)
            ?: return CompactContent(line, "Miasto niedostępne", "z: otwórz autoBus", null)
        val snapshot = cachedScheduleForCity(context, city)
            ?: return CompactContent(line, "Brak zapisanego rozkładu", "z: otwórz autoBus", null)
        val stop = snapshot.stops.firstOrNull { it.id == configuration.stopId }
            ?: return CompactContent(line, "Przystanek niedostępny", "z: otwórz autoBus", null)
        val now = TransitTime.now()
        val next = nextScheduledDepartures(snapshot, stop, line, now, limit = 1).firstOrNull()
        val liveDeparture = CityCatalog.byId(configuration.cityId)?.let { city ->
            withTimeoutOrNull(8_000) {
                TransitRepository(context, city).realTimeDepartures(stop.id).getOrNull()
                    ?.departures
                    ?.asSequence()
                    ?.firstOrNull { departure ->
                        next != null && departure.line == line &&
                            departure.scheduledSeconds == next.scheduledAt.toLocalTime().toSecondOfDay() &&
                            (departure.direction == next.timetable.direction || departure.direction.isBlank())
                    }
            }
        }
        val refreshAt = when {
            liveDeparture?.etaMinutes != null -> Instant.now().plusSeconds(60)
            next != null -> next.scheduledAt.atZone(TransitTime.zone).toInstant().plusSeconds(2)
            else -> Instant.now().plusSeconds(NO_DEPARTURE_RETRY_SECONDS)
        }
        return CompactContent(
            line = line,
            departure = liveDeparture?.let { widgetDepartureLabel(it, next?.scheduledAt, now) }
                ?: next?.let { widgetScheduledDepartureLabel(it.scheduledAt, now) }
                ?: "Brak kolejnych kursów",
            stop = "z: ${stop.name}",
            refreshAt = refreshAt,
        )
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, content: CompactContent) {
        val views = RemoteViews(context.packageName, R.layout.widget_departure_compact).apply {
            setTextViewText(R.id.widget_compact_line, content.line)
            setTextViewText(R.id.widget_compact_departure, content.departure)
            setTextViewText(R.id.widget_compact_stop, content.stop)
            setContentDescription(R.id.widget_compact_root, "${content.line}. ${content.departure}. ${content.stop}")
            setOnClickPendingIntent(
                R.id.widget_compact_root,
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

    private data class CompactContent(
        val line: String,
        val departure: String,
        val stop: String,
        val refreshAt: Instant?,
    )

    companion object {
        private const val ACTION_REFRESH = "pl.walbrzych.autobus.widget.COMPACT_REFRESH"
        private const val NO_DEPARTURE_RETRY_SECONDS = 30 * 60L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun requestRefresh(context: Context, appWidgetIds: IntArray? = null) {
            context.sendBroadcast(
                Intent(context, DepartureWidgetProvider::class.java).setAction(ACTION_REFRESH).apply {
                    appWidgetIds?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it) }
                },
            )
        }
    }
}

private object CompactWidgetRefreshScheduler {
    private const val REQUEST_CODE = 7021

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
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, DepartureWidgetProvider::class.java).setAction("pl.walbrzych.autobus.widget.COMPACT_REFRESH"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
