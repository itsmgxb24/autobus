package pl.walbrzych.autobus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.MyBusXmlParser

class MyBusXmlParserTest {
    @Test
    fun parsesPingAndVersionComparison() {
        assertEquals(2046, MyBusXmlParser.parsePing("<int>2046</int>".encodeToByteArray()))
        assertTrue(MyBusXmlParser.parseCompareSchedule("<int>1</int>".encodeToByteArray()))
        assertFalse(MyBusXmlParser.parseCompareSchedule("<int>0</int>".encodeToByteArray()))
    }

    @Test
    fun distinguishesServerEtaFromScheduledTime() {
        val departures = MyBusXmlParser.parseRealTimeDepartures(
            """
            <Departures time="18:24"><N>Informacja przewoźnika</N>
              <D i="2" t="68520" r="11" d="PONIATÓW" dd="A" n="123" m="1" v="6 min" />
              <D i="3" t="68580" r="11" d="PONIATÓW" m="0" v="19:03" />
            </Departures>
            """.trimIndent().encodeToByteArray(),
        )

        assertEquals("Informacja przewoźnika", departures.notice)
        assertEquals(6, departures.departures[0].etaMinutes)
        assertEquals("Przyjazd za 6 min", departures.departures[0].arrivalLabel)
        assertNull(departures.departures[1].etaMinutes)
        assertEquals("Przyjazd: 19:03", departures.departures[1].arrivalLabel)
    }

    @Test
    fun parsesVehiclesAndDepartureInfoContract() {
        val vehicles = MyBusXmlParser.parseVehicles(
            "<VL><V id=\"7\" nb=\"42\" nr=\"11\" wt=\"1\" kr=\"A\" x=\"16,284\" y=\"50,765\" px=\"16.285\" py=\"50.766\" op=\"PONIATÓW\" p=\"18:24\" /></VL>".encodeToByteArray(),
        )
        assertEquals(1, vehicles.size)
        assertEquals(50.765, vehicles.single().latitude, 0.0001)
        assertEquals(16.284, vehicles.single().longitude, 0.0001)

        val info = MyBusXmlParser.parseDepartureInfo(
            "<D i=\"2\" t=\"68520\" vr=\"1\" r=\"11\" dd=\"A\" d=\"PONIATÓW\" v=\"6 min\" n=\"42\" p=\"A\" vn=\"Solaris\" m=\"1\" />".encodeToByteArray(),
        )!!
        assertEquals(1, info.routeVariantId)
        assertEquals("Solaris", info.vehicleName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedRealtimeResponse() {
        MyBusXmlParser.parseRealTimeDepartures("<Departures time=\"18:24\"><D i=\"2\" /></Departures>".encodeToByteArray())
    }
}
