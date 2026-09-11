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
import pl.walbrzych.autobus.data.CitySelectionStore
import pl.walbrzych.autobus.data.ScheduleFileStore
import pl.walbrzych.autobus.data.UserInterfacePreferences

/**
 * A 4×3 dashboard that combines the closest courses from the user's favourite
 * stops. It intentionally uses the local schedule only: one widget refresh never
 * becomes a network request per favourite stop.
 */
class FavoriteDeparturesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateInBackground(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, FavoriteDeparturesWidgetProvider::class.java))
            updateInBackground(context, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        refreshScheduler.cancel(context)
    }

    private fun updateInBackground(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        val registeredIds = manager.getAppWidgetIds(ComponentName(context, FavoriteDeparturesWidgetProvider::class.java))
        val ids = if (registeredIds.isNotEmpty()) registeredIds else appWidgetIds
        if (ids.isEmpty()) {
            refreshScheduler.cancel(context)
            return
        }
        val result = goAsync()
        providerScope.launch {
            try {
                val content = resolveContent(context)
                ids.forEach { widgetId -> render(context, manager, widgetId, content) }
                refreshScheduler.schedule(context, content.refreshAt)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun resolveContent(context: Context): Content {
        val city = CitySelectionStore(context).selectedCity()
            ?: return Content("Ulubione odjazdy", "Najpierw wybierz miasto", emptyList(), null)
        val favorites = UserInterfacePreferences(context).favoriteStopIds(city.id)
        if (favorites.isEmpty()) {
            return Content("Ulubione odjazdy", "Dodaj przystanki do ulubionych w AutoBUS", emptyList(), null)
        }
        val snapshot = ScheduleFileStore(context, city.id).cachedSnapshot()
            ?: return Content("Ulubione odjazdy", "Brak zapisanego rozkładu", emptyList(), null)
        val now = LocalDateTime.now()
        val rows = selectFavoriteWidgetDepartures(snapshot, favorites, now)
        return Content(
            title = "Ulubione odjazdy",
            subtitle = "${favorites.size} ${favoriteStopLabel(favorites.size)}",
            rows = rows.map { favorite ->
                Row(
                    line = favorite.departure.timetable.line,
                    stop = favorite.stop.name,
                    direction = favorite.departure.timetable.direction,
                    time = "Przyjazd: ${favorite.departure.scheduledAt.format(TIME_FORMAT)}",
                )
            },
            refreshAt = rows.firstOrNull()?.departure?.scheduledAt
                ?.atZone(ZoneId.systemDefault())
                ?.toInstant()
                ?.plusSeconds(2)
                ?: Instant.now().plusSeconds(NO_DEPARTURE_RETRY_SECONDS),
        )
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, content: Content) {
        val views = RemoteViews(context.packageName, R.layout.widget_favorite_departures).apply {
            setTextViewText(R.id.widget_favorite_title, content.title)
            setTextViewText(R.id.widget_favorite_subtitle, content.subtitle)
            ROW_IDS.forEachIndexed { index, rowIds ->
                val row = content.rows.getOrNull(index)
                setViewVisibility(rowIds.root, if (row == null) View.GONE else View.VISIBLE)
                if (row != null) {
                    setTextViewText(rowIds.line, row.line)
                    setTextViewText(rowIds.stop, row.stop)
                    setTextViewText(rowIds.direction, row.direction)
                    setTextViewText(rowIds.time, row.time)
                }
            }
            setViewVisibility(R.id.widget_favorite_empty, if (content.rows.isEmpty()) View.VISIBLE else View.GONE)
            setContentDescription(
                R.id.widget_favorite_root,
                (listOf(content.title, content.subtitle) + content.rows.map { "${it.line}: ${it.stop}, ${it.time}" })
                    .filter(String::isNotBlank)
                    .joinToString(". "),
            )
            setOnClickPendingIntent(
                R.id.widget_favorite_root,
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
        val rows: List<Row>,
        val refreshAt: Instant?,
    )

    private data class Row(
        val line: String,
        val stop: String,
        val direction: String,
        val time: String,
    )

    private data class RowIds(val root: Int, val line: Int, val stop: Int, val direction: Int, val time: Int)

    companion object {
        private const val ACTION_REFRESH = "pl.walbrzych.autobus.widget.FAVORITE_DEPARTURES_REFRESH"
        private const val NO_DEPARTURE_RETRY_SECONDS = 30 * 60L
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshScheduler = WidgetRefreshScheduler(
            requestCode = 7024,
            receiver = FavoriteDeparturesWidgetProvider::class.java,
            action = ACTION_REFRESH,
        )
        private val ROW_IDS = listOf(
            RowIds(R.id.widget_favorite_row_1, R.id.widget_favorite_line_1, R.id.widget_favorite_stop_1, R.id.widget_favorite_direction_1, R.id.widget_favorite_time_1),
            RowIds(R.id.widget_favorite_row_2, R.id.widget_favorite_line_2, R.id.widget_favorite_stop_2, R.id.widget_favorite_direction_2, R.id.widget_favorite_time_2),
            RowIds(R.id.widget_favorite_row_3, R.id.widget_favorite_line_3, R.id.widget_favorite_stop_3, R.id.widget_favorite_direction_3, R.id.widget_favorite_time_3),
        )

        fun requestRefresh(context: Context, appWidgetIds: IntArray? = null) {
            context.sendBroadcast(
                Intent(context, FavoriteDeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH).apply {
                    appWidgetIds?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it) }
                },
            )
        }

        private fun favoriteStopLabel(count: Int): String = when {
            count == 1 -> "ulubiony przystanek"
            count in 2..4 -> "ulubione przystanki"
            else -> "ulubionych przystanków"
        }
    }
}
