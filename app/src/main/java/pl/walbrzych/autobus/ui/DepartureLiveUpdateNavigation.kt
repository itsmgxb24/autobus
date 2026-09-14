package pl.ruby.lubiechowlabs.autobus.ui

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.ruby.lubiechowlabs.autobus.live.DepartureLiveUpdateManager

data class DepartureOpenTarget(
    val cityId: Int,
    val stopId: String,
    val line: String,
    val scheduledAtMillis: Long,
)

/** Retains the notification target until the Compose navigation graph consumes it. */
object DepartureLiveUpdateNavigation {
    private val mutableTarget = MutableStateFlow<DepartureOpenTarget?>(null)
    val target = mutableTarget.asStateFlow()

    fun accept(intent: Intent) {
        if (intent.action != DepartureLiveUpdateManager.ACTION_OPEN) return
        val cityId = intent.getIntExtra(DepartureLiveUpdateManager.EXTRA_CITY_ID, -1)
        val stopId = intent.getStringExtra(DepartureLiveUpdateManager.EXTRA_STOP_ID)
        val line = intent.getStringExtra(DepartureLiveUpdateManager.EXTRA_LINE)
        val scheduledAt = intent.getLongExtra(DepartureLiveUpdateManager.EXTRA_SCHEDULED_AT, 0)
        if (cityId >= 0 && !stopId.isNullOrBlank() && !line.isNullOrBlank() && scheduledAt > 0) {
            mutableTarget.value = DepartureOpenTarget(cityId, stopId, line, scheduledAt)
        }
    }

    fun clear() {
        mutableTarget.value = null
    }
}
