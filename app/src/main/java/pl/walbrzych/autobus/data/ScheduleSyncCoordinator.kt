package pl.walbrzych.autobus.data

/** Small platform-free update transaction, kept separate so its failure rules are unit-tested. */
interface ScheduleCache {
    fun cachedSnapshot(): ScheduleSnapshot?
    fun install(gzipPayload: ByteArray): ScheduleSnapshot
}

class ScheduleSyncCoordinator(
    private val service: MyBusService,
    private val cache: ScheduleCache,
) {
    suspend fun synchronize(): SyncResult {
        val cached = cache.cachedSnapshot()
        service.ping()
        return if (cached != null && service.compareSchedule(cached.version.version, cached.version.generation)) {
            SyncResult.Current(cached)
        } else {
            // install must validate the staging database before changing its active pointer.
            SyncResult.Downloaded(cache.install(service.downloadSchedule()))
        }
    }
}
