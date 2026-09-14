package pl.walbrzych.autobus.live

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a user-started departure notification current while the tracked course is
 * active. It replaces fragile minute alarms that Android may defer in Doze.
 */
class DepartureLiveUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val update = DepartureLiveUpdateManager.activeUpdate(this) ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(
            DepartureLiveUpdateManager.NOTIFICATION_ID,
            DepartureLiveUpdateManager.connectingNotification(this, update),
        )
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                if (!DepartureLiveUpdateManager.refreshForForegroundService(applicationContext)) {
                    stopSelf(startId)
                    return@launch
                }
                delay(60_000)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DepartureLiveUpdateService::class.java),
            )
        }
    }
}
