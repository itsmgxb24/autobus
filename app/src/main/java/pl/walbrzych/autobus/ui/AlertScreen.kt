@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.ruby.lubiechowlabs.autobus.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.ruby.lubiechowlabs.autobus.data.StopData
import pl.ruby.lubiechowlabs.autobus.data.UserInterfacePreferences
import pl.ruby.lubiechowlabs.autobus.ui.theme.AutoBusTheme

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
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { UserInterfacePreferences(context) }
    var introductionStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(preferences) {
        if (!preferences.hasSeenKanarAlertIntroduction()) introductionStep = 1
    }
    when (introductionStep) {
        1 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Co to jest KanarAlert?") },
            text = {
                Text("KanarAlert to funkcja społecznościowa, która pozwala użytkownikom zgłaszać i sprawdzać informacje o aktualnych kontrolach biletów w komunikacji miejskiej. Dzięki zgłoszeniom społeczności możesz szybko zobaczyć, gdzie ostatnio pojawili się kontrolerzy oraz kiedy dana kontrola została zauważona.")
            },
            confirmButton = { TextButton(onClick = { introductionStep = 2 }) { Text("OK") } },
        )
        2 -> AlertDialog(
            onDismissRequest = {
                preferences.setKanarAlertIntroductionSeen()
                introductionStep = 0
            },
            title = { Text("KanarAlert jest wyłączony") },
            text = {
                Text("KanarAlert jest obecnie wyłączony. Funkcja opiera się na zgłoszeniach społeczności, dlatego ma sens dopiero wtedy, gdy z aplikacji korzysta większa liczba osób.\nJeśli autoBus zyska więcej aktywnych użytkowników, uruchomimy KanarAlert.")
            },
            confirmButton = {
                TextButton(onClick = {
                    preferences.setKanarAlertIntroductionSeen()
                    introductionStep = 0
                }) { Text("OK") }
            },
        )
    }
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
                        supportingContent = { Text("Rozkłady i kierunki z zapisanej bazy") },
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
