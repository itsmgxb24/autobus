@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.walbrzych.autobus.data.PlannedJourney
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.TransitTime
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.TransitPlanner

/** A local, calendar-aware connection planner for the downloaded timetable. */
@Composable
fun PlannerScreen(stops: List<StopData>, snapshot: ScheduleSnapshot) {
    val stopsById = remember(stops) { stops.associateBy(StopData::id) }
    var fromId by rememberSaveable { mutableStateOf<String?>(null) }
    var toId by rememberSaveable { mutableStateOf<String?>(null) }
    var stopoverId by rememberSaveable { mutableStateOf<String?>(null) }
    var stopoverVisible by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var journeys by remember { mutableStateOf<List<PlannedJourney>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val from = fromId?.let(stopsById::get)
    val to = toId?.let(stopsById::get)
    val stopover = stopoverId?.let(stopsById::get)
    val canSearch = from != null && to != null && (!stopoverVisible || stopover != null) && !searching

    Column {
        TopAppBar(
            title = { Text("Planer", fontWeight = FontWeight.SemiBold) },
            actions = { Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.padding(end = 16.dp)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Znajdź połączenie",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(
                    "Wyszukiwanie korzysta z pobranego rozkładu i rozpoczyna się od bieżącej godziny.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                PlannerStopField(
                    fieldKey = "planner_from",
                    label = "Przystanek początkowy",
                    stops = stops,
                    selectedStop = from,
                    onSelected = {
                        fromId = it?.id
                        hasSearched = false
                    },
                )
            }
            if (stopoverVisible) {
                item {
                    PlannerStopField(
                        fieldKey = "planner_stopover",
                        label = "Postój",
                        stops = stops,
                        selectedStop = stopover,
                        onSelected = {
                            stopoverId = it?.id
                            hasSearched = false
                        },
                    )
                }
            }
            item {
                PlannerStopField(
                    fieldKey = "planner_to",
                    label = "Przystanek docelowy",
                    stops = stops,
                    selectedStop = to,
                    onSelected = {
                        toId = it?.id
                        hasSearched = false
                    },
                )
            }
            item {
                if (stopoverVisible) {
                    OutlinedButton(
                        onClick = {
                            stopoverVisible = false
                            stopoverId = null
                            hasSearched = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Usuń postój") }
                } else {
                    OutlinedButton(
                        onClick = { stopoverVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Dodaj postój", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                Button(
                    enabled = canSearch,
                    onClick = {
                        val selectedFrom = from ?: return@Button
                        val selectedTo = to ?: return@Button
                        val selectedStopover = if (stopoverVisible) stopover else null
                        searching = true
                        hasSearched = true
                        scope.launch {
                            journeys = withContext(Dispatchers.Default) {
                                TransitPlanner.findJourneys(
                                    snapshot = snapshot,
                                    fromStopId = selectedFrom.id,
                                    toStopId = selectedTo.id,
                                    stopoverId = selectedStopover?.id,
                                    departureAt = TransitTime.now(),
                                )
                            }
                            searching = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Szukaj połączenia", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (hasSearched && !searching) {
                if (journeys.isEmpty()) {
                    item {
                        PlannerNotice(
                            "Nie znaleziono połączenia w pobranym rozkładzie na najbliższe 7 dni. " +
                                "Spróbuj dodać postój lub zmienić przystanek.",
                        )
                    }
                } else {
                    item {
                        Text("Najbliższe połączenia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(journeys, key = { journey -> journey.legs.joinToString { "${it.line}:${it.departureAt}" } }) { journey ->
                        PlannedJourneyCard(journey)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerStopField(
    fieldKey: String,
    label: String,
    stops: List<StopData>,
    selectedStop: StopData?,
    onSelected: (StopData?) -> Unit,
) {
    var query by rememberSaveable(fieldKey) { mutableStateOf(selectedStop?.name.orEmpty()) }
    var editing by rememberSaveable(fieldKey) { mutableStateOf(false) }
    LaunchedEffect(selectedStop?.id) {
        if (!editing) query = selectedStop?.name.orEmpty()
    }
    val suggestions = remember(query, stops) {
        if (query.isBlank()) emptyList()
        else stops.filter { it.name.contains(query, ignoreCase = true) }.take(6)
    }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                editing = true
                if (selectedStop?.name != value) onSelected(null)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = {
                        query = ""
                        editing = false
                        onSelected(null)
                    }) { Icon(Icons.Default.Close, contentDescription = "Wyczyść") }
                }
            } else {
                null
            },
        )
        if (editing && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.heightIn(max = 280.dp)) {
                    suggestions.forEachIndexed { index, stop ->
                        Surface(
                            onClick = {
                                query = stop.name
                                editing = false
                                onSelected(stop)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                        )
                        {
                            ListItem(
                                headlineContent = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(stop.publicNumber?.let { "nr $it" }, stop.timetables.map { it.line }.distinct().take(4).joinToString(" · ").takeIf(String::isNotBlank))
                                            .joinToString(" · "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                        if (index != suggestions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerNotice(message: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlannedJourneyCard(journey: PlannedJourney) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Odjazd ${journey.departureAt.format(PLAN_TIME_FORMAT)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Przyjazd ${journey.arrivalAt.format(PLAN_TIME_FORMAT)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Godziny rozkładowe",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            journey.legs.forEachIndexed { index, leg ->
                if (journey.stageBreakAfter == index) {
                    HorizontalDivider()
                    Text(
                        "Postój: ${leg.from.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Linia ${leg.line} · ${leg.direction}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${leg.from.name}  ${leg.departureAt.format(PLAN_TIME_FORMAT)} → ${leg.to.name}  ${leg.arrivalAt.format(PLAN_TIME_FORMAT)}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        leg.stopCount?.let { count ->
                            Text(
                                "$count ${if (count == 1) "przystanek po drodze" else "przystanki po drodze"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PLAN_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
