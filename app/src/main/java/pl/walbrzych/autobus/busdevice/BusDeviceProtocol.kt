package pl.ruby.lubiechowlabs.autobus.busdevice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

internal enum class BusDeviceTheme(val id: Int, val title: String, val color: Long, val background: Long, val accent: Long = color) {
    MONO(0, "Czarno-biały", 0xFF141414, 0xFFFFFFFF),
    RED_WHITE(1, "Biało-czerwony", 0xFFB91C1C, 0xFFFFFFFF),
    YELLOW_ACCENT(2, "Żółty akcent", 0xFFB91C1C, 0xFFFFFFFF, 0xFFF5C518),
    RED_INVERTED(3, "Czerwone tło", 0xFFFFFFFF, 0xFFB91C1C),
}

internal data class BusDeviceSettings(
    val cityId: Int,
    val stopId: Int,
    val stopName: String,
    val theme: BusDeviceTheme,
    val refreshMinutes: Int,
)

internal object BusDeviceProtocol {
    val service: UUID = UUID.fromString("add0b001-24a0-4d80-9f70-ab0000000001")
    val request: UUID = UUID.fromString("add0b001-24a0-4d80-9f70-ab0000000002")
    val response: UUID = UUID.fromString("add0b001-24a0-4d80-9f70-ab0000000003")

    const val Read = 1
    const val Stage = 2
    const val Commit = 3
    const val FrameHeader = 4
    const val Chunk = 16
    const val MinMinutes = 1
    const val MaxMinutes = 60
    const val MaxSettingsBytes = 251
    private const val Version = 1
    private const val SettingsHeader = 11

    fun frame(operation: Int, transaction: Int, offset: Int, data: ByteArray = byteArrayOf()): ByteArray =
        ByteBuffer.allocate(FrameHeader + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(operation.toByte()).put(transaction.toByte()).putShort(offset.toShort()).put(data).array()

    fun payload(frame: ByteArray, transaction: Int, offset: Int): ByteArray {
        check(frame.size in FrameHeader..(FrameHeader + Chunk)) { "Nieprawidłowa odpowiedź BusDevice." }
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        val result = buffer.get().toInt() and 0xff
        check((buffer.get().toInt() and 0xff) == transaction && (buffer.short.toInt() and 0xffff) == offset) {
            "BusDevice zwrócił nieaktualną odpowiedź. Spróbuj ponownie."
        }
        check(result == 0) {
            when (result) {
                2 -> "BusDevice odrzucił ustawienia. Sprawdź miasto, przystanek i interwał."
                3 -> "BusDevice nie zapisał ustawień w pamięci."
                else -> "Transmisja ustawień została przerwana. Spróbuj ponownie."
            }
        }
        return frame.copyOfRange(FrameHeader, frame.size)
    }

    fun encode(settings: BusDeviceSettings): ByteArray {
        val name = settings.stopName.toByteArray(Charsets.UTF_8)
        require(settings.cityId in 1..65535 && settings.stopId > 0 &&
            settings.refreshMinutes in MinMinutes..MaxMinutes && name.isNotEmpty() &&
            name.size <= MaxSettingsBytes - SettingsHeader) { "Nieprawidłowa konfiguracja BusDevice." }
        return ByteBuffer.allocate(SettingsHeader + name.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(Version.toByte()).putShort(settings.cityId.toShort()).putInt(settings.stopId)
            .putShort(settings.refreshMinutes.toShort()).put(settings.theme.id.toByte())
            .put(name.size.toByte()).put(name).array()
    }

    fun decode(bytes: ByteArray): BusDeviceSettings {
        check(bytes.size in (SettingsHeader + 1)..MaxSettingsBytes) { "BusDevice zwrócił niepełne ustawienia." }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(data.get().toInt() == Version) { "Ta wersja oprogramowania BusDevice wymaga aktualizacji." }
        val cityId = data.short.toInt() and 0xffff
        val stopId = data.int
        val minutes = data.short.toInt() and 0xffff
        val theme = BusDeviceTheme.entries.firstOrNull { it.id == (bytes[9].toInt() and 0xff) }
        val nameSize = bytes[10].toInt() and 0xff
        check(cityId > 0 && stopId > 0 && minutes in MinMinutes..MaxMinutes && theme != null &&
            nameSize == bytes.size - SettingsHeader) { "BusDevice zwrócił nieprawidłowe ustawienia." }
        val name = bytes.decodeToString(SettingsHeader, bytes.size, throwOnInvalidSequence = true)
        return BusDeviceSettings(cityId, stopId, name, theme, minutes)
    }
}
