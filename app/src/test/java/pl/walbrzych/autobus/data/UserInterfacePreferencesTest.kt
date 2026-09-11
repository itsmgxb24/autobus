package pl.walbrzych.autobus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInterfacePreferencesTest {
    @Test
    fun missingTilePreferencesEnableEveryDefaultTile() {
        val configuration = normalizedHomeScreenConfiguration(null, null)

        assertEquals(HomeScreenTile.entries, configuration.order)
        assertEquals(HomeScreenTile.entries.toSet(), configuration.visible)
    }

    @Test
    fun tilePreferencesIgnoreUnknownAndDuplicateValuesButKeepACompleteOrder() {
        val configuration = normalizedHomeScreenConfiguration(
            orderKeys = listOf("map", "unknown", "map", "search"),
            visibleKeys = setOf("map", "favorite_stops", "unknown"),
        )

        assertEquals(
            listOf(
                HomeScreenTile.MAP,
                HomeScreenTile.SEARCH,
                HomeScreenTile.NEARBY_STOPS,
                HomeScreenTile.FAVORITE_STOPS,
            ),
            configuration.order,
        )
        assertEquals(setOf(HomeScreenTile.MAP, HomeScreenTile.FAVORITE_STOPS), configuration.visible)
    }

    @Test
    fun stopsWithoutTimetablesAreHiddenUnlessDeveloperOptionIsEnabled() {
        val served = StopData("served", "Obsługiwany", 50.0, 16.0, listOf(timetable()))
        val placeholder = StopData("placeholder", "Techniczny", 50.1, 16.1, emptyList())

        assertEquals(listOf(served), filterStopsForDisplay(listOf(served, placeholder), false))
        assertTrue(filterStopsForDisplay(listOf(served, placeholder), true).contains(placeholder))
        assertFalse(filterStopsForDisplay(listOf(served, placeholder), false).contains(placeholder))
    }

    private fun timetable() = TimetableData("4", "Centrum", DayType.WORKING, listOf("08:00"))
}
