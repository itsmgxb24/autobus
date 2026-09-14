package pl.ruby.lubiechowlabs.autobus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/** A small shared, permission-free scheduler for widgets driven by a timetable. */
internal class WidgetRefreshScheduler(
    private val requestCode: Int,
    private val receiver: Class<*>,
    private val action: String,
) {
    fun schedule(context: Context, refreshAt: Instant?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        refreshAt ?: return
        val triggerAt = refreshAt.toEpochMilli().coerceAtLeast(System.currentTimeMillis() + MIN_DELAY_MILLIS)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Exact alarms are not required; this fallback remains correct and the
            // launcher can also request an ordinary widget update.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, receiver).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val MIN_DELAY_MILLIS = 5_000L
    }
}
