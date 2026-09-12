@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import pl.walbrzych.autobus.R
import pl.walbrzych.autobus.data.LiveVehicle
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.TransitRepository
import pl.walbrzych.autobus.data.destinationLabel
import pl.walbrzych.autobus.data.selectVehicleForDeparture
import pl.walbrzych.autobus.data.vehicleRouteProgress
import java.time.LocalTime

data class VehicleMapTarget(
    val sideNumber: Int,
    val departureId: Int,
    val departureStopId: String,
    val line: String,
)

private sealed interface VehiclePositionState {
    data object Loading : VehiclePositionState
    data class Available(val content: VehicleMapContent) : VehiclePositionState
    data class Unavailable(val message: String) : VehiclePositionState
}

private data class VehicleMapContent(
    val vehicle: LiveVehicle,
    val directionLabel: String,
    val nextStopName: String?,
    val nextStopId: String?,
    val etaLabel: String?,
    val routeStops: List<pl.walbrzych.autobus.data.StopData>,
)

/** Displays one server-confirmed vehicle and keeps its marker fresh only while resumed. */
@Composable
fun VehicleMapScreen(
    target: VehicleMapTarget,
    repository: TransitRepository,
    snapshot: ScheduleSnapshot,
    onBack: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refreshRequest by remember { mutableIntStateOf(0) }
    var state by remember(target) { mutableStateOf<VehiclePositionState>(VehiclePositionState.Loading) }
    var refreshing by remember(target) { mutableStateOf(true) }

    LaunchedEffect(target, repository, snapshot, refreshRequest, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refreshing = true
                state = repository.vehiclesBySideNumber(target.sideNumber).fold(
                    onSuccess = { vehicles -> vehicleMapContent(target, snapshot, repository, vehicles) },
                    onFailure = { error ->
                        VehiclePositionState.Unavailable(
                            error.message ?: "Nie udało się pobrać pozycji pojazdu.",
                        )
                    },
                )
                refreshing = false
                delay(VEHICLE_REFRESH_MILLIS)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Pojazd linii ${target.line}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshRequest++ },
                        enabled = !refreshing,
                    ) {
                        Text(
                            text = nerdReloadGlyph,
                            fontFamily = vehicleMapNerdFont,
                            fontSize = 24.sp,
                            color = if (refreshing) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            when (val positionState = state) {
                VehiclePositionState.Loading -> VehicleLoadingState()
                is VehiclePositionState.Available -> VehicleLocationMap(
                    vehicle = positionState.content.vehicle,
                    line = target.line.ifBlank { positionState.content.vehicle.line },
                    directionLabel = positionState.content.directionLabel,
                    nextStopName = positionState.content.nextStopName,
                    nextStopId = positionState.content.nextStopId,
                    etaLabel = positionState.content.etaLabel,
                    routeStops = positionState.content.routeStops,
                    modifier = Modifier.fillMaxSize(),
                )
                is VehiclePositionState.Unavailable -> VehicleUnavailableState(
                    message = positionState.message,
                    onRetry = { refreshRequest++ },
                )
            }
            if (refreshing && state is VehiclePositionState.Available) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

private suspend fun vehicleMapContent(
    target: VehicleMapTarget,
    snapshot: ScheduleSnapshot,
    repository: TransitRepository,
    vehicles: List<LiveVehicle>,
): VehiclePositionState {
    val vehicle = selectVehicleForDeparture(
        vehicles = vehicles,
        sideNumber = target.sideNumber,
        departureId = target.departureId,
    ) ?: return VehiclePositionState.Unavailable("Pozycja pojazdu jest obecnie niedostępna.")
    val progress = vehicleRouteProgress(snapshot, target.departureStopId, target.line, vehicle)
    val nextStop = snapshot.stops.firstOrNull { it.id == target.departureStopId }
    val etaLabel = nextStop?.let { stop ->
        repository.realTimeDepartures(stop.id).getOrNull()?.let { realtime ->
            realtime.departures
                .firstOrNull { departure ->
                    departure.departureId == target.departureId && departure.n == target.sideNumber
                }
                ?.mapEtaLabel(realtime.serverTime)
        }
    }
    val directionLabel = progress?.directionLabel
        ?: vehicle.destinationLabel().takeIf(String::isNotBlank)
        ?: vehicle.variant.takeIf(String::isNotBlank)
        ?: vehicle.directionCode
    return VehiclePositionState.Available(
        VehicleMapContent(
            vehicle = vehicle,
            directionLabel = directionLabel,
            nextStopName = nextStop?.name,
            nextStopId = nextStop?.id,
            etaLabel = etaLabel ?: "—",
            routeStops = progress?.routeStops.orEmpty(),
        ),
    )
}

private fun pl.walbrzych.autobus.data.RealTimeDeparture.mapEtaLabel(serverTime: String): String? {
    val serverLocalTime = runCatching { LocalTime.parse(serverTime.trim()) }.getOrNull() ?: return null
    val departureLabel = stopDetailDepartureLabel(serverLocalTime)
    return if (departureLabel.startsWith("Odjazd za ")) {
        departureLabel.removePrefix("Odjazd za ").removeSuffix(" min") + " m"
    } else {
        departureLabel.removePrefix("Odjazd: ")
    }
}

@Composable
private fun VehicleLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "Pobieranie pozycji pojazdu…",
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VehicleUnavailableState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Ponów")
        }
    }
}

private const val VEHICLE_REFRESH_MILLIS = 15_000L
private val vehicleMapNerdFont = FontFamily(Font(R.font.commit_mono_nerd_font_propo_regular))
private val nerdReloadGlyph = String(Character.toChars(0xF0453)) // nf-md-reload
