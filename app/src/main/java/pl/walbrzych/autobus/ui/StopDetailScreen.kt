@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.ruby.lubiechowlabs.autobus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.ruby.lubiechowlabs.autobus.data.DayType
import pl.ruby.lubiechowlabs.autobus.data.RealTimeDepartures
import pl.ruby.lubiechowlabs.autobus.data.ScheduleSnapshot
import pl.ruby.lubiechowlabs.autobus.data.StopData
import pl.ruby.lubiechowlabs.autobus.data.TimetableData
import pl.ruby.lubiechowlabs.autobus.data.TransitRepository
import pl.ruby.lubiechowlabs.autobus.data.TransitTime
import pl.ruby.lubiechowlabs.autobus.data.localTimes
import pl.ruby.lubiechowlabs.autobus.data.nextScheduledDepartures
import pl.ruby.lubiechowlabs.autobus.live.DepartureLiveUpdate
import pl.ruby.lubiechowlabs.autobus.live.DepartureLiveUpdateManager
import pl.ruby.lubiechowlabs.autobus.ui.theme.AutoBusTheme
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import kotlin.math.abs

private data class UpcomingDeparture(
    val timetable: TimetableData,
    val dateTime: LocalDateTime,
    val nextDay: Boolean,
)

private sealed interface RealtimeState {
    data object Loading : RealtimeState
    data class Available(val data: RealTimeDepartures) : RealtimeState
    data class Unavailable(val message: String) : RealtimeState
}

@Composable
fun StopDetailScreen(
    stop: StopData,
    onBack: () -> Unit,
    onLineClick: (String) -> Unit,
    cityId: Int? = null,
    markTrackableDepartures: Boolean = false,
    markInvalidMidnightDepartures: Boolean = false,
    repository: TransitRepository? = null,
    snapshot: ScheduleSnapshot? = null,
    isFavorite: Boolean = false,
    onFavoriteChange: (Boolean) -> Unit = {},
    liveUpdateTarget: DepartureOpenTarget? = null,
    onVehicleMap: (pl.ruby.lubiechowlabs.autobus.data.RealTimeDeparture) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val lines = remember(stop) { stop.timetables.map { it.line }.distinct().sorted() }
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Przystanek", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
            actions = {
                IconButton(onClick = { onFavoriteChange(!isFavorite) }) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stop.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(stop.publicNumber?.let { "nr $it" }, "${"%.5f".format(stop.latitude)}, ${"%.5f".format(stop.longitude)}").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lines, key = { it }) { line ->
                    OutlinedButton(
                        onClick = { onLineClick(line) },
                        modifier = Modifier.widthIn(min = 56.dp).height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                line,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Odjazdy") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Rozkład jazdy") })
        }
        if (selectedTab == 0) {
            DeparturesTab(
                stop = stop,
                snapshot = snapshot,
                repository = repository,
                onLineClick = onLineClick,
                cityId = cityId,
                markTrackableDepartures = markTrackableDepartures,
                markInvalidMidnightDepartures = markInvalidMidnightDepartures,
                liveUpdateTarget = liveUpdateTarget,
                onVehicleMap = onVehicleMap,
            )
        }
        else TimetableTab(stop, snapshot, onLineClick)
    }
}

