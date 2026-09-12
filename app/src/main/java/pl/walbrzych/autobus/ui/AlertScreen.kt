@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.ui.theme.AutoBusTheme

/**
 * Community reports need a trustworthy, dedicated source. This application has no
 * KanarAlert backend, so it exposes downloaded stops but never fabricates reports.
 */
@Composable
fun AlertScreen(
    stops: List<StopData>,
    onStopClick: (StopData) -> Unit,
    onMapClick: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("KanarAlert", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.NotificationsOff, null) },
                        headlineContent = { Text("KanarAlert jest wyłączony") },
                        supportingContent = {
                            Text("Aplikacja nie ma (jeszcze) serwera zgłoszeń społecznościowych. Nie pokazuje więc wymyślonych alertów ani pozycji autobusów.")
                        },
                    )
                }
            }
            item {
                OfflineMap(
                    stops = stops,
                    onStopClick = onStopClick,
                    onMapClick = onMapClick,
                    interactive = false,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            }
            item {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Report, null)
                    Text("Zgłoś kanara")
                }
            }
            item {
                Text("Pobrane przystanki", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(stops.sortedBy { it.name }, key = { it.id }) { stop ->
                Surface(
                    onClick = { onStopClick(stop) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("Rozkłady i kierunki z zapisanej bazy MyBus") },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AlertPreview() {
    AutoBusTheme { AlertScreen(listOf(previewStop, previewStop.copy(id = "two", latitude = 50.771, longitude = 16.29)), {}) }
}
