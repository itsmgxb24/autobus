package pl.walbrzych.autobus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

data class DepartureWidgetConfiguration(
    val cityId: Int,
    val stopId: String,
    /** Empty means that the widget follows every line serving this stop. */
    val lines: Set<String> = emptySet(),
) {
    val followsAllLines: Boolean get() = lines.isEmpty()
}

/** Per-widget settings: widgets keep their original city after the app changes city. */
class DepartureWidgetConfigurationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(appWidgetId: Int): DepartureWidgetConfiguration? {
        val prefix = "$appWidgetId."
        val cityId = preferences.getInt(prefix + CITY_ID, NO_CITY)
        val stopId = preferences.getString(prefix + STOP_ID, null)
        val lines = preferences.getStringSet(prefix + LINES, null)
            ?.filter(String::isNotBlank)
            ?.toSet()
            // Widgets added before multi-line tracking used this key. Retaining it
            // makes the upgrade non-destructive for people who already pinned one.
            ?: preferences.getString(prefix + LEGACY_LINE, null)
                ?.takeIf(String::isNotBlank)
                ?.let(::setOf)
            ?: emptySet()
        return if (cityId == NO_CITY || stopId.isNullOrBlank()) null
        else DepartureWidgetConfiguration(cityId, stopId, lines)
    }

    fun save(appWidgetId: Int, configuration: DepartureWidgetConfiguration) {
        val prefix = "$appWidgetId."
        preferences.edit(commit = true) {
            putInt(prefix + CITY_ID, configuration.cityId)
            putString(prefix + STOP_ID, configuration.stopId)
            putStringSet(prefix + LINES, configuration.lines)
            remove(prefix + LEGACY_LINE)
        }
    }

    fun remove(appWidgetId: Int) {
        val prefix = "$appWidgetId."
        preferences.edit(commit = true) {
            remove(prefix + CITY_ID)
            remove(prefix + STOP_ID)
            remove(prefix + LINES)
            remove(prefix + LEGACY_LINE)
        }
    }

    private companion object {
        const val PREFERENCES = "departure_widget_configuration"
        const val CITY_ID = "city_id"
        const val STOP_ID = "stop_id"
        const val LINES = "lines"
        const val LEGACY_LINE = "line"
        const val NO_CITY = -1
    }
}

/** Requests the original compact widget after choosing one stop and one line. */
object DepartureWidgetPinning {
    const val EXTRA_CITY_ID = "pl.walbrzych.autobus.widget.CITY_ID"
    const val EXTRA_STOP_ID = "pl.walbrzych.autobus.widget.STOP_ID"
    const val EXTRA_LINES = "pl.walbrzych.autobus.widget.LINES"
    /** Kept only to read pin callbacks from an APK version installed before the upgrade. */
    const val EXTRA_LINE = "pl.walbrzych.autobus.widget.LINE"

    fun request(context: Context, configuration: DepartureWidgetConfiguration): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            configuration.hashCode(),
            Intent(context, DepartureWidgetPinResultReceiver::class.java)
                .putExtra(EXTRA_CITY_ID, configuration.cityId)
                .putExtra(EXTRA_STOP_ID, configuration.stopId)
                .putExtra(EXTRA_LINES, configuration.lines.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return manager.requestPinAppWidget(
            ComponentName(context, DepartureWidgetProvider::class.java),
            null,
            callback,
        )
    }
}

/** Requests the additional 4×2 widget that can follow several lines or all of them. */
object DeparturesWidgetPinning {
    fun request(context: Context, configuration: DepartureWidgetConfiguration): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            31 * configuration.hashCode() + 1,
            Intent(context, DeparturesWidgetPinResultReceiver::class.java)
                .putExtra(DepartureWidgetPinning.EXTRA_CITY_ID, configuration.cityId)
                .putExtra(DepartureWidgetPinning.EXTRA_STOP_ID, configuration.stopId)
                .putExtra(DepartureWidgetPinning.EXTRA_LINES, configuration.lines.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return manager.requestPinAppWidget(
            ComponentName(context, DeparturesWidgetProvider::class.java),
            null,
            callback,
        )
    }
}

/** Requests the 4×1 widget that keeps the next three courses of one line in view. */
object LineDeparturesWidgetPinning {
    fun request(context: Context, configuration: DepartureWidgetConfiguration): Boolean {
        val line = configuration.lines.firstOrNull() ?: return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            37 * configuration.hashCode() + 2,
            Intent(context, LineDeparturesWidgetPinResultReceiver::class.java)
                .putExtra(DepartureWidgetPinning.EXTRA_CITY_ID, configuration.cityId)
                .putExtra(DepartureWidgetPinning.EXTRA_STOP_ID, configuration.stopId)
                .putExtra(DepartureWidgetPinning.EXTRA_LINES, arrayOf(line)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return manager.requestPinAppWidget(
            ComponentName(context, LineDeparturesWidgetProvider::class.java),
            null,
            callback,
        )
    }
}
