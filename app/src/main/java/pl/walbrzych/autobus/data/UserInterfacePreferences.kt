package pl.walbrzych.autobus.data

import android.content.Context
import androidx.core.content.edit

/** Blocks that may be independently shown and reordered on the main screen. */
enum class HomeScreenTile(val storageKey: String, val label: String) {
    NEARBY_STOPS("nearby_stops", "Najbliższe przystanki"),
    MAP("map", "Mapa"),
    SEARCH("search", "Wyszukiwarka"),
    FAVORITE_STOPS("favorite_stops", "Ulubione przystanki"),
}

data class HomeScreenConfiguration(
    val order: List<HomeScreenTile> = HomeScreenTile.entries,
    val visible: Set<HomeScreenTile> = HomeScreenTile.entries.toSet(),
) {
    val visibleTiles: List<HomeScreenTile> get() = order.filter { it in visible }
}

/** Makes persisted tile data forward-compatible and resistant to duplicate values. */
internal fun normalizedHomeScreenConfiguration(
    orderKeys: List<String>?,
    visibleKeys: Set<String>?,
): HomeScreenConfiguration {
    val byKey = HomeScreenTile.entries.associateBy(HomeScreenTile::storageKey)
    val ordered = orderKeys.orEmpty()
        .mapNotNull(byKey::get)
        .distinct()
        .let { saved -> saved + HomeScreenTile.entries.filterNot(saved::contains) }
    val visible = if (visibleKeys == null) HomeScreenTile.entries.toSet()
    else visibleKeys.mapNotNull(byKey::get).toSet()
    return HomeScreenConfiguration(ordered, visible)
}

/** Small, synchronous preferences store for user-owned presentation settings. */
class UserInterfacePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun showStopsWithoutLines(): Boolean = preferences.getBoolean(SHOW_STOPS_WITHOUT_LINES, false)

    fun setShowStopsWithoutLines(show: Boolean) {
        preferences.edit(commit = true) { putBoolean(SHOW_STOPS_WITHOUT_LINES, show) }
    }

    fun markTrackableDepartures(): Boolean = preferences.getBoolean(MARK_TRACKABLE_DEPARTURES, false)

    fun setMarkTrackableDepartures(mark: Boolean) {
        preferences.edit(commit = true) { putBoolean(MARK_TRACKABLE_DEPARTURES, mark) }
    }

    fun markInvalidMidnightDepartures(): Boolean = preferences.getBoolean(MARK_INVALID_MIDNIGHT_DEPARTURES, false)

    fun setMarkInvalidMidnightDepartures(mark: Boolean) {
        preferences.edit(commit = true) { putBoolean(MARK_INVALID_MIDNIGHT_DEPARTURES, mark) }
    }

    /** TLS can be enabled per user; legacy HTTP remains the compatible default. */
    fun useHttps(): Boolean = preferences.getBoolean(USE_HTTPS, false)

    fun setUseHttps(useHttps: Boolean) {
        preferences.edit(commit = true) { putBoolean(USE_HTTPS, useHttps) }
    }

    fun hideKanarAlert(): Boolean = preferences.getBoolean(HIDE_KANAR_ALERT, false)

    fun setHideKanarAlert(hide: Boolean) {
        preferences.edit(commit = true) { putBoolean(HIDE_KANAR_ALERT, hide) }
    }

    fun hideTickets(): Boolean = preferences.getBoolean(HIDE_TICKETS, false)

    fun setHideTickets(hide: Boolean) {
        preferences.edit(commit = true) { putBoolean(HIDE_TICKETS, hide) }
    }

    fun hideSettings(): Boolean = preferences.getBoolean(HIDE_SETTINGS, false)

    fun setHideSettings(hide: Boolean) {
        preferences.edit(commit = true) { putBoolean(HIDE_SETTINGS, hide) }
    }

    fun hasSeenKanarAlertIntroduction(): Boolean = preferences.getBoolean(KANAR_ALERT_INTRODUCTION_SEEN, false)

    fun setKanarAlertIntroductionSeen() {
        preferences.edit(commit = true) { putBoolean(KANAR_ALERT_INTRODUCTION_SEEN, true) }
    }

    fun homeScreenConfiguration(): HomeScreenConfiguration = normalizedHomeScreenConfiguration(
        preferences.getString(START_TILE_ORDER, null)?.split(ORDER_SEPARATOR)?.filter(String::isNotBlank),
        preferences.getStringSet(START_TILE_VISIBLE, null),
    )

    fun saveHomeScreenConfiguration(configuration: HomeScreenConfiguration) {
        val normalized = normalizedHomeScreenConfiguration(
            configuration.order.map(HomeScreenTile::storageKey),
            configuration.visible.map(HomeScreenTile::storageKey).toSet(),
        )
        preferences.edit(commit = true) {
            putString(START_TILE_ORDER, normalized.order.joinToString(ORDER_SEPARATOR) { it.storageKey })
            putStringSet(START_TILE_VISIBLE, normalized.visible.map(HomeScreenTile::storageKey).toSet())
        }
    }

    fun favoriteStopIds(cityId: Int): Set<String> =
        preferences.getStringSet("$FAVORITES_PREFIX$cityId", emptySet()).orEmpty().toSet()

    fun setFavoriteStop(cityId: Int, stopId: String, favorite: Boolean): Set<String> {
        val updated = favoriteStopIds(cityId).toMutableSet().apply {
            if (favorite) add(stopId) else remove(stopId)
        }.toSet()
        preferences.edit(commit = true) { putStringSet("$FAVORITES_PREFIX$cityId", updated) }
        return updated
    }

    private companion object {
        const val PREFERENCES = "user_interface_preferences"
        const val SHOW_STOPS_WITHOUT_LINES = "show_stops_without_lines"
        const val MARK_TRACKABLE_DEPARTURES = "mark_trackable_departures"
        const val MARK_INVALID_MIDNIGHT_DEPARTURES = "mark_invalid_midnight_departures"
        const val USE_HTTPS = "use_https"
        const val HIDE_KANAR_ALERT = "hide_kanar_alert"
        const val HIDE_TICKETS = "hide_tickets"
        const val HIDE_SETTINGS = "hide_settings"
        const val KANAR_ALERT_INTRODUCTION_SEEN = "kanar_alert_introduction_seen"
        const val START_TILE_ORDER = "start_tile_order"
        const val START_TILE_VISIBLE = "start_tile_visible"
        const val FAVORITES_PREFIX = "favorite_stops_"
        const val ORDER_SEPARATOR = ","
    }
}

/** Empty timetable records are operational placeholders, not usable passenger stops. */
internal fun filterStopsForDisplay(stops: List<StopData>, showStopsWithoutLines: Boolean): List<StopData> =
    if (showStopsWithoutLines) stops else stops.filter { it.timetables.isNotEmpty() }
