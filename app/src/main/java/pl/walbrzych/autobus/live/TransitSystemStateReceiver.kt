package pl.ruby.lubiechowlabs.autobus.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.ruby.lubiechowlabs.autobus.widget.DepartureWidgetProvider
import pl.ruby.lubiechowlabs.autobus.widget.DeparturesWidgetProvider
import pl.ruby.lubiechowlabs.autobus.widget.FavoriteDeparturesWidgetProvider
import pl.ruby.lubiechowlabs.autobus.widget.LineDeparturesWidgetProvider

/** Rebuilds local update schedules after reboot, a clock change or an app update. */
class TransitSystemStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DepartureWidgetProvider.requestRefresh(context)
        DeparturesWidgetProvider.requestRefresh(context)
        LineDeparturesWidgetProvider.requestRefresh(context)
        FavoriteDeparturesWidgetProvider.requestRefresh(context)
        DepartureLiveUpdateManager.restore(context)
    }
}
