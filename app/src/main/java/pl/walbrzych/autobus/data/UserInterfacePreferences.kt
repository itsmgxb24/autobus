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
        const val START_TILE_ORDER = "start_tile_order"
        const val START_TILE_VISIBLE = "start_tile_visible"
        const val FAVORITES_PREFIX = "favorite_stops_"
        const val ORDER_SEPARATOR = ","
    }
}

/** Empty timetable records are operational placeholders, not usable passenger stops. */
internal fun filterStopsForDisplay(stops: List<StopData>, showStopsWithoutLines: Boolean): List<StopData> =
    if (showStopsWithoutLines) stops else stops.filter { it.timetables.isNotEmpty() }
