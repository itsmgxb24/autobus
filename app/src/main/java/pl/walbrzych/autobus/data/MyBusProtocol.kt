package pl.walbrzych.autobus.data

import java.io.ByteArrayInputStream
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Exact network surface recovered from MyBus Online 2.8.11. */
interface MyBusService {
    suspend fun ping(): Int
    suspend fun compareSchedule(version: Int, generation: Int): Boolean
    suspend fun downloadSchedule(): ByteArray
    suspend fun realTimeDepartures(stopId: Int, groupId: Int = 0): RealTimeDepartures
    suspend fun departureInfo(date: LocalDate, stopId: Int, uniqueTripId: Long): DepartureInfo?
    suspend fun vehicles(line: String, directionCode: String): List<LiveVehicle>
}

object MyBusXmlParser {
    fun parsePing(xml: ByteArray): Int = scalarInt(xml, "PingService")

    fun parseCompareSchedule(xml: ByteArray): Boolean = scalarInt(xml, "CompareScheduleFile") == 1

    fun parseRealTimeDepartures(xml: ByteArray): RealTimeDepartures {
        val root = document(xml).documentElement.requireName("Departures")
        val serverTime = root.required("time")
        var notice: String? = null
        val departures = root.elementChildren().mapNotNull { element ->
            when (element.tagName) {
                "N" -> {
                    notice = element.textContent.trim().takeIf(String::isNotEmpty)
                    null
                }
                "D" -> RealTimeDeparture(
                    departureId = element.required("i").toInt(),
                    tripId = element.required("di", "i").toInt(),
                    line = element.required("r").trim(),
                    direction = element.required("d").trim(),
                    directionCode = element.optional("dd")?.trim()?.takeIf(String::isNotEmpty),
                    scheduledSeconds = element.required("t").toInt(),
                    displayValue = element.required("v").trim(),
                    status = element.required("m").toInt(),
                    vehicleNumber = element.optional("n")?.toIntOrNull()?.takeIf { it > 0 },
                )
                else -> null
            }
        }
        return RealTimeDepartures(serverTime, notice, departures)
    }

    fun parseVehicles(xml: ByteArray): List<LiveVehicle> {
        val root = document(xml).documentElement.requireName("VL")
        return root.elementChildren().filter { it.tagName == "V" }.map { element ->
            LiveVehicle(
                vehicleId = element.required("id").toLong(),
                sideNumber = element.required("nb").toInt(),
                line = element.optional("nr")?.trim().orEmpty(),
                variant = element.optional("wt")?.trim().orEmpty(),
                directionCode = element.optional("kr")?.trim().orEmpty(),
                latitude = element.required("y").coordinate(),
                longitude = element.required("x").coordinate(),
                predictedLatitude = element.optional("py")?.coordinateOrNull(),
                predictedLongitude = element.optional("px")?.coordinateOrNull(),
                destination = element.optional("op")?.trim().orEmpty(),
                reportedAt = element.optional("p")?.trim().orEmpty(),
            )
        }
    }

    fun parseDepartureInfo(xml: ByteArray): DepartureInfo? {
        val root = document(xml).documentElement
        val element = when (root.tagName) {
            "D" -> root
            else -> root.elementChildren().firstOrNull { it.tagName == "D" }
        } ?: return null
        return DepartureInfo(
            departureId = element.required("i").toInt(),
            scheduledSeconds = element.required("t").toInt(),
            routeVariantId = element.required("vr").toInt(),
            line = element.required("r").trim(),
            directionCode = element.optional("dd")?.trim()?.takeIf(String::isNotEmpty),
            direction = element.required("d").trim(),
            displayValue = element.required("v").trim(),
            vehicleNumber = element.optional("n")?.toIntOrNull()?.takeIf { it > 0 },
            positionStatus = element.required("p").trim(),
            vehicleName = element.required("vn").trim(),
            status = element.required("m").toInt(),
        )
    }

    private fun scalarInt(xml: ByteArray, operation: String): Int {
        val root = document(xml).documentElement
        require(root.tagName == "int") { "$operation: oczekiwano XML <int>." }
        return root.textContent.trim().toIntOrNull()
            ?: throw IllegalArgumentException("$operation: nieprawidłowa wartość <int>.")
    }

    private fun document(xml: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // Responses are data-only XML; disable external expansion before parsing server input.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        // Android's built-in DOM factory throws UnsupportedOperationException from
        // setXIncludeAware itself ("Unknown version 0.0"). The platform parser has no
        // XInclude support, so leaving its default is both compatible and non-expanding.
        runCatching { isXIncludeAware = false }
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml))

    private fun Element.requireName(name: String): Element {
        require(tagName == name) { "Oczekiwano <$name>, otrzymano <$tagName>." }
        return this
    }

    private fun Element.elementChildren(): List<Element> = buildList {
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) add(child as Element)
        }
    }

    private fun Element.required(name: String, fallback: String? = null): String =
        optional(name) ?: fallback?.let { optional(it) }
        ?: throw IllegalArgumentException("<$tagName> nie ma wymaganego atrybutu $name.")

    private fun Element.optional(name: String): String? =
        getAttribute(name).takeIf { hasAttribute(name) }

    private fun String.coordinate(): Double = coordinateOrNull()
        ?: throw IllegalArgumentException("Nieprawidłowa współrzędna: $this")

    private fun String.coordinateOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
}
