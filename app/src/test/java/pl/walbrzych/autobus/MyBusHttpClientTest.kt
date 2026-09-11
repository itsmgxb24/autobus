package pl.walbrzych.autobus

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.MyBusHttpClient

class MyBusHttpClientTest {
    @Test
    fun sendsRecoveredHeadersAndCompareParameters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>100</int>"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>1</int>"))
            val client = MyBusHttpClient(server.url("/myBusServices/SchedulesService.svc/"), "WALBR")

            assertEquals(100, client.ping())
            assertTrue(client.compareSchedule(377, 1))

            val ping = server.takeRequest()
            assertEquals("/myBusServices/SchedulesService.svc/PingService", ping.path)
            assertEquals("myBusOnline", ping.getHeader("User-Agent"))
            assertEquals("436", ping.getHeader("Age"))
            val compare = server.takeRequest()
            assertEquals("/myBusServices/SchedulesService.svc/CompareScheduleFile?nIdWersja=377&nGeneracja=1", compare.path)
            assertEquals("476", compare.getHeader("Age"))
        }
    }
}
