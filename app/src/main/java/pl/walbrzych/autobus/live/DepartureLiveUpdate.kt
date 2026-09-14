package pl.walbrzych.autobus.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.Instant
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
import pl.walbrzych.autobus.data.TransitRepository
import pl.walbrzych.autobus.data.TransitTime

/** Persistent identity of the exact scheduled course being tracked by the user. */
data class DepartureLiveUpdate(
    val cityId: Int,
    val stopId: String,
    val stopName: String,
    val line: String,
    val direction: String,
    val scheduledAtMillis: Long,
    val scheduledSeconds: Int,
    val tripId: Int? = null,
)

/**
 * User-initiated Android 16 Live Update for one imminent bus course. Before API 36
 * the identical notification remains a normal, ongoing notification. No custom
 * RemoteViews are used, preserving promoted-notification eligibility.
 */
object DepartureLiveUpdateManager {
    const val ACTION_REFRESH = "pl.walbrzych.autobus.live.REFRESH"
    const val ACTION_CANCEL = "pl.walbrzych.autobus.live.CANCEL"
    const val ACTION_OPEN = "pl.walbrzych.autobus.live.OPEN"
    const val EXTRA_CITY_ID = "pl.walbrzych.autobus.live.CITY_ID"
    const val EXTRA_STOP_ID = "pl.walbrzych.autobus.live.STOP_ID"
    const val EXTRA_LINE = "pl.walbrzych.autobus.live.LINE"
    const val EXTRA_SCHEDULED_AT = "pl.walbrzych.autobus.live.SCHEDULED_AT"

