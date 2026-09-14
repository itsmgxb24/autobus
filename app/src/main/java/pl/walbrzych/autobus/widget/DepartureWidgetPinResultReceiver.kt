package pl.ruby.lubiechowlabs.autobus.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Finishes a launcher pin request with the stop and optional line filters selected in autoBus. */
class DepartureWidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val cityId = intent.getIntExtra(DepartureWidgetPinning.EXTRA_CITY_ID, -1)
        val stopId = intent.getStringExtra(DepartureWidgetPinning.EXTRA_STOP_ID)
        val lines = intent.getStringArrayExtra(DepartureWidgetPinning.EXTRA_LINES)
            ?.filter(String::isNotBlank)
            ?.toSet()
            ?: intent.getStringExtra(DepartureWidgetPinning.EXTRA_LINE)
                ?.takeIf(String::isNotBlank)
                ?.let(::setOf)
            ?: emptySet()
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || cityId < 0 || stopId.isNullOrBlank()) return

        DepartureWidgetConfigurationStore(context).save(widgetId, DepartureWidgetConfiguration(cityId, stopId, lines))
        DepartureWidgetProvider.requestRefresh(context, intArrayOf(widgetId))
    }
}
