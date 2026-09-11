@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.walbrzych.autobus.data.DayType
import pl.walbrzych.autobus.data.RealTimeDepartures
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.TimetableData
import pl.walbrzych.autobus.data.TransitRepository
import pl.walbrzych.autobus.data.localTimes
import pl.walbrzych.autobus.data.nextScheduledDepartures
import pl.walbrzych.autobus.ui.theme.AutoBusTheme
import pl.walbrzych.autobus.widget.DepartureWidgetConfiguration
import pl.walbrzych.autobus.widget.DepartureWidgetPinning
import pl.walbrzych.autobus.widget.DeparturesWidgetPinning
import pl.walbrzych.autobus.widget.LineDeparturesWidgetPinning
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

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
    repository: TransitRepository? = null,
    snapshot: ScheduleSnapshot? = null,
    isFavorite: Boolean = false,
    onFavoriteChange: (Boolean) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCompactWidgetLinePicker by rememberSaveable(stop.id) { mutableStateOf(false) }
    var showDeparturesWidgetLinePicker by rememberSaveable(stop.id) { mutableStateOf(false) }
    var showLineSeriesWidgetPicker by rememberSaveable(stop.id) { mutableStateOf(false) }
    val lines = remember(stop) { stop.timetables.map { it.line }.distinct().sorted() }
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    AssistChip(
                        onClick = { onLineClick(line) },
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
            if (cityId != null && lines.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showCompactWidgetLinePicker = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Dodaj mały widget")
                }
                TextButton(
                    onClick = { showDeparturesWidgetLinePicker = true },
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text("Dodaj duży widget odjazdów")
                }
                TextButton(
                    onClick = { showLineSeriesWidgetPicker = true },
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text("Dodaj widget kolejnych kursów")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Odjazdy") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Rozkład jazdy") })
        }
        if (selectedTab == 0) DeparturesTab(stop, snapshot, repository, onLineClick)
        else TimetableTab(stop, snapshot, onLineClick)
    }
    if (showCompactWidgetLinePicker && cityId != null) {
        WidgetLinePickerDialog(
            stop = stop,
            lines = lines,
            allowsMultipleLines = false,
            title = "Linia dla małego widgetu",
            description = "${stop.name} · wybierz linię, której najbliższy odjazd ma być wyświetlany.",
            onDismiss = { showCompactWidgetLinePicker = false },
            onLinesSelected = { selectedLines ->
                val requested = DepartureWidgetPinning.request(
                    context,
                    DepartureWidgetConfiguration(cityId, stop.id, selectedLines),
                )
                Toast.makeText(
                    context,
                    if (requested) "Wybierz miejsce widgetu na ekranie głównym." else "Ten launcher nie obsługuje przypinania widgetów.",
                    Toast.LENGTH_LONG,
                ).show()
                showCompactWidgetLinePicker = false
            },
        )
    }
    if (showDeparturesWidgetLinePicker && cityId != null) {
        WidgetLinePickerDialog(
            stop = stop,
            lines = lines,
            allowsMultipleLines = true,
            title = "Linie dla dużego widgetu",
            description = "${stop.name} · zaznacz linie do śledzenia albo pomiń, aby wyświetlić cztery najbliższe odjazdy.",
            onDismiss = { showDeparturesWidgetLinePicker = false },
            onLinesSelected = { selectedLines ->
                val requested = DeparturesWidgetPinning.request(
                    context,
                    DepartureWidgetConfiguration(cityId, stop.id, selectedLines),
                )
                Toast.makeText(
                    context,
                    if (requested) "Wybierz miejsce widgetu na ekranie głównym." else "Ten launcher nie obsługuje przypinania widgetów.",
                    Toast.LENGTH_LONG,
                ).show()
                showDeparturesWidgetLinePicker = false
            },
        )
    }
    if (showLineSeriesWidgetPicker && cityId != null) {
        WidgetLinePickerDialog(
            stop = stop,
            lines = lines,
            allowsMultipleLines = false,
            title = "Linia dla kolejnych kursów",
            description = "${stop.name} · wybierz linię, aby wyświetlić trzy kolejne kursy.",
            onDismiss = { showLineSeriesWidgetPicker = false },
            onLinesSelected = { selectedLines ->
                val requested = LineDeparturesWidgetPinning.request(
                    context,
                    DepartureWidgetConfiguration(cityId, stop.id, selectedLines),
                )
                Toast.makeText(
                    context,
                    if (requested) "Wybierz miejsce widgetu na ekranie głównym." else "Ten launcher nie obsługuje przypinania widgetów.",
                    Toast.LENGTH_LONG,
                ).show()
                showLineSeriesWidgetPicker = false
            },
        )
    }
}

@Composable
private fun WidgetLinePickerDialog(
    stop: StopData,
    lines: List<String>,
    allowsMultipleLines: Boolean,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onLinesSelected: (Set<String>) -> Unit,
) {
    var selectedLineList by rememberSaveable(stop.id) { mutableStateOf(emptyList<String>()) }
    val selectedLines = selectedLineList.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
                items(lines, key = { it }) { line ->
                    FilterChip(
                        selected = line in selectedLines,
                        onClick = {
                            selectedLineList = if (allowsMultipleLines) {
                                if (line in selectedLines) selectedLineList - line else selectedLineList + line
                            } else {
                                listOf(line)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(line, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = allowsMultipleLines || selectedLines.isNotEmpty(),
                onClick = { onLinesSelected(selectedLines) },
            ) {
                Text(if (allowsMultipleLines && selectedLines.isEmpty()) "Pomiń" else "Dodaj widget")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun DeparturesTab(
    stop: StopData,
    snapshot: ScheduleSnapshot?,
    repository: TransitRepository?,
    onLineClick: (String) -> Unit,
) {
    val now by androidx.compose.runtime.produceState(LocalDateTime.now(), stop.id) {
        while (true) {
            value = LocalDateTime.now()
            delay(30_000)
        }
    }
    var refreshKey by rememberSaveable(stop.id) { mutableIntStateOf(0) }
    var realtime by remember(stop.id, repository) { mutableStateOf<RealtimeState>(RealtimeState.Loading) }
    LaunchedEffect(stop.id, repository, refreshKey) {
        realtime = if (repository == null) RealtimeState.Unavailable("Podgląd nie łączy się z serwerem.")
        else repository.realTimeDepartures(stop.id).fold(
            onSuccess = { RealtimeState.Available(it) },
            onFailure = { RealtimeState.Unavailable(it.message ?: "Brak połączenia z serwerem MyBus.") },
        )
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
                if (state.data.departures.isEmpty()) item { Text("Serwer nie zwrócił bieżących odjazdów.") }
                else items(state.data.departures, key = { it.departureId }) { departure ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = { onLineClick(departure.line) }, label = { Text(departure.line, fontWeight = FontWeight.Bold) })
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(departure.direction, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // HH:mm is a scheduled arrival supplied by the server, not a calculated ETA.
                                Text(departure.arrivalLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
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
        else items(departures) { departure -> DepartureCard(departure, onLineClick) }
    }
}

@Composable
private fun DepartureCard(departure: UpcomingDeparture, onLineClick: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
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
        }
    }
}

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
            pl.walbrzych.autobus.data.ServiceDay(code, stop.timetables.first { it.serviceDayCode == code }.serviceDayLabel, index)
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
