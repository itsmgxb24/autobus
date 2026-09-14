package pl.ruby.lubiechowlabs.autobus.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stores an in-app pin request for the wide, single-line course widget. */
class LineDeparturesWidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val cityId = intent.getIntExtra(DepartureWidgetPinning.EXTRA_CITY_ID, -1)
        val stopId = intent.getStringExtra(DepartureWidgetPinning.EXTRA_STOP_ID)
        val line = intent.getStringArrayExtra(DepartureWidgetPinning.EXTRA_LINES)
            ?.firstOrNull(String::isNotBlank)
            ?: intent.getStringExtra(DepartureWidgetPinning.EXTRA_LINE)?.takeIf(String::isNotBlank)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || cityId < 0 || stopId.isNullOrBlank() || line == null) return

        DepartureWidgetConfigurationStore(context).save(widgetId, DepartureWidgetConfiguration(cityId, stopId, setOf(line)))
        LineDeparturesWidgetProvider.requestRefresh(context, intArrayOf(widgetId))
    }
}
