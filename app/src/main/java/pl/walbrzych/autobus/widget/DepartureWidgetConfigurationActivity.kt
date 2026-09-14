@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.walbrzych.autobus.data.CitySelectionStore
import pl.walbrzych.autobus.data.cachedScheduleForCity
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.UserInterfacePreferences
import pl.walbrzych.autobus.data.filterStopsForDisplay
import pl.walbrzych.autobus.ui.FullscreenStopMap
import pl.walbrzych.autobus.ui.OfflineMap
import pl.walbrzych.autobus.ui.theme.AutoBusTheme

open class DepartureWidgetConfigurationActivity : ComponentActivity() {
    /** The original 2×2 widget follows exactly one line. */
    protected open val allowsMultipleLines: Boolean = false
    protected open val singleLineSelectionDescription: String =
        "Wybierz linię, której najbliższy odjazd ma być widoczny na kompaktowym widgecie."

    protected open fun refreshWidgets(appWidgetIds: IntArray) {
        DepartureWidgetProvider.requestRefresh(this, appWidgetIds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED)
        enableEdgeToEdge()
        setContent {
            AutoBusTheme {
                DepartureWidgetConfigurationScreen(
                    appWidgetId = appWidgetId,
                    initialStopId = intent.getStringExtra(DepartureWidgetPinning.EXTRA_STOP_ID),
                    initialLines = intent.getStringArrayExtra(DepartureWidgetPinning.EXTRA_LINES)
                        ?.filter(String::isNotBlank)
                        ?.toSet()
                        ?: intent.getStringExtra(DepartureWidgetPinning.EXTRA_LINE)
                            ?.takeIf(String::isNotBlank)
                            ?.let(::setOf)
                        ?: emptySet(),
                    allowsMultipleLines = allowsMultipleLines,
                    singleLineSelectionDescription = singleLineSelectionDescription,
                    onComplete = { configuration ->
                        DepartureWidgetConfigurationStore(this).save(appWidgetId, configuration)
                        refreshWidgets(intArrayOf(appWidgetId))
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}

/** Configuration entry point for the added wide 4×2 departures widget. */
class DeparturesWidgetConfigurationActivity : DepartureWidgetConfigurationActivity() {
    override val allowsMultipleLines: Boolean = true

    override fun refreshWidgets(appWidgetIds: IntArray) {
        DeparturesWidgetProvider.requestRefresh(this, appWidgetIds)
    }
}

/** Configuration entry point for the 4×1 single-line course series widget. */
class LineDeparturesWidgetConfigurationActivity : DepartureWidgetConfigurationActivity() {
    override val singleLineSelectionDescription: String =
        "Wybierz linię. Widget pokaże trzy najbliższe kursy na tym przystanku."

    override fun refreshWidgets(appWidgetIds: IntArray) {
        LineDeparturesWidgetProvider.requestRefresh(this, appWidgetIds)
    }
}

@Composable
private fun DepartureWidgetConfigurationScreen(
    appWidgetId: Int,
    initialStopId: String?,
    initialLines: Set<String>,
    allowsMultipleLines: Boolean,
    singleLineSelectionDescription: String,
    onComplete: (DepartureWidgetConfiguration) -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val city = remember(context) { CitySelectionStore(context).selectedCity() }
    val preferences = remember(context) { UserInterfacePreferences(context) }
    var snapshot by remember(city?.id) { mutableStateOf<ScheduleSnapshot?>(null) }
    var loading by remember(city?.id) { mutableStateOf(city != null) }
    var selectedStop by remember { mutableStateOf<StopData?>(null) }
    var stopMapOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(city?.id) {
        snapshot = city?.let { selectedCity ->
            withContext(Dispatchers.IO) { cachedScheduleForCity(context, selectedCity) }
        }
        loading = false
    }
    LaunchedEffect(snapshot, initialStopId) {
        if (selectedStop == null && initialStopId != null) {
            selectedStop = snapshot?.stops?.firstOrNull { it.id == initialStopId }
        }
    }
    BackHandler(enabled = stopMapOpen) { stopMapOpen = false }
    BackHandler(enabled = selectedStop != null && !stopMapOpen) { selectedStop = null }
    val selectableStops = remember(snapshot, preferences.showStopsWithoutLines()) {
        snapshot?.let { filterStopsForDisplay(it.stops, preferences.showStopsWithoutLines()) }.orEmpty()
    }

    if (stopMapOpen) {
        FullscreenStopMap(
            stops = selectableStops,
            userLocation = null,
            onBack = { stopMapOpen = false },
            onStopClick = { stop ->
                stopMapOpen = false
                selectedStop = stop
            },
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dodaj widget", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedStop == null) onCancel() else selectedStop = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            city == null -> WidgetConfigurationMessage(
                modifier = Modifier.padding(padding),
                title = "Najpierw wybierz miasto",
                body = "Otwórz autoBus i wybierz miasto, dla którego chcesz dodać widget.",
            )
            loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            snapshot == null -> WidgetConfigurationMessage(
                modifier = Modifier.padding(padding),
                title = "Brak zapisanego rozkładu",
                body = "Otwórz autoBus i pozwól pobrać rozkład dla ${city.name}.",
            )
            selectedStop == null -> StopPicker(
                stops = selectableStops,
                modifier = Modifier.padding(padding),
                onStopSelected = { selectedStop = it },
                onOpenMap = { stopMapOpen = true },
            )
            else -> LinePicker(
                cityId = city.id,
                stop = selectedStop!!,
                initialLines = initialLines,
                allowsMultipleLines = allowsMultipleLines,
                singleLineSelectionDescription = singleLineSelectionDescription,
                modifier = Modifier.padding(padding),
                onComplete = onComplete,
            )
        }
    }
}

@Composable
private fun StopPicker(
    stops: List<StopData>,
    modifier: Modifier,
    onStopSelected: (StopData) -> Unit,
    onOpenMap: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matchingStops = remember(stops, query) {
        stops.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("1. Wybierz przystanek", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Text(
                "Wskaż go na mapie albo znajdź na liście.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OfflineMap(
                stops = stops,
                onStopClick = onStopSelected,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                onMapClick = onOpenMap,
                interactive = false,
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Szukaj przystanku") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                supportingText = { Text("${matchingStops.size} przystanków") },
            )
        }
        items(matchingStops, key = { it.id }) { stop ->
            Surface(
                onClick = { onStopSelected(stop) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ListItem(
                    headlineContent = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(stop.timetables.map { it.line }.distinct().sorted().joinToString(" · "))
                    },
                )
            }
        }
    }
}

@Composable
private fun LinePicker(
    cityId: Int,
    stop: StopData,
    initialLines: Set<String>,
    allowsMultipleLines: Boolean,
    singleLineSelectionDescription: String,
    modifier: Modifier,
    onComplete: (DepartureWidgetConfiguration) -> Unit,
) {
    val lines = remember(stop) { stop.timetables.map { it.line }.distinct().sorted() }
    var selectedLineList by rememberSaveable(stop.id) {
        mutableStateOf(
            if (allowsMultipleLines) initialLines.filter { it in lines }
            else initialLines.firstOrNull { it in lines }?.let(::listOf).orEmpty(),
        )
    }
    val selectedLines = selectedLineList.toSet()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(
            if (allowsMultipleLines) "2. Wybierz linie" else "2. Wybierz linię",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(stop.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (allowsMultipleLines) {
                "Zaznacz linie do śledzenia. Pominięcie pokaże cztery najbliższe odjazdy ze wszystkich linii."
            } else {
                singleLineSelectionDescription
            },
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(lines, key = { it }) { line ->
                val selected = line in selectedLines
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedLineList = if (allowsMultipleLines) {
                            if (selected) selectedLineList - line else selectedLineList + line
                        } else {
                            listOf(line)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stop.timetables.filter { it.line == line }.map { it.direction }.distinct().joinToString(" · "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
            }
        }
        if (allowsMultipleLines && selectedLines.isEmpty()) {
            Button(
                onClick = { onComplete(DepartureWidgetConfiguration(cityId, stop.id)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text("Pomiń — wszystkie linie")
            }
        } else {
            Button(
                onClick = { onComplete(DepartureWidgetConfiguration(cityId, stop.id, selectedLines)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = selectedLines.isNotEmpty(),
            ) {
                Text(if (selectedLines.isEmpty()) "Wybierz linię" else "Dodaj widget")
            }
        }
    }
}

@Composable
private fun WidgetConfigurationMessage(modifier: Modifier, title: String, body: String) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