    private const val CHANNEL_ID = "tracked_departure_live_update"
    internal const val NOTIFICATION_ID = 4016
    private const val ALARM_REQUEST_CODE = 4017
    private const val NO_LIVE_DATA_GRACE_MINUTES = 1L
    private const val REFRESH_SECONDS = 60L
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, update: DepartureLiveUpdate) {
        LiveUpdateStore(context).save(update)
        try {
            DepartureLiveUpdateService.start(context.applicationContext)
        } catch (_: SecurityException) {
            // Keep a best-effort alarm refresh if the OS denies foreground work.
            refresh(context)
        } catch (_: IllegalStateException) {
            refresh(context)
        }
    }

    fun refresh(context: Context) {
        providerScope.launch {
            refreshNow(context.applicationContext)
        }
    }

    internal fun refreshFromReceiver(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult) {
        providerScope.launch {
            try {
                refreshNow(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    fun cancel(context: Context) {
        LiveUpdateStore(context).clear()
        scheduler.cancel(context)
        context.stopService(Intent(context, DepartureLiveUpdateService::class.java))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Called after system events where a foreground service cannot safely be started. */
    fun restore(context: Context) {
        if (activeUpdate(context) != null) refresh(context)
    }

    internal fun activeUpdate(context: Context): DepartureLiveUpdate? = LiveUpdateStore(context).read()

    internal fun connectingNotification(context: Context, update: DepartureLiveUpdate): android.app.Notification {
        createChannel(context)
        return buildNotification(
            context,
            update,
            LiveUpdatePresentation("${update.line} • śledzenie", "Pobieranie bieżących danych"),
        )
    }

    internal suspend fun refreshForForegroundService(context: Context): Boolean =
        refreshNow(context, scheduleFallback = false)

    @SuppressLint("MissingPermission") // Guarded by canPostNotifications immediately below.
    private suspend fun refreshNow(context: Context, scheduleFallback: Boolean = true): Boolean {
        val update = LiveUpdateStore(context).read() ?: run {
            cancel(context)
            return false
        }
        if (!canPostNotifications(context)) {
            cancel(context)
            return false
        }
        val now = Instant.now()
        val scheduledAt = Instant.ofEpochMilli(update.scheduledAtMillis)
        val realtime = CityCatalog.byId(update.cityId)?.let { city ->
            withTimeoutOrNull(8_000) {
                TransitRepository(context, city).realTimeDepartures(update.stopId).getOrNull()?.departures
            }
        }
        val matchingDeparture = realtime?.firstOrNull { candidate -> candidate.matches(update) }
        if (shouldEndTracking(now, scheduledAt, serverResponded = realtime != null, realtime = matchingDeparture)) {
            cancel(context)
            return false
        }
        createChannel(context)
        val presentation = liveUpdatePresentation(update, scheduledAt, matchingDeparture)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context, update, presentation))
        if (scheduleFallback) scheduler.schedule(context, now.plusSeconds(REFRESH_SECONDS))
        return true
    }

    internal fun buildNotification(
        context: Context,
        update: DepartureLiveUpdate,
        presentation: LiveUpdatePresentation,
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CITY_ID, update.cityId)
                .putExtra(EXTRA_STOP_ID, update.stopId)
                .putExtra(EXTRA_LINE, update.line)
                .putExtra(EXTRA_SCHEDULED_AT, update.scheduledAtMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1,
            Intent(context, DepartureLiveUpdateReceiver::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bus)
            .setContentTitle(presentation.statusChip)
            .setContentText("${update.direction} · ${update.stopName}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${presentation.detail}\n${update.direction}\nPrzystanek: ${update.stopName}",
                ),
            )
            .setShortCriticalText(presentation.statusChip)
            .setRequestPromotedOngoing(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_notification_bus, "Zakończ śledzenie", cancelIntent)
            .build()
    }

    internal fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Śledzone odjazdy",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Bieżące, śledzone odjazdy autobusów"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private val scheduler = object {
        fun schedule(context: Context, refreshAt: Instant) {
            val manager = context.getSystemService(AlarmManager::class.java)
            val pendingIntent = pendingIntent(context)
            manager.cancel(pendingIntent)
            val atMillis = refreshAt.toEpochMilli().coerceAtLeast(System.currentTimeMillis() + 5_000)
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            } catch (_: SecurityException) {
                manager.set(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            }
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, DepartureLiveUpdateReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Receives minute refreshes and the explicit notification action to stop tracking. */
class DepartureLiveUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DepartureLiveUpdateManager.ACTION_CANCEL) {
            DepartureLiveUpdateManager.cancel(context)
        } else if (intent.action == DepartureLiveUpdateManager.ACTION_REFRESH) {
            DepartureLiveUpdateManager.refreshFromReceiver(context, goAsync())
        }
    }
}

internal data class LiveUpdatePresentation(val statusChip: String, val detail: String)

internal fun liveUpdatePresentation(
    update: DepartureLiveUpdate,
    scheduledAt: Instant,
    realtime: RealTimeDeparture?,
): LiveUpdatePresentation {
    val time = scheduledAt.atZone(TransitTime.zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    val eta = realtime?.etaMinutes
    return when {
        eta != null && eta > 0 -> LiveUpdatePresentation(
            statusChip = "${update.line} • $eta min",
            detail = "Przyjazd za $eta min",
        )
        eta == 0 -> LiveUpdatePresentation(
            statusChip = "${update.line} • teraz",
            detail = "Przyjazd teraz",
        )
        else -> LiveUpdatePresentation(
            statusChip = "${update.line} • $time",
            detail = "Planowy przyjazd: $time",
        )
    }
}

internal fun shouldEndTracking(
    now: Instant,
    scheduledAt: Instant,
    serverResponded: Boolean,
    realtime: RealTimeDeparture?,
): Boolean {
    // Keep the user-selected notification through a network outage. A course can
    // be delayed much longer than ten minutes, so end only after the server
    // actually responds without an ETA for that course.
    if (!serverResponded) return false
    return now.isAfter(scheduledAt.plusSeconds(60)) && (realtime == null || realtime.etaMinutes == null || realtime.etaMinutes <= 0)
}

private fun RealTimeDeparture.matches(update: DepartureLiveUpdate): Boolean =
    (update.tripId != null && tripId == update.tripId) ||
        (line == update.line && scheduledSeconds == update.scheduledSeconds &&
            (update.direction.isBlank() || direction == update.direction))

private class LiveUpdateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("departure_live_update", Context.MODE_PRIVATE)

    fun save(update: DepartureLiveUpdate) {
        preferences.edit(commit = true) {
            putInt("city", update.cityId)
            putString("stop", update.stopId)
            putString("stop_name", update.stopName)
            putString("line", update.line)
            putString("direction", update.direction)
            putLong("scheduled_at", update.scheduledAtMillis)
            putInt("scheduled_seconds", update.scheduledSeconds)
            update.tripId?.let { putInt("trip_id", it) } ?: remove("trip_id")
        }
    }

    fun read(): DepartureLiveUpdate? {
        val cityId = preferences.getInt("city", -1)
        val stopId = preferences.getString("stop", null)
        val stopName = preferences.getString("stop_name", null)
        val line = preferences.getString("line", null)
        val direction = preferences.getString("direction", null)
        val scheduledAt = preferences.getLong("scheduled_at", 0)
        if (cityId < 0 || stopId.isNullOrBlank() || stopName.isNullOrBlank() || line.isNullOrBlank() || direction == null || scheduledAt <= 0) {
            return null
        }
        return DepartureLiveUpdate(
            cityId = cityId,
            stopId = stopId,
            stopName = stopName,
            line = line,
            direction = direction,
            scheduledAtMillis = scheduledAt,
            scheduledSeconds = preferences.getInt("scheduled_seconds", 0),
            tripId = preferences.takeIf { it.contains("trip_id") }?.getInt("trip_id", 0),
        )
    }

    fun clear() {
        preferences.edit(commit = true) { clear() }
    }
}
