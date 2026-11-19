package pl.ruby.lubiechowlabs.autobus.busdevice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BusDeviceProtocolTest {
    @Test
    fun settingsUseLittleEndianIdsAndUtf8StopNames() {
        val config = BusDeviceSettings(10, 395, "Łódź", BusDeviceTheme.RED_WHITE, 5)
        val wire = "010a008b01000005000107c581c3b364c5ba".hexToByteArray()
        assertArrayEquals(wire, BusDeviceProtocol.encode(config))
        assertEquals(config, BusDeviceProtocol.decode(wire))
    }

    @Test
    fun corruptNameLengthAndUnsupportedProtocolAreRejected() {
        val wire = BusDeviceProtocol.encode(BusDeviceSettings(10, 395, "Plac Grunwaldzki", BusDeviceTheme.MONO, 5))
        assertThrows(IllegalStateException::class.java) { BusDeviceProtocol.decode(wire.copyOf(wire.size - 1)) }
        wire[0] = 2
        assertThrows(IllegalStateException::class.java) { BusDeviceProtocol.decode(wire) }
    }

    @Test
    fun anAckCannotBeReusedForAnotherTransactionOrOffset() {
        val reply = byteArrayOf(0, 8, 16, 0, 34)
        assertArrayEquals(byteArrayOf(34), BusDeviceProtocol.payload(reply, 8, 16))
        assertThrows(IllegalStateException::class.java) { BusDeviceProtocol.payload(reply, 9, 16) }
        assertThrows(IllegalStateException::class.java) { BusDeviceProtocol.payload(reply, 8, 0) }
    }
}
