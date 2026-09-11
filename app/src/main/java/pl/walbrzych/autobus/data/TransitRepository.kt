package pl.walbrzych.autobus.data

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Coordinates the verified MyBus protocol with the last known-good local SQLite cache. */
class TransitRepository(
    context: Context,
    city: CityConfig,
    private val service: MyBusService = MyBusHttpClient("${city.baseUrl.trimEnd('/')}/".toHttpUrl(), city.cityCode),
    private val store: ScheduleFileStore = ScheduleFileStore(context, city.id),
) {
    private val syncCoordinator = ScheduleSyncCoordinator(service, store)

    suspend fun cachedSchedule(): ScheduleSnapshot? = withContext(Dispatchers.IO) { store.cachedSnapshot() }

    fun cacheIntegrity(): ScheduleIntegrityReport? = store.activeIntegrity()

    suspend fun loadInitial(): Result<ScheduleSnapshot> = withContext(Dispatchers.IO) {
        store.cachedSnapshot()?.let(Result.Companion::success) ?: synchronize().map { it.snapshot }
    }

    suspend fun synchronize(): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching { syncCoordinator.synchronize() }
    }

    suspend fun realTimeDepartures(stopId: String): Result<RealTimeDepartures> = withContext(Dispatchers.IO) {
        runCatching { service.realTimeDepartures(stopId.toInt()) }
    }

    /** Not called by stop UI: nUqTripId is obtained only from the route-planner response. */
    suspend fun departureInfo(date: LocalDate, stopId: String, uniqueTripId: Long): Result<DepartureInfo?> = withContext(Dispatchers.IO) {
        runCatching { service.departureInfo(date, stopId.toInt(), uniqueTripId) }
    }

    suspend fun vehicles(line: String, directionCode: String): Result<List<LiveVehicle>> = withContext(Dispatchers.IO) {
        runCatching { service.vehicles(line, directionCode) }
    }
}

private val SyncResult.snapshot: ScheduleSnapshot
    get() = when (this) {
        is SyncResult.Current -> snapshot
        is SyncResult.Downloaded -> snapshot
    }
