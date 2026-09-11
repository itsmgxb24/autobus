package pl.walbrzych.autobus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import pl.walbrzych.autobus.MainActivity
import pl.walbrzych.autobus.R
import pl.walbrzych.autobus.data.ScheduleFileStore

/**
 * A wide 4×1 widget for a single line: it shows the next three calendar-aware
 * courses instead of repeating the next-departure compact widget.
 */
class LineDeparturesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateInBackground(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, LineDeparturesWidgetProvider::class.java))
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
        refreshScheduler.cancel(context)
    }

    private fun updateInBackground(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        val registeredIds = manager.getAppWidgetIds(ComponentName(context, LineDeparturesWidgetProvider::class.java))
        val ids = if (registeredIds.isNotEmpty()) registeredIds else appWidgetIds
        if (ids.isEmpty()) {
            refreshScheduler.cancel(context)
            return
        }
        val result = goAsync()
        providerScope.launch {
            try {
                val store = DepartureWidgetConfigurationStore(context)
                val refreshAt = ids.map { widgetId ->
                    val content = store.read(widgetId)?.let { resolveContent(context, it) }
                        ?: Content("AutoBUS", "Wybierz przystanek i linię", emptyList(), null)
                    render(context, manager, widgetId, content)
                    content.refreshAt
                }.filterNotNull().minOrNull()
                refreshScheduler.schedule(context, refreshAt)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun resolveContent(context: Context, configuration: DepartureWidgetConfiguration): Content {
        val line = configuration.lines.firstOrNull()
            ?: return Content("AutoBUS", "Wybierz linię w konfiguracji", emptyList(), null)
        val snapshot = ScheduleFileStore(context, configuration.cityId).cachedSnapshot()
            ?: return Content("Linia $line", "Brak zapisanego rozkładu", emptyList(), null)
        val stop = snapshot.stops.firstOrNull { it.id == configuration.stopId }
            ?: return Content("Linia $line", "Przystanek niedostępny", emptyList(), null)
        val now = LocalDateTime.now()
        val departures = selectLineWidgetDepartures(snapshot, stop, line, now)
        val directions = stop.timetables
            .asSequence()
            .filter { it.line == line }
            .map { it.direction }
            .distinct()
            .take(2)
            .joinToString(" · ")
        return Content(
            title = "Linia $line",
            subtitle = listOf(stop.name, directions.takeIf(String::isNotBlank)).joinToString(" · "),
            departures = departures.map { it.scheduledAt.format(TIME_FORMAT) },
            refreshAt = departures.firstOrNull()?.scheduledAt
                ?.atZone(ZoneId.systemDefault())
                ?.toInstant()
                ?.plusSeconds(2)
                ?: Instant.now().plusSeconds(NO_DEPARTURE_RETRY_SECONDS),
        )
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, content: Content) {
        val views = RemoteViews(context.packageName, R.layout.widget_line_departures).apply {
            setTextViewText(R.id.widget_line_series_title, content.title)
            setTextViewText(R.id.widget_line_series_subtitle, content.subtitle)
            TIME_IDS.forEachIndexed { index, viewId ->
                val time = content.departures.getOrNull(index)
                setViewVisibility(viewId, if (time == null) View.GONE else View.VISIBLE)
                if (time != null) setTextViewText(viewId, time)
            }
            setViewVisibility(
                R.id.widget_line_series_empty,
                if (content.departures.isEmpty()) View.VISIBLE else View.GONE,
            )
            setContentDescription(
                R.id.widget_line_series_root,
                listOf(content.title, content.subtitle, content.departures.joinToString(", ")).filter(String::isNotBlank).joinToString(". "),
            )
            setOnClickPendingIntent(
                R.id.widget_line_series_root,
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

    private data class Content(
        val title: String,
        val subtitle: String,
        val departures: List<String>,
        val refreshAt: Instant?,
    )

    companion object {
        private const val ACTION_REFRESH = "pl.walbrzych.autobus.widget.LINE_DEPARTURES_REFRESH"
        private const val NO_DEPARTURE_RETRY_SECONDS = 30 * 60L
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val TIME_IDS = listOf(
            R.id.widget_line_series_time_1,
            R.id.widget_line_series_time_2,
            R.id.widget_line_series_time_3,
        )
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshScheduler = WidgetRefreshScheduler(
            requestCode = 7023,
            receiver = LineDeparturesWidgetProvider::class.java,
            action = ACTION_REFRESH,
        )

        fun requestRefresh(context: Context, appWidgetIds: IntArray? = null) {
            context.sendBroadcast(
                Intent(context, LineDeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH).apply {
                    appWidgetIds?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it) }
                },
            )
        }
    }
}
