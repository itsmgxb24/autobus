package pl.walbrzych.autobus.ui

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Stores all locally generated tickets, grouped by the city chosen at purchase time. */
data class PurchasedTicket(
    val ticketId: String,
    val purchasedAtMillis: Long,
    val cityId: Int,
)

class TicketPurchaseStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("ticket_purchase", Context.MODE_PRIVATE)

    fun readAll(cityId: Int): List<PurchasedTicket> = storedPurchases()
        .filter { it.cityId == cityId }
        .sortedByDescending(PurchasedTicket::purchasedAtMillis)

    fun save(cityId: Int, ticket: Ticket): PurchasedTicket {
        val purchase = PurchasedTicket(ticket.id, System.currentTimeMillis(), cityId)
        val purchases = storedPurchases() + purchase
        writePurchases(purchases)
        return purchase
    }

    /** Demo tickets have no payment record, so removal is immediate and local. */
    fun delete(purchase: PurchasedTicket): Boolean {
        val stored = storedPurchases()
        val remaining = stored.filterNot { saved ->
            saved.ticketId == purchase.ticketId &&
                saved.purchasedAtMillis == purchase.purchasedAtMillis &&
                saved.cityId == purchase.cityId
        }
        if (remaining.size == stored.size) return false
        writePurchases(remaining)
        return true
    }

    private fun writePurchases(purchases: List<PurchasedTicket>) {
        preferences.edit(commit = true) {
            putString(PURCHASES, JSONArray().apply {
                purchases.forEach { saved ->
                    put(JSONObject().apply {
                        put(CITY_ID, saved.cityId)
                        put(TICKET_ID, saved.ticketId)
                        put(PURCHASED_AT, saved.purchasedAtMillis)
                    })
                }
            }.toString())
            remove(CITY_ID)
            remove(TICKET_ID)
            remove(PURCHASED_AT)
        }
    }

    private fun storedPurchases(): List<PurchasedTicket> {
        val saved = preferences.getString(PURCHASES, null)?.let(::decodePurchases)
        if (saved != null) return saved
        return legacyPurchase()?.let(::listOf).orEmpty()
    }

    private fun decodePurchases(serialized: String): List<PurchasedTicket> = runCatching {
        val array = JSONArray(serialized)
        buildList {
            repeat(array.length()) { index ->
                val entry = array.optJSONObject(index) ?: return@repeat
                val cityId = entry.optInt(CITY_ID, -1)
                val ticketId = entry.optString(TICKET_ID)
                val purchasedAt = entry.optLong(PURCHASED_AT, 0L)
                if (cityId >= 0 && ticketId.isNotBlank() && purchasedAt > 0L) {
                    add(PurchasedTicket(ticketId, purchasedAt, cityId))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun legacyPurchase(): PurchasedTicket? {
        val cityId = preferences.getInt(CITY_ID, -1)
        val ticketId = preferences.getString(TICKET_ID, null) ?: return null
        val purchasedAt = preferences.getLong(PURCHASED_AT, 0L)
        return PurchasedTicket(ticketId, purchasedAt, cityId).takeIf { it.purchasedAtMillis > 0L }
    }

    private companion object {
        const val CITY_ID = "city_id"
        const val TICKET_ID = "ticket_id"
        const val PURCHASED_AT = "purchased_at"
        const val PURCHASES = "purchases"
    }
}