@Composable
private fun DeparturesTab(
    stop: StopData,
    snapshot: ScheduleSnapshot?,
    repository: TransitRepository?,
    onLineClick: (String) -> Unit,
    cityId: Int?,
    markTrackableDepartures: Boolean,
    markInvalidMidnightDepartures: Boolean,
    liveUpdateTarget: DepartureOpenTarget?,
    onVehicleMap: (pl.ruby.lubiechowlabs.autobus.data.RealTimeDeparture) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val now by androidx.compose.runtime.produceState(TransitTime.now(), stop.id) {
        while (true) {
            value = TransitTime.now()
            delay(1_000)
        }
    }
    var refreshKey by rememberSaveable(stop.id) { mutableIntStateOf(0) }
    var realtime by remember(stop.id, repository) { mutableStateOf<RealtimeState>(RealtimeState.Loading) }
    var pendingTracking by remember { mutableStateOf<DepartureLiveUpdate?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requested = pendingTracking
        pendingTracking = null
        if (granted && requested != null) {
            DepartureLiveUpdateManager.start(context, requested)
        } else if (requested != null) {
            Toast.makeText(context, "Aby śledzić odjazd, zezwól na powiadomienia.", Toast.LENGTH_LONG).show()
        }
    }
    val startTracking: (DepartureLiveUpdate) -> Unit = { update ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTracking = update
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DepartureLiveUpdateManager.start(context, update)
        }
    }
    LaunchedEffect(stop.id, repository, refreshKey) {
        if (repository == null) {
            realtime = RealtimeState.Unavailable("Podgląd nie łączy się z serwerem.")
            return@LaunchedEffect
        }
        while (true) {
            realtime = repository.realTimeDepartures(stop.id).fold(
                onSuccess = { RealtimeState.Available(it) },
                onFailure = { RealtimeState.Unavailable(it.message ?: "Brak połączenia z serwerem MyBus.") },
            )
            delay(20_000)
        }
    }
    val departures = remember(stop, snapshot, now.toLocalDate(), now.hour, now.minute) {
        nextDepartures(stop, snapshot, now)
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Odjazdy z serwera", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        when (val state = realtime) {
            RealtimeState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Sprawdzanie czasu rzeczywistego…")
                }
            }
            is RealtimeState.Available -> {
                state.data.notice?.let { notice -> item { Text(notice, style = MaterialTheme.typography.bodySmall) } }
                val visibleDepartures = state.data.departures.filter { departure ->
                    markInvalidMidnightDepartures || !departure.hasInvalidScheduledTime
                }
                if (visibleDepartures.isEmpty()) item { Text("Brak bieżących odjazdów.") }
                else items(visibleDepartures, key = { it.departureId }) { departure ->
                    val invalidScheduledTime = departure.hasInvalidScheduledTime
                    val update = cityId?.let {
                        realtimeTrackingUpdate(it, stop, departure, now)
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (invalidScheduledTime) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = { onLineClick(departure.line) }, label = { Text(departure.line, fontWeight = FontWeight.Bold) })
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(departure.direction, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val serverClock = state.data.serverTime.toServerTimeOrNull() ?: now.toLocalTime()
                                val label = if (invalidScheduledTime) "NIEPOPRAWNY!"
                                else departure.stopDetailDepartureLabel(serverClock)
                                RealtimeDepartureLabel(
                                    label = label,
                                    blink = !invalidScheduledTime && departure.shouldBlinkSubMinuteEta(now.toLocalTime()),
                                    color = if (invalidScheduledTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            update?.let {
                                val markTrackable = markTrackableDepartures && departure.n != 0
                                if (markTrackable) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        TrackDepartureButton(
                                            tracked = liveUpdateTarget.matches(it),
                                            compact = true,
                                            onClick = { startTracking(it) },
                                        )
                                        TrackableDepartureMarker(
                                            onClick = { onVehicleMap(departure) },
                                        )
                                    }
                                } else {
                                    TrackDepartureButton(
                                        tracked = liveUpdateTarget.matches(it),
                                        onClick = { startTracking(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is RealtimeState.Unavailable -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Czas rzeczywisty niedostępny")
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "Ponów") }
                    }
                }
            }
        }
        item { Text("Rozkład zapisany lokalnie", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if (departures.isEmpty()) item { Text("Brak dalszych kursów w zapisanym kalendarzu.") }
        else items(departures) { departure ->
            val update = cityId?.let { scheduledTrackingUpdate(it, stop, departure) }
            DepartureCard(
                departure = departure,
                onLineClick = onLineClick,
                onTrack = update?.let { { startTracking(it) } },
                tracked = update?.let { liveUpdateTarget.matches(it) } == true,
            )
        }
    }
}

@Composable
private fun RealtimeDepartureLabel(label: String, blink: Boolean, color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "sub-minute-departure")
    val blinkAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "sub-minute-departure-alpha",
    )
    Text(
        text = label,
        modifier = Modifier.alpha(if (blink) blinkAlpha else 1f),
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun DepartureCard(
    departure: UpcomingDeparture,
    onLineClick: (String) -> Unit,
    onTrack: (() -> Unit)? = null,
    tracked: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (tracked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { onLineClick(departure.timetable.line) }, label = { Text(departure.timetable.line, fontWeight = FontWeight.Bold) })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(departure.timetable.direction, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("Rozkład: ${departure.dateTime.toLocalTime()}")
                        if (departure.nextDay) append(" · następny dzień")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            onTrack?.let { TrackDepartureButton(tracked = tracked, onClick = it) }
        }
    }
}

@Composable
private fun TrackDepartureButton(tracked: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val description = if (tracked) "Śledzony odjazd" else "Śledź ten odjazd na żywo"
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(if (compact) 32.dp else 40.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = "\uEB47",
            fontFamily = trackableMarkerFont,
            fontSize = if (compact) 21.sp else 24.sp,
            color = if (tracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackableDepartureMarker(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = "Pokaż pozycję pojazdu na mapie" },
    ) {
        Text(
            text = "\uF05B",
            fontFamily = trackableMarkerFont,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val trackableMarkerFont = FontFamily(Font(pl.ruby.lubiechowlabs.autobus.R.font.commit_mono_nerd_font_propo_regular))

private fun realtimeTrackingUpdate(
    cityId: Int,
    stop: StopData,
    departure: pl.ruby.lubiechowlabs.autobus.data.RealTimeDeparture,
    now: LocalDateTime,
): DepartureLiveUpdate? {
    val scheduledInstant = departure.scheduledInstantOrNull(now) ?: return null
    return DepartureLiveUpdate(
        cityId = cityId,
        stopId = stop.id,
        stopName = stop.name,
        line = departure.line,
        direction = departure.direction,
        scheduledAtMillis = scheduledInstant.toEpochMilli(),
        scheduledSeconds = departure.scheduledSeconds,
        tripId = departure.tripId,
    )
}

private fun scheduledTrackingUpdate(
    cityId: Int,
    stop: StopData,
    departure: UpcomingDeparture,
): DepartureLiveUpdate = DepartureLiveUpdate(
    cityId = cityId,
    stopId = stop.id,
    stopName = stop.name,
    line = departure.timetable.line,
    direction = departure.timetable.direction,
    scheduledAtMillis = departure.dateTime.atZone(TransitTime.zone).toInstant().toEpochMilli(),
    scheduledSeconds = departure.dateTime.toLocalTime().toSecondOfDay(),
)

internal fun pl.ruby.lubiechowlabs.autobus.data.RealTimeDeparture.scheduledInstantOrNull(now: LocalDateTime): Instant? {
    val time = scheduledTimeOrNull() ?: return null
    // GetTimeTableReal has no date. Choose the occurrence closest to the server's
    // current day; blindly moving every earlier clock time to tomorrow incorrectly
    // turns delayed courses into tomorrow's trip.
    val scheduled = sequenceOf(-1L, 0L, 1L)
        .map { dayOffset -> LocalDateTime.of(now.toLocalDate().plusDays(dayOffset), time) }
        .minBy { candidate -> abs(Duration.between(now, candidate).seconds) }
    return scheduled.atZone(TransitTime.zone).toInstant()
}

private fun DepartureOpenTarget?.matches(update: DepartureLiveUpdate): Boolean =
    this != null && cityId == update.cityId && stopId == update.stopId && line == update.line &&
        scheduledAtMillis == update.scheduledAtMillis

private fun String.toServerTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(trim()) }.getOrNull()

@Composable
private fun TimetableTab(stop: StopData, snapshot: ScheduleSnapshot?, onLineClick: (String) -> Unit) {
    val lines = remember(stop) { stop.timetables.map { it.line }.distinct().sorted() }
    var selectedLine by rememberSaveable(stop.id) { mutableStateOf(lines.firstOrNull().orEmpty()) }
    val directions = remember(stop, selectedLine) {
        stop.timetables.filter { it.line == selectedLine }.map { it.direction }.distinct()
    }
    var selectedDirection by rememberSaveable(stop.id, selectedLine) { mutableStateOf(directions.firstOrNull().orEmpty()) }
    val variants = remember(stop, selectedLine, selectedDirection) {
        stop.timetables.filter { it.line == selectedLine && it.direction == selectedDirection }
            .distinctBy { it.timetableVariantKey() }
    }
    var selectedVariantKey by rememberSaveable(stop.id, selectedLine, selectedDirection) {
        mutableStateOf(variants.firstOrNull()?.timetableVariantKey().orEmpty())
    }
    val availableDays = remember(stop, selectedLine, selectedDirection, selectedVariantKey, snapshot) {
        val codes = stop.timetables.filter {
            it.line == selectedLine && it.direction == selectedDirection && it.timetableVariantKey() == selectedVariantKey
        }
            .map { it.serviceDayCode }.distinct()
        snapshot?.serviceDays?.filter { it.code in codes } ?: codes.mapIndexed { index, code ->
            pl.ruby.lubiechowlabs.autobus.data.ServiceDay(code, stop.timetables.first { it.serviceDayCode == code }.serviceDayLabel, index)
        }
    }
    var selectedDayCode by rememberSaveable(stop.id, selectedLine, selectedDirection, selectedVariantKey) {
        mutableStateOf(availableDays.firstOrNull()?.code.orEmpty())
    }
    val table = stop.timetables.firstOrNull {
        it.line == selectedLine && it.direction == selectedDirection &&
            it.timetableVariantKey() == selectedVariantKey && it.serviceDayCode == selectedDayCode
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Wybierz linię i kierunek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(lines, key = { it }) { line ->
                        FilterChip(
                            selected = line == selectedLine,
                            onClick = { selectedLine = line },
                            modifier = Modifier.widthIn(min = 48.dp),
                            label = {
                                Text(
                                    line,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    directions.forEach { direction ->
                        FilterChip(selected = direction == selectedDirection, onClick = { selectedDirection = direction }, label = { Text(direction, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            }
            if (variants.size > 1) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wariant", style = MaterialTheme.typography.labelLarge)
                    variants.forEach { variant ->
                        FilterChip(
                            selected = variant.timetableVariantKey() == selectedVariantKey,
                            onClick = { selectedVariantKey = variant.timetableVariantKey() },
                            label = {
                                Text(
                                    variant.variant.takeIf(String::isNotBlank)?.let { "Wariant $it" }
                                        ?: variant.directionCode.takeIf(String::isNotBlank)?.let { "Kierunek $it" }
                                        ?: "Wariant rozkładu",
                                )
                            },
                        )
                    }
                }
            }
            item {
                Button(onClick = { onLineClick(selectedLine) }, modifier = Modifier.fillMaxWidth()) { Text("Pokaż wariant linii") }
            }
            item { Text("Godziny odjazdów — pobrana baza", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            if (table == null) item { Text("Dla tego wyboru nie zapisano rozkładu.") }
            else table.times.groupBy { it.substringBefore(':') }.toSortedMap().forEach { (hour, times) ->
                item(hour) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                            Text(hour, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                            Text(times.joinToString("  ") { it.substringAfter(':') }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        TabRow(selectedTabIndex = availableDays.indexOfFirst { it.code == selectedDayCode }.coerceAtLeast(0)) {
            availableDays.forEach { day ->
                Tab(selected = day.code == selectedDayCode, onClick = { selectedDayCode = day.code }, text = { Text(day.label, maxLines = 1) })
            }
        }
    }
}

private fun nextDepartures(stop: StopData, snapshot: ScheduleSnapshot?, now: LocalDateTime): List<UpcomingDeparture> {
    snapshot?.let { downloadedSchedule ->
        return nextScheduledDepartures(downloadedSchedule, stop, from = now).map { departure ->
            UpcomingDeparture(
                timetable = departure.timetable,
                dateTime = departure.scheduledAt,
                nextDay = departure.scheduledAt.toLocalDate() != now.toLocalDate(),
            )
        }
    }

    val result = mutableListOf<UpcomingDeparture>()
    for (offset in 0..7) {
        val date = now.toLocalDate().plusDays(offset.toLong())
        // Only the preview / JSON fixture lacks a downloaded KALENDARZ.
        val tables = stop.timetables.filter { it.dayType == dayTypeFor(date) }
        tables.forEach { table ->
            table.localTimes().forEach { time ->
                val departure = LocalDateTime.of(date, time)
                if (offset > 0 || !departure.isBefore(now)) result += UpcomingDeparture(table, departure, offset > 0)
            }
        }
        if (result.size >= 10) break
    }
    return result.sortedBy { it.dateTime }.take(10)
}

private fun dayTypeFor(date: LocalDate): DayType = when {
    date.dayOfWeek == DayOfWeek.SUNDAY -> DayType.SUNDAY_HOLIDAY
    date.monthValue in 7..8 || date.dayOfWeek == DayOfWeek.SATURDAY -> DayType.VACATION
    else -> DayType.WORKING
}

private fun TimetableData.timetableVariantKey(): String = "$variant|$directionCode|$direction"

internal val previewStop = StopData(
    id = "preview",
    name = "Piłsudskiego — Plac Tuwima",
    latitude = 50.76697,
    longitude = 16.28661,
    timetables = DayType.entries.flatMap { day -> listOf(
        TimetableData("5", "RUSINOWA — Szczawno-Zdrój", day, listOf("08:05", "08:20", "08:35", "09:05")),
        TimetableData("11", "KOZICE — Poniatów", day, listOf("08:10", "08:40", "09:10")),
    ) },
)

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun StopDetailPreview() {
    AutoBusTheme { StopDetailScreen(previewStop, {}, {}) }
}
