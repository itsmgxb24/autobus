@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.walbrzych.autobus.data.StopData

/** The complete line index comes from ODJAZDY/KIERUNKI in the downloaded schedule. */
@Composable
fun LinesScreen(stops: List<StopData>, onBack: () -> Unit, onLineClick: (String, StopData) -> Unit) {
    val lines = remember(stops) {
        stops.flatMap { stop -> stop.timetables.map { table -> table.line to stop } }
            .groupBy({ it.first }, { it.second })
            .map { (line, coveredStops) ->
                val allTables = coveredStops.flatMap { stop -> stop.timetables.filter { it.line == line } }
                LineIndex(
                    line,
                    coveredStops.distinctBy { it.id },
                    allTables.map { "${it.variant}|${it.directionCode}|${it.direction}" }.distinct().size,
                )
            }
            .sortedWith(compareBy<LineIndex> { it.line.toIntOrNull() == null }.thenBy { it.line.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.line })
    }
    var query by rememberSaveable { mutableStateOf("") }
    val matchingLines = remember(lines, query) {
        lines.filter { entry ->
            query.isBlank() || entry.line.contains(query, ignoreCase = true) ||
                entry.stops.any { stop ->
                    stop.timetables.any { table -> table.line == entry.line && table.direction.contains(query, ignoreCase = true) }
                }
        }
    }
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Wszystkie linie") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    if (query.isBlank()) "${lines.size} linii z pobranej bazy" else "${matchingLines.size} wyników",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Szukaj numeru lub kierunku") },
                )
            }
            if (matchingLines.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text("Nie znaleziono linii ani kierunku.", modifier = Modifier.padding(20.dp))
                    }
                }
            }
            items(matchingLines, key = { it.line }) { entry ->
                Surface(
                    onClick = { onLineClick(entry.line, entry.stops.first()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    LineRow(entry)
                }
            }
        }
    }
}

/**
 * `ListItem` cannot safely host a long route description in its trailing slot: that slot
 * is measured first and may consume the text column's width. This row reserves predictable
 * room for the icon and line number, leaving every remaining pixel to the route description.
 */
@Composable
private fun LineRow(entry: LineIndex) {
    val direction = entry.stops.first().timetables.firstOrNull { it.line == entry.line }?.direction.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsBus,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = entry.line,
            modifier = Modifier.widthIn(min = 40.dp, max = 64.dp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = direction,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${entry.variantCount} wariantów · ${entry.stops.size} przystanków z odjazdami",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class LineIndex(val line: String, val stops: List<StopData>, val variantCount: Int)
