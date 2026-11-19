@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.ruby.lubiechowlabs.autobus.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.ruby.lubiechowlabs.autobus.busdevice.BusDeviceBle
import pl.ruby.lubiechowlabs.autobus.busdevice.BusDevicePeer
import pl.ruby.lubiechowlabs.autobus.busdevice.BusDeviceSettings
import pl.ruby.lubiechowlabs.autobus.busdevice.BusDeviceTheme
import pl.ruby.lubiechowlabs.autobus.busdevice.busDevicePermissions
import pl.ruby.lubiechowlabs.autobus.data.CityCatalog
import pl.ruby.lubiechowlabs.autobus.data.CityConfig
import pl.ruby.lubiechowlabs.autobus.data.CityDataSource
import pl.ruby.lubiechowlabs.autobus.data.StopData
import pl.ruby.lubiechowlabs.autobus.data.TransitRepository

private enum class BusPage { Pair, Stop, City, Theme, Interval, Settings }

@Composable
internal fun BusDeviceScreen(activeCity: CityConfig, useHttps: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val appScope = rememberCoroutineScope()
    val stored = remember(context) { context.getSharedPreferences("bus_device", Context.MODE_PRIVATE) }
    val ble = remember(context) { BusDeviceBle(context) }
    DisposableEffect(ble) { onDispose { ble.close() } }
    val online by ble.online.collectAsState()
    val cities = remember { CityCatalog.cities.filter { it.dataSource == CityDataSource.MYBUS } }
    val initialCity = activeCity.takeIf { it in cities } ?: cities.first { it.id == 10 }
    var savedPeer by remember {
        mutableStateOf(stored.getString("address", null)?.let { BusDevicePeer(it, stored.getString("name", "BusDevice") ?: "BusDevice") })
    }
    var peer by remember { mutableStateOf(savedPeer) }
    var confirmed by remember { mutableStateOf<BusDeviceSettings?>(null) }
    var wizard by remember { mutableStateOf(savedPeer == null) }
    var page by remember { mutableStateOf(if (wizard) BusPage.Pair else BusPage.Settings) }
    var city by remember { mutableStateOf(initialCity) }
    var stopId by remember { mutableStateOf<Int?>(null) }
    var stopName by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(BusDeviceTheme.RED_WHITE) }
    var refreshing by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(emptyList<BusDevicePeer>()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val next = afterPermission
        afterPermission = null
        if (ble.hasPermissions()) next?.invoke()
        else error = "Zezwól na dostęp do urządzeń w pobliżu, aby połączyć się z BusDevice."
    }

    fun withPermissions(action: () -> Unit) {
        if (ble.hasPermissions()) action() else {
            afterPermission = action
            permissionLauncher.launch(busDevicePermissions())
        }
    }

    fun runOperation(action: suspend () -> Unit) {
        if (refreshing) return
        refreshing = true
        error = null
        appScope.launch {
            try {
                action()
            } catch (_: TimeoutCancellationException) {
                error = "BusDevice nie odpowiedział. Sprawdź Bluetooth i spróbuj ponownie."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Nie udało się połączyć z BusDevice."
            } finally {
                refreshing = false
            }
        }
    }

    fun scan() = withPermissions {
        if (!scanning && !refreshing) {
            scanning = true
            devices = emptyList()
            error = null
            scanJob = appScope.launch {
                try {
                    withTimeoutOrNull(12_000) {
                        ble.scan().collect { found ->
                            if (devices.none { it.address == found.address }) devices += found
                        }
                    }
                    if (devices.isEmpty()) error = "Nie znaleziono BusDevice. Sprawdź zasilanie i naciśnij BOOT na ESP32."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Wyszukiwanie urządzenia nie powiodło się."
                } finally {
                    scanning = false
                }
            }
        }
    }

    fun connectTo(target: BusDevicePeer, startSetup: Boolean) = withPermissions {
        scanJob?.cancel()
        runOperation {
            confirmed = ble.connect(target)
            peer = target
            if (startSetup) {
                city = initialCity
                stopId = null
                stopName = ""
                theme = BusDeviceTheme.RED_WHITE
                page = BusPage.Stop
            }
        }
    }

    fun save(settings: BusDeviceSettings) {
        val target = peer ?: return
        withPermissions {
            runOperation {
                confirmed = ble.save(target, settings)
                stored.edit {
                    putString("address", target.address)
                    putString("name", target.name)
                }
                savedPeer = target
                wizard = false
                page = BusPage.Settings
            }
        }
    }

    fun back() {
        when (page) {
            BusPage.Settings -> onBack()
            BusPage.Pair -> if (savedPeer == null) onBack() else {
                scanJob?.cancel()
                wizard = false
                peer = savedPeer
                page = BusPage.Settings
                savedPeer?.let { connectTo(it, startSetup = false) }
            }
            BusPage.City -> page = BusPage.Stop
            BusPage.Stop -> page = if (wizard) BusPage.Pair else BusPage.Settings
            BusPage.Theme -> page = if (wizard) BusPage.Stop else BusPage.Settings
            BusPage.Interval -> page = BusPage.Settings
        }
        error = null
    }

    BackHandler(enabled = page != BusPage.Settings, onBack = { if (!refreshing) back() })
    LaunchedEffect(ble) {
        val target = savedPeer
        if (target != null && ble.hasPermissions()) connectTo(target, startSetup = false)
    }

    if (page == BusPage.City) {
        CityPickerScreen(city, ::back, { chosen ->
            if (city.id != chosen.id) {
                city = chosen
                stopId = null
                stopName = ""
            }
            page = BusPage.Stop
        }, cities)
        return
    }

    Column(Modifier.fillMaxSize()) {
        val title = when (page) {
            BusPage.Pair -> "Skonfiguruj BusDevice"
            BusPage.Stop -> "Wybierz przystanek"
            BusPage.Theme -> "Motyw ekranu"
            BusPage.Interval -> "Częstotliwość odświeżania"
            else -> "BusDevice"
        }
        SettingsDetailTopBar(title, onBack = { if (!refreshing) back() })
        if (refreshing || scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { message ->
            Text(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        if (page == BusPage.Stop) {
            BusStopPicker(city, useHttps, stopId, stopName, wizard, refreshing, onChangeCity = { page = BusPage.City },
                onSelect = { stop -> stopId = stop.id.toIntOrNull(); stopName = stop.name },
                onConfirm = {
                    val selected = stopId ?: return@BusStopPicker
                    if (wizard) page = BusPage.Theme
                    else confirmed?.let { save(it.copy(cityId = city.id, stopId = selected, stopName = stopName)) }
                },
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                BusPage.Pair -> {
                    item {
                        WizardStep("1 z 3", "Połącz urządzenie", "Włącz BusDevice i krótko naciśnij BOOT na ESP32, aby umożliwić parowanie.")
                    }
                    item {
                        Button(onClick = ::scan, enabled = !scanning && !refreshing, modifier = Modifier.fillMaxWidth()) {
                            Text(if (scanning) "Szukam…" else "Szukaj urządzeń")
                        }
                    }
                    items(devices, key = { it.address }) { target ->
                        SettingsGroup {
                            SettingsNavigationRow(busIcon, target.name, "Połącz przez Bluetooth", onClick = {
                                if (!refreshing) connectTo(target, startSetup = true)
                            })
                        }
                    }
                }
                BusPage.Settings -> {
                    item { SettingsGroupTitle("Urządzenie") }
                    item {
                        SettingsGroup {
                            ListItem(
                                leadingContent = busIcon,
                                headlineContent = { Text(savedPeer?.name ?: "BusDevice") },
                                supportingContent = { Text(if (online && confirmed != null) "Połączono przez Bluetooth" else "Rozłączono") },
                                trailingContent = {
                                    if (!online || confirmed == null) TextButton(
                                        onClick = { savedPeer?.let { connectTo(it, startSetup = false) } }, enabled = !refreshing,
                                    ) { Text("Połącz") }
                                },
                            )
                        }
                    }
                    confirmed?.let { current ->
                        item { SettingsGroupTitle("Ekran") }
                        item {
                            SettingsGroup {
                                fun editStop(destination: BusPage) {
                                    if (!refreshing) {
                                        city = cities.firstOrNull { it.id == current.cityId } ?: initialCity
                                        stopId = current.stopId
                                        stopName = current.stopName
                                        page = destination
                                    }
                                }
                                SettingsNavigationRow({ Icon(Icons.Default.LocationCity, null) }, "Miasto",
                                    CityCatalog.byId(current.cityId)?.name ?: "Nieznane miasto") { editStop(BusPage.City) }
                                BusDivider()
                                SettingsNavigationRow({ Icon(Icons.Default.LocationOn, null) }, "Przystanek", current.stopName) {
                                    editStop(BusPage.Stop)
                                }
                                BusDivider()
                                SettingsNavigationRow({ Icon(Icons.Default.Palette, null) }, "Motyw", current.theme.title) {
                                    if (!refreshing) { theme = current.theme; page = BusPage.Theme }
                                }
                                BusDivider()
                                SettingsNavigationRow({ Icon(Icons.Default.Schedule, null) }, "Odświeżanie", "Co ${current.refreshMinutes} min") {
                                    if (!refreshing) page = BusPage.Interval
                                }
                            }
                        }
                    }
                    item {
                        TextButton(enabled = !refreshing, onClick = {
                            ble.close()
                            devices = emptyList()
                            wizard = true
                            page = BusPage.Pair
                        }) { Text("Sparuj inne urządzenie") }
                    }
                }
                BusPage.Theme -> {
                    if (wizard) item { WizardStep("3 z 3", "Wybierz motyw", "Motyw będzie widoczny na tablicy odjazdów.") }
                    val themes = if (wizard) BusDeviceTheme.entries.take(2) else BusDeviceTheme.entries
                    item {
                        SettingsGroup {
                            Column(Modifier.selectableGroup()) {
                                themes.forEach { option ->
                                    ListItem(
                                        modifier = Modifier.selectable(theme == option, enabled = !refreshing, role = Role.RadioButton) { theme = option },
                                        leadingContent = { ThemeSample(option) },
                                        headlineContent = { Text(option.title) },
                                        trailingContent = { RadioButton(selected = theme == option, onClick = null) },
                                    )
                                }
                            }
                        }
                    }
                    if (theme == BusDeviceTheme.MONO) item {
                        Text("Na niektórych panelach Nebular czerń przybiera odcień pomarańczowy.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        Button(modifier = Modifier.fillMaxWidth(), enabled = !refreshing, onClick = {
                            val current = confirmed ?: return@Button
                            val chosen = if (wizard) {
                                val id = stopId ?: return@Button
                                BusDeviceSettings(city.id, id, stopName, theme, current.refreshMinutes)
                            } else current.copy(theme = theme)
                            save(chosen)
                        }) { Text(if (wizard) "Zapisz i zakończ" else "Zapisz motyw") }
                    }
                }
                BusPage.Interval -> {
                    confirmed?.let { config ->
                        item {
                            SettingsGroup {
                                Column(Modifier.selectableGroup()) {
                                    (listOf(1, 2, 5, 10, 15, 30, 60) + config.refreshMinutes).distinct().sorted().forEach { minutes ->
                                        ListItem(
                                            modifier = Modifier.selectable(minutes == config.refreshMinutes, enabled = !refreshing, role = Role.RadioButton) {
                                                save(config.copy(refreshMinutes = minutes))
                                            },
                                            headlineContent = { Text("Co $minutes min") },
                                            trailingContent = { RadioButton(selected = minutes == config.refreshMinutes, onClick = null) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private val busIcon: @Composable () -> Unit = { Icon(Icons.Default.Bluetooth, contentDescription = null) }

@Composable
private fun BusDivider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun WizardStep(step: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Krok $step", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThemeSample(theme: BusDeviceTheme) {
    val sampleBackground = if (theme == BusDeviceTheme.YELLOW_ACCENT) theme.accent else theme.background
    Surface(color = Color(sampleBackground), shape = MaterialTheme.shapes.small, shadowElevation = 1.dp) {
        Box(Modifier.size(width = 48.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            Text("12", color = Color(theme.color), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BusStopPicker(
    city: CityConfig, useHttps: Boolean, selected: Int?, selectedName: String, wizard: Boolean, saving: Boolean,
    onChangeCity: () -> Unit, onSelect: (StopData) -> Unit, onConfirm: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var stops by remember(city.id) { mutableStateOf<List<StopData>?>(null) }
    var error by remember(city.id) { mutableStateOf<String?>(null) }
    var retry by remember(city.id) { mutableStateOf(0) }
    var search by rememberSaveable(city.id) { mutableStateOf("") }
    var map by rememberSaveable(city.id) { mutableStateOf(false) }
    LaunchedEffect(city.id, retry, useHttps) {
        error = null
        TransitRepository(context, city, useHttps).loadInitial().fold(onSuccess = { snapshot ->
            stops = snapshot.stops.filter { it.id.toIntOrNull()?.let { id -> id > 0 } == true && it.timetables.isNotEmpty() }
                .sortedWith(compareBy<StopData> { it.name.lowercase() }.thenBy { it.publicNumber })
        }, onFailure = { failure ->
            if (failure is CancellationException) throw failure
            error = failure.message ?: "Nie udało się pobrać przystanków."
        })
    }
    val visibleStops = remember(stops, search) {
        val query = search.trim()
        stops.orEmpty().filter { it.name.contains(query, ignoreCase = true) || it.publicNumber?.contains(query) == true }
    }
    val mapStops = remember(stops) {
        stops.orEmpty().filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && (it.latitude != 0.0 || it.longitude != 0.0) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wizard) Text("Krok 2 z 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingsGroup {
            SettingsNavigationRow({ Icon(Icons.Default.LocationCity, null) }, "Miasto", city.name, onChangeCity)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(0, 2), onClick = { map = false }, selected = !map) { Text("Wyszukaj") }
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(1, 2), onClick = { map = true }, selected = map) { Text("Mapa") }
        }
        if (!map) OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Nazwa lub numer przystanku") },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text("Spróbuj ponownie") }
                }
                stops == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                map && mapStops.isEmpty() -> Text("Brak przystanków z położeniem na mapie.")
                map -> OfflineMap(mapStops, onStopClick = onSelect, modifier = Modifier.fillMaxSize(), interactive = true)
                visibleStops.isEmpty() -> Text("Brak pasujących przystanków.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visibleStops, key = { it.id }) { stop ->
                        val checked = stop.id.toIntOrNull() == selected
                        ListItem(
                            modifier = Modifier.clickable { onSelect(stop) },
                            headlineContent = { Text(stop.name) },
                            supportingContent = { Text(stop.publicNumber?.let { "Nr $it" } ?: "ID ${stop.id}") },
                            trailingContent = { RadioButton(selected = checked, onClick = null) },
                        )
                    }
                }
            }
        }
        if (selected != null) Text(stopNameOrHint(selectedName), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onConfirm, enabled = selected != null && !saving, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(if (wizard) "Dalej" else "Zapisz przystanek")
        }
    }
}

private fun stopNameOrHint(name: String) = "Wybrano: $name"
