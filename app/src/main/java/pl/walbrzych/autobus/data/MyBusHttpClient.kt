package pl.ruby.lubiechowlabs.autobus.data

import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resumeWithException

private const val INITIAL_SERVER_TOKEN = 60

/**
 * GET client matching `g4/c.java` and `g4/e.java` from MyBus Online. A cancelled
 * coroutine cancels the underlying OkHttp call, so live requests do not outlive a screen.
 */
class MyBusHttpClient(
    private val baseUrl: HttpUrl,
    private val cityCode: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) : MyBusService {
    private val tokenLock = Mutex()
    private var sessionToken: Int? = null

    override suspend fun ping(): Int = tokenLock.withLock { pingLocked() }

    override suspend fun compareSchedule(version: Int, generation: Int): Boolean =
        MyBusXmlParser.parseCompareSchedule(
            get(
                "CompareScheduleFile",
                mapOf("nIdWersja" to version.toString(), "nGeneracja" to generation.toString()),
            ),
        )

    override suspend fun downloadSchedule(): ByteArray = get("GetScheduleFile")

    override suspend fun realTimeDepartures(stopId: Int, groupId: Int): RealTimeDepartures =
        MyBusXmlParser.parseRealTimeDepartures(
            get(
                "GetTimeTableReal",
                mapOf("nBusStopId" to stopId.toString(), "nBusStopGroupId" to groupId.toString()),
            ),
        )

    override suspend fun departureInfo(date: LocalDate, stopId: Int, uniqueTripId: Long): DepartureInfo? =
        MyBusXmlParser.parseDepartureInfo(
            get(
                "GetDepartureInfo",
                mapOf(
                    "cDate" to date.toString(),
                    "nBusStopId" to stopId.toString(),
                    "nUqTripId" to uniqueTripId.toString(),
                ),
            ),
        )

    override suspend fun vehicles(line: String, directionCode: String): List<LiveVehicle> =
        MyBusXmlParser.parseVehicles(
            get(
                "GetVehicles",
                mapOf(
                    "cNbLst" to "",
                    "cIdLst" to "",
                    "cRouteLst" to line,
                    "cTrackLst" to "",
                    "cDirLst" to directionCode,
                    "cKrsLst" to "",
                ),
            ),
        )

    override suspend fun vehiclesBySideNumber(sideNumber: Int): List<LiveVehicle> =
        MyBusXmlParser.parseVehicles(
            get(
                "GetVehicles",
                mapOf(
                    "cNbLst" to sideNumber.toString(),
                    "cIdLst" to "",
                    "cRouteLst" to "",
                    "cTrackLst" to "",
                    "cDirLst" to "",
                    "cKrsLst" to "",
                ),
            ),
        )

    private suspend fun get(endpoint: String, query: Map<String, String> = emptyMap()): ByteArray {
        val token = tokenLock.withLock { sessionToken ?: pingLocked() }
        val url = baseUrl.newBuilder().addPathSegment(endpoint).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        return try {
            requestWithSingleRetry(url, token)
        } catch (failure: MyBusHttpException) {
            // MyBus invalidates an old session through an HTTP authentication-style
            // response. Refresh once, then retry the original request. The mutex
            // prevents concurrent refreshes from creating a PingService stampede.
            if (failure.code !in setOf(400, 401, 403)) throw failure
            val refreshedToken = tokenLock.withLock {
                if (sessionToken == token) sessionToken = null
                sessionToken ?: pingLocked()
            }
            requestWithSingleRetry(url, refreshedToken)
        }
    }

    private suspend fun pingLocked(): Int {
        val url = baseUrl.newBuilder().addPathSegment("PingService").build()
        val token = MyBusXmlParser.parsePing(requestWithSingleRetry(url, INITIAL_SERVER_TOKEN))
        require(token > 0) { "PingService zwrócił nieprawidłowy token." }
        sessionToken = token
        return token
    }

    private suspend fun requestWithSingleRetry(url: HttpUrl, token: Int): ByteArray {
        var lastFailure: IOException? = null
        repeat(2) { attempt ->
            try {
                return request(url, token)
            } catch (failure: MyBusHttpException) {
                if (failure.code !in 500..599 || attempt == 1) throw failure
                lastFailure = failure
            } catch (failure: IOException) {
                if (attempt == 1) throw failure
                lastFailure = failure
            }
            delay(250L)
        }
        throw lastFailure ?: IOException("Nie udało się wykonać żądania MyBus.")
    }

    private suspend fun request(url: HttpUrl, token: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "myBusOnline")
            .header("Age", (token + cityCode.sumOf { it.code }).toString())
            .get()
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(MyBusHttpException(it.code, "HTTP ${it.code} z MyBus."))
                        }
                        return
                    }
                    val bytes = try {
                        it.body?.bytes() ?: throw IOException("MyBus zwrócił pustą odpowiedź.")
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return
                    }
                    if (continuation.isActive) continuation.resumeWith(Result.success(bytes))
                }
            }
        })
    }
}

class MyBusHttpException(val code: Int, message: String) : IOException(message)
