@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.walbrzych.autobus.data.LiveVehicle
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.TimetableData
import pl.walbrzych.autobus.data.TransitRepository

/**
 * Shows the actual ordered stop identifiers from KIERUNKI.trasa. This database has
 * no geometry points, so the line on the map connects stop points, never invented roads.
 * Vehicle locations are requested only after an explicit user action.
 */
@Composable
fun LineDetailScreen(
    stop: StopData,
    line: String,
    allStops: List<StopData>,
    onBack: () -> Unit,
    repository: TransitRepository,
    onFullscreenMap: (TimetableData) -> Unit,
) {
    val variants = remember(allStops, line) {
        allStops.flatMap { it.timetables }.filter { it.line == line }
            .distinctBy { "${it.variant}|${it.directionCode}|${it.direction}" }
    }
    var selectedKey by rememberSaveable(stop.id, line) {
        mutableStateOf(variants.firstOrNull()?.variantKey().orEmpty())
    }
    val selectedVariant = variants.firstOrNull { it.variantKey() == selectedKey } ?: variants.firstOrNull()
    val routeStops = remember(selectedVariant, allStops) {
        val byId = allStops.associateBy { it.id }
        selectedVariant?.routeStopIds?.mapNotNull(byId::get).orEmpty()
    }
    val scope = rememberCoroutineScope()
    var vehicleState by remember(selectedVariant?.variantKey()) { mutableStateOf<VehicleState>(VehicleState.Idle) }

    fun requestVehicles() {
        val variant = selectedVariant ?: return
        if (variant.directionCode.isBlank()) {
            vehicleState = VehicleState.Unavailable("Ten wariant nie ma kodu kierunku dla GetVehicles.")
            return
        }
        vehicleState = VehicleState.Loading
        scope.launch {
            vehicleState = repository.vehicles(line, variant.directionCode)
                .fold(
                    onSuccess = { VehicleState.Loaded(it) },
                    onFailure = { VehicleState.Unavailable(it.message ?: "Nie udało się pobrać pozycji pojazdów.") },
                )
        }
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Linia $line") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    selectedVariant?.direction ?: "Brak wariantu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (variants.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        variants.forEach { variant ->
                            FilterChip(
                                selected = variant.variantKey() == selectedKey,
                                onClick = { selectedKey = variant.variantKey() },
                                label = {
                                    Text(
                                        variant.direction,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                LineRouteMap(
                    stopsForRoute = routeStops,
                    line = line,
                    direction = selectedVariant?.direction.orEmpty(),
                    vehicles = (vehicleState as? VehicleState.Loaded)?.vehicles.orEmpty(),
                    onMapClick = { selectedVariant?.let(onFullscreenMap) },
                    interactive = false,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )
            }
            item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large) {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.DirectionsBus, null) },
                        headlineContent = { Text("Przebieg wariantu z rozkładu") },
                        supportingContent = {
                            Text(
                                if (routeStops.isEmpty()) "Baza nie zawiera kolejności przystanków dla tego wariantu."
                                else "Połączono ${routeStops.size} rzeczywistych punktów przystanków w kolejności z KIERUNKI.trasa. Baza nie zawiera geometrii ulic.",
                            )
                        },
                    )
                }
            }
            item {
                VehicleSection(vehicleState, selectedVariant?.directionCode.orEmpty(), ::requestVehicles)
            }
            item {
                Text("Przystanki wariantu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (routeStops.isEmpty()) {
                item { Text("Brak przebiegu trasy w pobranej bazie.") }
            } else {
                items(routeStops, key = { it.id }) { routeStop ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        ListItem(
                            headlineContent = { Text(routeStop.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(routeStop.publicNumber?.let { "nr $it" }, "ID ${routeStop.id}").joinToString(" · "),
                                )
                            },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun VehicleSection(state: VehicleState, directionCode: String, onRequest: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pozycje pojazdów", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (state) {
                VehicleState.Idle -> Text("Pozycje nie są pobierane automatycznie. Sprawdź je tylko wtedy, gdy są potrzebne.")
                VehicleState.Loading -> Text("Pobieranie pozycji z serwera MyBus…")
                is VehicleState.Loaded -> Text(
                    if (state.vehicles.isEmpty()) "Serwer nie udostępnił teraz pozycji pojazdu dla tego wariantu."
                    else "Serwer udostępnił ${state.vehicles.size} rzeczywistych pozycji; pokazano je na mapie.",
                )
                is VehicleState.Unavailable -> Text("Pozycja pojazdu niedostępna: ${state.reason}")
            }
            OutlinedButton(
                onClick = onRequest,
                enabled = state !is VehicleState.Loading && directionCode.isNotBlank(),
            ) {
                Icon(Icons.Default.MyLocation, null)
                Text(" Sprawdź pozycje na żywo")
            }
            if (directionCode.isBlank()) Text("Brak kodu kierunku - zapytanie nie zostanie wysłane.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun TimetableData.variantKey(): String = "$variant|$directionCode|$direction"

private sealed interface VehicleState {
    data object Idle : VehicleState
    data object Loading : VehicleState
    data class Loaded(val vehicles: List<LiveVehicle>) : VehicleState
    data class Unavailable(val reason: String) : VehicleState
}
