package pl.ruby.lubiechowlabs.autobus.data

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Coordinates the selected operator protocol with its last known-good local cache. */
class TransitRepository(
    context: Context,
    private val city: CityConfig,
    useHttps: Boolean = UserInterfacePreferences(context).useHttps(),
) {
    private val appContext = context.applicationContext
    private val myBusStore by lazy { ScheduleFileStore(appContext, city.id) }
    private val gtfsStore by lazy { GtfsScheduleStore(appContext, city.id) }
    private val myBusService by lazy {
        MyBusHttpClient("${city.serviceBaseUrl(useHttps).trimEnd('/')}/".toHttpUrl(), city.cityCode)
    }
    private val myBusSync by lazy { ScheduleSyncCoordinator(myBusService, myBusStore) }
    private val zditmClient by lazy { ZditmGtfsClient() }
    private val zditmRealtime by lazy { ZditmRealtimeRepository(gtfsStore, zditmClient) }

    suspend fun cachedSchedule(): ScheduleSnapshot? = withContext(Dispatchers.IO) {
        when (city.dataSource) {
            CityDataSource.MYBUS -> myBusStore.cachedSnapshot()
            CityDataSource.ZDITM_GTFS -> gtfsStore.cachedSnapshot()
        }
    }

    fun cacheIntegrity(): ScheduleIntegrityReport? = when (city.dataSource) {
        CityDataSource.MYBUS -> myBusStore.activeIntegrity()
        CityDataSource.ZDITM_GTFS -> gtfsStore.activeIntegrity()
    }

    suspend fun loadInitial(): Result<ScheduleSnapshot> = withContext(Dispatchers.IO) {
        cachedSchedule()?.let(Result.Companion::success) ?: synchronize().map { it.snapshot }
    }

    suspend fun synchronize(): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            when (city.dataSource) {
                CityDataSource.MYBUS -> myBusSync.synchronize()
                CityDataSource.ZDITM_GTFS -> synchronizeZditm()
            }
        }
    }

    suspend fun realTimeDepartures(stopId: String): Result<RealTimeDepartures> = withContext(Dispatchers.IO) {
        runCatching {
            when (city.dataSource) {
                CityDataSource.MYBUS -> myBusService.realTimeDepartures(stopId.toInt())
                CityDataSource.ZDITM_GTFS -> zditmRealtime.departures(stopId)
            }
        }
    }

    /** Not called by stop UI: nUqTripId is obtained only from the route-planner response. */
    suspend fun departureInfo(date: LocalDate, stopId: String, uniqueTripId: Long): Result<DepartureInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            when (city.dataSource) {
                CityDataSource.MYBUS -> myBusService.departureInfo(date, stopId.toInt(), uniqueTripId)
                CityDataSource.ZDITM_GTFS -> null
            }
        }
    }

    suspend fun vehicles(line: String, directionCode: String): Result<List<LiveVehicle>> = withContext(Dispatchers.IO) {
        runCatching {
            when (city.dataSource) {
                CityDataSource.MYBUS -> myBusService.vehicles(line, directionCode)
                CityDataSource.ZDITM_GTFS -> zditmRealtime.vehicles().filter { vehicle ->
                    vehicle.line == line && (directionCode.isBlank() || vehicle.directionCode == directionCode)
                }
            }
        }
    }

    suspend fun vehiclesBySideNumber(sideNumber: Int): Result<List<LiveVehicle>> = withContext(Dispatchers.IO) {
        runCatching {
            when (city.dataSource) {
                CityDataSource.MYBUS -> myBusService.vehiclesBySideNumber(sideNumber)
                CityDataSource.ZDITM_GTFS -> zditmRealtime.vehicles().filter { it.sideNumber == sideNumber }
            }
        }
    }

    private suspend fun synchronizeZditm(): SyncResult {
        val cached = gtfsStore.cachedSnapshot()
        val (eTag, lastModified) = gtfsStore.requestValidators()
        val response = zditmClient.schedule(eTag, lastModified)
        if (cached != null && gtfsStore.isCurrent(response)) return SyncResult.Current(cached)
        val download = response.bytes ?: throw java.io.IOException("ZDiTM nie zwrócił pobranego rozkładu.")
        return SyncResult.Downloaded(gtfsStore.install(download, response))
    }
}

/** Used by widgets, which read the same city-scoped atomic cache as the app. */
fun cachedScheduleForCity(context: Context, city: CityConfig): ScheduleSnapshot? = when (city.dataSource) {
    CityDataSource.MYBUS -> ScheduleFileStore(context.applicationContext, city.id).cachedSnapshot()
    CityDataSource.ZDITM_GTFS -> GtfsScheduleStore(context.applicationContext, city.id).cachedSnapshot()
}

private val SyncResult.snapshot: ScheduleSnapshot
    get() = when (this) {
        is SyncResult.Current -> snapshot
        is SyncResult.Downloaded -> snapshot
    }
