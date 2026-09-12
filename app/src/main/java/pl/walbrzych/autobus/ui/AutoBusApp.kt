@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.SyncResult
import pl.walbrzych.autobus.data.CityCatalog
import pl.walbrzych.autobus.data.CityConfig
import pl.walbrzych.autobus.data.CitySelectionStore
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.TransitRepository
import pl.walbrzych.autobus.data.distanceTo
import pl.walbrzych.autobus.data.HomeScreenConfiguration
import pl.walbrzych.autobus.data.HomeScreenTile
import pl.walbrzych.autobus.data.UserInterfacePreferences
import pl.walbrzych.autobus.data.filterStopsForDisplay
import pl.walbrzych.autobus.widget.DepartureWidgetProvider
import pl.walbrzych.autobus.widget.DeparturesWidgetProvider
import pl.walbrzych.autobus.widget.FavoriteDeparturesWidgetProvider
import pl.walbrzych.autobus.widget.LineDeparturesWidgetProvider
import kotlin.math.abs

private object Routes {
    const val SCHEDULE = "schedule"
    const val ALERTS = "alerts"
    const val PLANNER = "planner"
    const val SETTINGS = "settings"
    const val SETTINGS_CITY = "settings/city"
    const val SETTINGS_SCHEDULE = "settings/schedule"
    const val SETTINGS_APPLICATION = "settings/application"
    const val SETTINGS_DEVELOPER = "settings/developer"
    const val HOME_EDITOR = "home_editor"
    const val MAP = "map"
    const val STOP = "stop/{stopId}"
    const val STOP_PREFIX = "stop/"
    const val VEHICLE = "vehicle/{sideNumber}/{departureId}/{stopId}/{line}"
    const val VEHICLE_PREFIX = "vehicle/"
    const val LINES = "lines"
    const val LINE = "line/{stopId}/{lineId}"
    const val LINE_PREFIX = "line/"
    const val LINE_MAP = "line_map/{stopId}/{lineId}/{variantKey}"
    const val LINE_MAP_PREFIX = "line_map/"
    const val TICKET = "ticket/{ticketId}"
    const val TICKET_PREFIX = "ticket/"
    const val TICKET_DEMO = "ticket_demo/{ticketId}"
    const val TICKET_DEMO_PREFIX = "ticket_demo/"
}

// Used only when a user declines location access or no one-shot position is available.
private data class RootDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

private enum class CityFlowScreen { WELCOME, PICKER, APP }

private data class CityFlowState(val screen: CityFlowScreen, val cityId: Int?)

private fun SyncResult.snapshot(): ScheduleSnapshot = when (this) {
    is SyncResult.Current -> snapshot
    is SyncResult.Downloaded -> snapshot
}

private fun rootTabIndex(route: String?): Int? = when (route) {
    Routes.SCHEDULE -> 0
    Routes.ALERTS -> 1
    Routes.PLANNER -> 2
    Routes.SETTINGS -> 3
    else -> null
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootTabDirection(): Int? {
    val initialIndex = rootTabIndex(initialState.destination.route) ?: return null
    val targetIndex = rootTabIndex(targetState.destination.route) ?: return null
    return (targetIndex - initialIndex).takeIf { it != 0 }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootTabEnterTransition(): EnterTransition {
    val direction = rootTabDirection() ?: return EnterTransition.None
    return slideInHorizontally(
        animationSpec = tween(durationMillis = 250),
        initialOffsetX = { width -> if (direction > 0) width else -width },
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootTabExitTransition(): ExitTransition {
    val direction = rootTabDirection() ?: return ExitTransition.None
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 250),
        targetOffsetX = { width -> if (direction > 0) -width else width },
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailEnterFromRight(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(durationMillis = 250),
        initialOffsetX = { width -> width },
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopExitToRight(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(durationMillis = 250),
        targetOffsetX = { width -> width },
    )

/**
 * A destination must paint its own opaque layer while Navigation renders both sides of
 * a predictive-back gesture. Putting this at the nav-graph boundary makes the contract
 * apply equally to normal, missing and error destinations.
 */
@Composable
private fun NavigationDestinationSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun AutoBusApp() {
    val context = LocalContext.current.applicationContext
    val citySelection = remember(context) { CitySelectionStore(context) }
    var selectedCity by remember { mutableStateOf(citySelection.selectedCity()) }
    var flowScreen by rememberSaveable {
        mutableStateOf(if (selectedCity == null) CityFlowScreen.WELCOME else CityFlowScreen.APP)
    }
    val liveUpdateTarget by DepartureLiveUpdateNavigation.target.collectAsState()
    LaunchedEffect(liveUpdateTarget?.cityId) {
        val target = liveUpdateTarget ?: return@LaunchedEffect
        val targetCity = CityCatalog.byId(target.cityId) ?: return@LaunchedEffect
        if (selectedCity?.id != targetCity.id) {
            citySelection.select(targetCity)
            selectedCity = targetCity
            flowScreen = CityFlowScreen.APP
        }
    }
    val flowState = CityFlowState(flowScreen, selectedCity?.id)

    AnimatedContent(
        targetState = flowState,
        transitionSpec = {
            val movingForward = targetState.screen.ordinal > initialState.screen.ordinal
            slideInHorizontally(
                animationSpec = tween(durationMillis = 250),
                initialOffsetX = { width -> if (movingForward) width else -width },
            ) togetherWith slideOutHorizontally(
                animationSpec = tween(durationMillis = 250),
                targetOffsetX = { width -> if (movingForward) -width else width },
            )
        },
        label = "city-configuration",
    ) { target ->
        when (target.screen) {
            CityFlowScreen.WELCOME -> NavigationDestinationSurface {
                CityWelcomeScreen(onChooseCity = { flowScreen = CityFlowScreen.PICKER })
            }
            CityFlowScreen.PICKER -> NavigationDestinationSurface {
                CityPickerScreen(
                    selectedCity = selectedCity,
                    onBack = {
                        flowScreen = if (selectedCity == null) CityFlowScreen.WELCOME else CityFlowScreen.APP
                    },
                    onCitySelected = { city ->
                        citySelection.select(city)
                        FavoriteDeparturesWidgetProvider.requestRefresh(context)
                        selectedCity = city
                        flowScreen = CityFlowScreen.APP
                    },
                )
            }
            CityFlowScreen.APP -> selectedCity?.let { city ->
                key(city.id) {
                    CityAppContent(
                        city = city,
                        onChangeCity = { flowScreen = CityFlowScreen.PICKER },
                        liveUpdateTarget = liveUpdateTarget,
                    )
                }
            }
        }
    }
}

@Composable
private fun CityWelcomeScreen(onChooseCity: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text("Witaj w AutoBUS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Wybierz miasto, z którego komunikacji chcesz korzystać.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChooseCity) { Text("Wybierz miasto") }
    }
}

@Composable
private fun CityPickerScreen(
    selectedCity: CityConfig?,
    onBack: () -> Unit,
    onCitySelected: (CityConfig) -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val cities = remember(query) {
        CityCatalog.cities.filter { city ->
            query.isBlank() || city.name.contains(query, ignoreCase = true) ||
                city.operator.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Wybierz miasto", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć") }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Szukaj miasta lub operatora") },
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
            items(cities, key = { it.id }) { city ->
                val isSelected = city.id == selectedCity?.id
                Surface(
                    onClick = { onCitySelected(city) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    ListItem(
                        headlineContent = { Text(city.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(if (isSelected) "${city.operator} · wybrane miasto" else city.operator)
                        },
                        trailingContent = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Wybrane miasto")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CityAppContent(
    city: CityConfig,
    onChangeCity: () -> Unit,
    liveUpdateTarget: DepartureOpenTarget?,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context, city.id) { TransitRepository(context, city) }
    val scope = rememberCoroutineScope()
    var retryKey by remember(city.id) { mutableIntStateOf(0) }
    var snapshot by remember(city.id) { mutableStateOf<ScheduleSnapshot?>(null) }
    var initialError by remember(city.id) { mutableStateOf<String?>(null) }
    var syncing by remember(city.id) { mutableStateOf(false) }
    var syncNotice by remember(city.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(repository, retryKey) {
        initialError = null
        val cached = repository.cachedSchedule()
        if (cached != null) {
            snapshot = cached
            syncing = true
            repository.synchronize().onSuccess { result ->
                snapshot = result.snapshot()
                DepartureWidgetProvider.requestRefresh(context)
                DeparturesWidgetProvider.requestRefresh(context)
                LineDeparturesWidgetProvider.requestRefresh(context)
                FavoriteDeparturesWidgetProvider.requestRefresh(context)
                syncNotice = if (result is SyncResult.Downloaded) "Pobrano nowszy rozkład." else "Rozkład jest aktualny."
            }.onFailure { error ->
                syncNotice = "Pracujesz na ostatnim zapisanym rozkładzie: ${error.message ?: "brak połączenia"}."
            }
            syncing = false
        } else {
            syncing = true
            repository.loadInitial().onSuccess {
                snapshot = it
                DepartureWidgetProvider.requestRefresh(context)
                DeparturesWidgetProvider.requestRefresh(context)
                LineDeparturesWidgetProvider.requestRefresh(context)
                FavoriteDeparturesWidgetProvider.requestRefresh(context)
            }
                .onFailure { initialError = it.message ?: "Nie udało się pobrać rozkładu." }
            syncing = false
        }
    }

    val refresh: () -> Unit = {
        if (!syncing) scope.launch {
            syncing = true
            syncNotice = null
            repository.synchronize().onSuccess { result ->
                snapshot = result.snapshot()
                DepartureWidgetProvider.requestRefresh(context)
                DeparturesWidgetProvider.requestRefresh(context)
                LineDeparturesWidgetProvider.requestRefresh(context)
                FavoriteDeparturesWidgetProvider.requestRefresh(context)
                syncNotice = if (result is SyncResult.Downloaded) "Pobrano nowszy rozkład." else "Rozkład jest aktualny."
            }.onFailure { error ->
                syncNotice = "Aktualizacja nie powiodła się: ${error.message ?: "brak połączenia"}."
            }
            syncing = false
        }
    }

    when {
        snapshot == null && initialError == null -> NavigationDestinationSurface {
            LoadingScreen(city.name, onChangeCity)
        }
        snapshot == null -> NavigationDestinationSurface {
            ErrorScreen(initialError.orEmpty(), retry = { retryKey++ }, onChangeCity = onChangeCity)
        }
        else -> TransitNavigation(
            snapshot = snapshot!!,
            city = city,
            repository = repository,
            syncing = syncing,
            syncNotice = syncNotice,
            onRefresh = refresh,
            onChangeCity = onChangeCity,
            liveUpdateTarget = liveUpdateTarget,
        )
    }
}

@Composable
private fun TransitNavigation(
    snapshot: ScheduleSnapshot,
    city: CityConfig,
    repository: TransitRepository,
    syncing: Boolean,
    syncNotice: String?,
    onRefresh: () -> Unit,
    onChangeCity: () -> Unit,
    liveUpdateTarget: DepartureOpenTarget?,
) {
    val stops = snapshot.stops
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { UserInterfacePreferences(context) }
    var showStopsWithoutLines by remember(city.id) {
        mutableStateOf(preferences.showStopsWithoutLines())
    }
    var markTrackableDepartures by remember(city.id) {
        mutableStateOf(preferences.markTrackableDepartures())
    }
    var homeScreenConfiguration by remember(city.id) {
        mutableStateOf(preferences.homeScreenConfiguration())
    }
    var favoriteStopIds by remember(city.id) {
        mutableStateOf(preferences.favoriteStopIds(city.id))
    }
    val displayedStops = remember(stops, showStopsWithoutLines) {
        filterStopsForDisplay(stops, showStopsWithoutLines)
    }
    val locationAccess = rememberUserLocationAccess()
    val userLocation = (locationAccess.state as? UserLocationState.Available)?.location
    val navController = rememberNavController()
    var departureToHighlight by remember(city.id) { mutableStateOf<DepartureOpenTarget?>(null) }
    LaunchedEffect(liveUpdateTarget, city.id, stops) {
        val target = liveUpdateTarget ?: return@LaunchedEffect
        if (target.cityId != city.id || stops.none { it.id == target.stopId }) return@LaunchedEffect
        departureToHighlight = target
        navController.navigate(Routes.STOP_PREFIX + target.stopId) {
            popUpTo(Routes.SCHEDULE) { inclusive = false }
            launchSingleTop = true
        }
        DepartureLiveUpdateNavigation.clear()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val roots = listOf(
        RootDestination(Routes.SCHEDULE, "Rozkład") { Icon(Icons.Default.Schedule, null) },
        RootDestination(Routes.ALERTS, "KanarAlert") { Icon(Icons.Default.NotificationsActive, null) },
        RootDestination(Routes.PLANNER, "Planer") { Icon(Icons.Default.Route, null) },
        RootDestination(Routes.SETTINGS, "Ustawienia") { Icon(Icons.Default.Settings, null) },
    )
    val onStopClick: (StopData) -> Unit = { navController.navigate(Routes.STOP_PREFIX + it.id) }
    val isRoot = roots.any { root -> currentDestination?.hierarchy?.any { it.route == root.route } == true }

    Scaffold(
        // Top app bars apply the status-bar inset themselves. Passing it through the
        // scaffold as well would leave a second, empty status-bar-sized strip.
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isRoot) {
                NavigationBar {
                    roots.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.SCHEDULE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // This persistent opaque base protects the gap between destinations as well.
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.SCHEDULE,
                modifier = Modifier.fillMaxSize(),
            // Only root tabs slide. Detail destinations retain no custom transition so
            // predictive back never renders a transparent outgoing detail screen.
            enterTransition = { rootTabEnterTransition() },
            exitTransition = { rootTabExitTransition() },
            popEnterTransition = { rootTabEnterTransition() },
            popExitTransition = { rootTabExitTransition() },
            ) {
                composable(route = Routes.SCHEDULE) {
                    NavigationDestinationSurface {
                        ScheduleScreen(
                            stops = displayedStops,
                            cityName = city.name,
                            onStopClick = onStopClick,
                            onLinesClick = { navController.navigate(Routes.LINES) },
                            locationAccess = locationAccess,
                            onMapClick = { navController.navigate(Routes.MAP) },
                            homeScreenConfiguration = homeScreenConfiguration,
                            favoriteStops = displayedStops.filter { it.id in favoriteStopIds },
                        )
                    }
                }
                composable(
                    route = Routes.MAP,
                    enterTransition = {
                        slideInVertically(
                            animationSpec = tween(durationMillis = 250),
                            initialOffsetY = { height -> height },
                        )
                    },
                    popExitTransition = {
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 250),
                            targetOffsetY = { height -> height },
                        )
                    },
                ) {
                    NavigationDestinationSurface {
                        FullscreenStopMap(
                            stops = displayedStops,
                            userLocation = userLocation,
                            onBack = navController::popBackStack,
                            onStopClick = onStopClick,
                        )
                    }
                }
                composable(
                    route = Routes.LINES,
                    enterTransition = { detailEnterFromRight() },
                    popExitTransition = { detailPopExitToRight() },
                ) {
                    NavigationDestinationSurface {
                        LinesScreen(
                            stops = displayedStops,
                            onBack = navController::popBackStack,
                            onLineClick = { line, representativeStop ->
                                navController.navigate("${Routes.LINE_PREFIX}${representativeStop.id}/$line")
                            },
                        )
                    }
                }
                composable(route = Routes.ALERTS) {
                    NavigationDestinationSurface {
                        AlertScreen(
                            stops = displayedStops,
                            onStopClick = onStopClick,
                            onMapClick = { navController.navigate(Routes.MAP) },
                        )
                    }
                }
                composable(route = Routes.PLANNER) {
                    NavigationDestinationSurface {
                        PlannerScreen(stops = displayedStops, snapshot = snapshot)
                    }
                }
                composable(route = Routes.SETTINGS) {
                    NavigationDestinationSurface {
                        SettingsScreen(
                            city = city,
                            onOpenCity = { navController.navigate(Routes.SETTINGS_CITY) },
                            onOpenSchedule = { navController.navigate(Routes.SETTINGS_SCHEDULE) },
                            onOpenApplication = { navController.navigate(Routes.SETTINGS_APPLICATION) },
                            onOpenDeveloper = { navController.navigate(Routes.SETTINGS_DEVELOPER) },
                        )
                    }
                }
                composable(
                    route = Routes.SETTINGS_CITY,
                    enterTransition = { detailEnterFromRight() },
                    popExitTransition = { detailPopExitToRight() },
                ) {
                    NavigationDestinationSurface {
                        SettingsCityScreen(city = city, onBack = navController::popBackStack, onChangeCity = onChangeCity)
                    }
                }
                composable(
                    route = Routes.SETTINGS_SCHEDULE,
                    enterTransition = { detailEnterFromRight() },
                    popExitTransition = { detailPopExitToRight() },
                ) {
                    NavigationDestinationSurface {
                        SettingsScheduleScreen(
                            snapshot = snapshot,
                            syncing = syncing,
                            syncNotice = syncNotice,
                            onRefresh = onRefresh,
                            onBack = navController::popBackStack,
                        )
                    }
                }
                composable(
                    route = Routes.SETTINGS_APPLICATION,
                    enterTransition = { detailEnterFromRight() },
                    popExitTransition = { detailPopExitToRight() },
                ) {
                    NavigationDestinationSurface {
                        SettingsApplicationScreen(
                            onBack = navController::popBackStack,
                            onConfigureHome = { navController.navigate(Routes.HOME_EDITOR) },
                        )
                    }
                }
                composable(
                    route = Routes.SETTINGS_DEVELOPER,
                    enterTransition = { detailEnterFromRight() },
                    popExitTransition = { detailPopExitToRight() },
                ) {
                    NavigationDestinationSurface {
                        SettingsDeveloperScreen(
                            onBack = navController::popBackStack,
                            showStopsWithoutLines = showStopsWithoutLines,
                            onShowStopsWithoutLinesChange = { show ->
                                preferences.setShowStopsWithoutLines(show)
                                showStopsWithoutLines = show
                            },
                            markTrackableDepartures = markTrackableDepartures,
                            onMarkTrackableDeparturesChange = { mark ->
                                preferences.setMarkTrackableDepartures(mark)
                                markTrackableDepartures = mark
                            },
                        )
                    }
                }
                composable(
                    route = Routes.HOME_EDITOR,
                    enterTransition = { detailEnterFromRight() },
                    popExitTransition = { detailPopExitToRight() },
                ) {
                    NavigationDestinationSurface {
                        HomeScreenEditor(
                            configuration = homeScreenConfiguration,
                            hasFavoriteStops = displayedStops.any { it.id in favoriteStopIds },
                            onBack = navController::popBackStack,
                            onConfigurationChange = { updated ->
                                preferences.saveHomeScreenConfiguration(updated)
                                homeScreenConfiguration = updated
                            },
                        )
                    }
                }
            composable(
                route = Routes.STOP,
                arguments = listOf(navArgument("stopId") { type = NavType.StringType }),
                enterTransition = {
                    if (rootTabIndex(initialState.destination.route) != null) {
                        slideInVertically(
                            animationSpec = tween(durationMillis = 250),
                            initialOffsetY = { height -> height },
                        )
                    } else {
                        EnterTransition.None
                    }
                },
                popExitTransition = {
                    if (rootTabIndex(targetState.destination.route) != null) {
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 250),
                            targetOffsetY = { height -> height },
                        )
                    } else {
                        ExitTransition.None
                    }
                },
                ) { entry ->
                    NavigationDestinationSurface {
                        val stop = stops.firstOrNull { it.id == entry.arguments?.getString("stopId") }
                        if (stop == null) MissingScreen("Nie znaleziono przystanku.", navController::popBackStack)
                        else StopDetailScreen(
                            stop = stop,
                            onBack = navController::popBackStack,
                            onLineClick = { line -> navController.navigate("${Routes.LINE_PREFIX}${stop.id}/$line") },
                            cityId = city.id,
                            markTrackableDepartures = markTrackableDepartures,
                            repository = repository,
                            snapshot = snapshot,
                            isFavorite = stop.id in favoriteStopIds,
                            onFavoriteChange = { favorite ->
                                favoriteStopIds = preferences.setFavoriteStop(city.id, stop.id, favorite)
                                FavoriteDeparturesWidgetProvider.requestRefresh(context)
                            },
                            liveUpdateTarget = departureToHighlight?.takeIf { it.stopId == stop.id },
                            onVehicleMap = { departure ->
                                navController.navigate(
                                    "${Routes.VEHICLE_PREFIX}${departure.n}/${departure.departureId}/${Uri.encode(stop.id)}/${Uri.encode(departure.line)}",
                                )
                            },
                        )
                    }
                }
            composable(
                route = Routes.VEHICLE,
                arguments = listOf(
                    navArgument("sideNumber") { type = NavType.IntType },
                    navArgument("departureId") { type = NavType.IntType },
                    navArgument("stopId") { type = NavType.StringType },
                    navArgument("line") { type = NavType.StringType },
                ),
                enterTransition = { detailEnterFromRight() },
                popExitTransition = { detailPopExitToRight() },
            ) { entry ->
                NavigationDestinationSurface {
                    val sideNumber = entry.arguments?.getInt("sideNumber")
                    val departureId = entry.arguments?.getInt("departureId")
                    val stopId = entry.arguments?.getString("stopId")
                    val line = entry.arguments?.getString("line")
                    if (sideNumber == null || sideNumber == 0 || departureId == null || stopId.isNullOrBlank() || line.isNullOrBlank()) {
                        MissingScreen("Nie znaleziono pojazdu.", navController::popBackStack)
                    } else {
                        VehicleMapScreen(
                            target = VehicleMapTarget(sideNumber, departureId, stopId, line),
                            repository = repository,
                            snapshot = snapshot,
                            onBack = navController::popBackStack,
                        )
                    }
                }
            }
            composable(
                route = Routes.LINE,
                arguments = listOf(
                    navArgument("stopId") { type = NavType.StringType },
                    navArgument("lineId") { type = NavType.StringType },
                ),
                enterTransition = { detailEnterFromRight() },
                popExitTransition = { detailPopExitToRight() },
                ) { entry ->
                    NavigationDestinationSurface {
                        val stop = stops.firstOrNull { it.id == entry.arguments?.getString("stopId") }
                        val line = entry.arguments?.getString("lineId")
                        if (stop == null || line.isNullOrBlank()) MissingScreen("Nie znaleziono linii.", navController::popBackStack)
                        else LineDetailScreen(
                            stop = stop,
                            line = line,
                            allStops = stops,
                            onBack = navController::popBackStack,
                            repository = repository,
                            onFullscreenMap = { variant ->
                                navController.navigate(
                                    "${Routes.LINE_MAP_PREFIX}${stop.id}/${Uri.encode(line)}/${Uri.encode(variant.variantKey())}",
                                )
                            },
                        )
                    }
                }
            composable(
                route = Routes.LINE_MAP,
                arguments = listOf(
                    navArgument("stopId") { type = NavType.StringType },
                    navArgument("lineId") { type = NavType.StringType },
                    navArgument("variantKey") { type = NavType.StringType },
                ),
                enterTransition = { detailEnterFromRight() },
                popExitTransition = { detailPopExitToRight() },
            ) { entry ->
                NavigationDestinationSurface {
                    val stop = stops.firstOrNull { it.id == entry.arguments?.getString("stopId") }
                    val line = entry.arguments?.getString("lineId")
                    val variantKey = entry.arguments?.getString("variantKey")
                    val variant = line?.let { selectedLine ->
                        stops.flatMap { it.timetables }
                            .filter { it.line == selectedLine }
                            .distinctBy { it.variantKey() }
                            .firstOrNull { it.variantKey() == variantKey }
                    }
                    val routeStops = variant?.let { selectedVariant ->
                        val stopsById = stops.associateBy { it.id }
                        selectedVariant.routeStopIds.mapNotNull(stopsById::get)
                    }.orEmpty()
                    if (stop == null || line.isNullOrBlank() || variant == null) {
                        MissingScreen("Nie znaleziono przebiegu linii.", navController::popBackStack)
                    } else {
                        FullscreenLineRouteMap(
                            stopsForRoute = routeStops,
                            line = line,
                            direction = variant.direction,
                            onBack = navController::popBackStack,
                            onStopClick = onStopClick,
                        )
                    }
                }
            }
            composable(
                route = Routes.TICKET,
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
                enterTransition = { detailEnterFromRight() },
                popExitTransition = { detailPopExitToRight() },
                ) { entry ->
                    NavigationDestinationSurface {
                        val ticket = ticketById(entry.arguments?.getString("ticketId"))
                        if (ticket == null) MissingScreen("Nie znaleziono biletu.", navController::popBackStack)
                        else TicketDetailScreen(
                            ticket = ticket,
                            onBack = navController::popBackStack,
                            onBuy = { navController.navigate(Routes.TICKET_DEMO_PREFIX + ticket.id) },
                        )
                    }
                }
            composable(
                route = Routes.TICKET_DEMO,
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
                enterTransition = {
                    slideInVertically(
                        animationSpec = tween(durationMillis = 250),
                        initialOffsetY = { height -> height },
                    )
                },
                popExitTransition = {
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 250),
                        targetOffsetY = { height -> height },
                    )
                },
                ) { entry ->
                    NavigationDestinationSurface {
                        val ticket = ticketById(entry.arguments?.getString("ticketId"))
                        if (ticket == null) MissingScreen("Nie znaleziono biletu.", navController::popBackStack)
                        else DemoTicketScreen(ticket, navController::popBackStack)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(
    stops: List<StopData>,
    cityName: String,
    onStopClick: (StopData) -> Unit,
    onLinesClick: () -> Unit,
    locationAccess: UserLocationAccess,
    onMapClick: () -> Unit,
    homeScreenConfiguration: HomeScreenConfiguration,
    favoriteStops: List<StopData>,
) {
    val userLocation = (locationAccess.state as? UserLocationState.Available)?.location
    var query by rememberSaveable { mutableStateOf("") }
    var visibleStopCount by rememberSaveable { mutableIntStateOf(STOP_PAGE_SIZE) }
    val downloadedDataCenter = remember(stops) {
        val coordinates = stops.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        UserLocation(
            latitude = coordinates.map { it.latitude }.average().takeIf(Double::isFinite) ?: 0.0,
            longitude = coordinates.map { it.longitude }.average().takeIf(Double::isFinite) ?: 0.0,
        )
    }
    val sortingLocation = userLocation ?: downloadedDataCenter
    val matchingStops = remember(query, stops, sortingLocation) {
        stops.filter { stop ->
            query.isBlank() || stop.name.contains(query, true) ||
                stop.timetables.any { it.line.contains(query, true) }
        }.sortedBy { it.distanceTo(sortingLocation.latitude, sortingLocation.longitude) }
    }
    LaunchedEffect(query) { visibleStopCount = STOP_PAGE_SIZE }
    val visibleStops = matchingStops.take(visibleStopCount)
    val visibleHomeTiles = homeScreenConfiguration.visibleTiles.filter {
        it != HomeScreenTile.FAVORITE_STOPS || favoriteStops.isNotEmpty()
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AutoBUS $cityName", fontWeight = FontWeight.SemiBold) },
            actions = {
                TextButton(onClick = onLinesClick) { Text("Linie") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (visibleHomeTiles.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Ekran startowy jest pusty. W Ustawieniach wybierz „Konfiguruj ekran startowy”, aby włączyć kafelki.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            visibleHomeTiles.forEach { tile ->
                when (tile) {
                    HomeScreenTile.SEARCH -> {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                label = { Text("Szukaj przystanku lub linii") },
                                supportingText = { Text("${stops.size} dostępnych przystanków") },
                            )
                        }
                        if (query.isNotBlank()) {
                            item { HomeTileTitle("Wyniki wyszukiwania") }
                            if (matchingStops.isEmpty()) {
                                item { EmptySearch() }
                            } else {
                                items(visibleStops, key = { "search-${it.id}" }) { stop ->
                                    StopRow(stop, userLocation, downloadedDataCenter, onClick = { onStopClick(stop) })
                                }
                                if (visibleStops.size < matchingStops.size) {
                                    item {
                                        LoadMoreStops {
                                            visibleStopCount = (visibleStopCount + STOP_PAGE_SIZE).coerceAtMost(matchingStops.size)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HomeScreenTile.MAP -> {
                        item {
                            OfflineMap(
                                stops = stops,
                                onStopClick = onStopClick,
                                userLocation = userLocation,
                                onMapClick = onMapClick,
                                interactive = false,
                                modifier = Modifier.fillMaxWidth().height(250.dp),
                            )
                        }
                    }
                    HomeScreenTile.FAVORITE_STOPS -> {
                        item { HomeTileTitle("Ulubione przystanki") }
                        if (favoriteStops.isEmpty()) {
                            item {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Nie masz jeszcze ulubionych przystanków. Dodaj je ikoną serca na ekranie przystanku.",
                                        modifier = Modifier.padding(20.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        } else {
                            items(favoriteStops, key = { "favorite-${it.id}" }) { stop ->
                                StopRow(stop, userLocation, downloadedDataCenter, onClick = { onStopClick(stop) })
                            }
                        }
                    }
                    HomeScreenTile.NEARBY_STOPS -> if (query.isBlank()) {
                        if (locationAccess.state !is UserLocationState.Available) {
                            item { LocationStatusCard(locationAccess) }
                        }
                        item { HomeTileTitle("Najbliższe przystanki") }
                        if (matchingStops.isEmpty()) {
                            item { EmptySearch() }
                        } else {
                            items(visibleStops, key = { "nearby-${it.id}" }) { stop ->
                                StopRow(stop, userLocation, downloadedDataCenter, onClick = { onStopClick(stop) })
                            }
                            if (visibleStops.size < matchingStops.size) {
                                item {
                                    LoadMoreStops {
                                        visibleStopCount = (visibleStopCount + STOP_PAGE_SIZE).coerceAtMost(matchingStops.size)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTileTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun LoadMoreStops(onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onClick) { Text("Załaduj więcej", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun SettingsScreen(
    city: CityConfig,
    onOpenCity: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenApplication: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ustawienia", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { SettingsGroupTitle("Komunikacja") }
            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                        title = "Miasto",
                        summary = "${city.name} · ${city.operator}",
                        onClick = onOpenCity,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        title = "Rozkład jazdy",
                        summary = "Wersja, odświeżanie i zapisane dane",
                        onClick = onOpenSchedule,
                    )
                }
            }
            item { SettingsGroupTitle("Aplikacja") }
            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        title = "Ekran startowy",
                        summary = "Widoczne kafelki i ich kolejność",
                        onClick = onOpenApplication,
                    )
                }
            }
            item { SettingsGroupTitle("Zaawansowane") }
            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                        title = "Opcje deweloperskie",
                        summary = "Diagnostyka i techniczne rekordy przystanków",
                        onClick = onOpenDeveloper,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsNavigationRow(
    leadingIcon: @Composable () -> Unit,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingIcon()
                }
            }
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Przejdź do: $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun SettingsInlineRow(
    leadingIcon: @Composable () -> Unit,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            leadingContent = { leadingIcon() },
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
        )
    }
}

@Composable
private fun SettingsDetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SettingsCityScreen(city: CityConfig, onBack: () -> Unit, onChangeCity: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsDetailTopBar("Miasto", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                SettingsInlineRow(
                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                    title = "${city.name} · ${city.operator}",
                    summary = "Zmień aktywne miasto i pobierany rozkład.",
                    onClick = onChangeCity,
                )
            }
        }
    }
}

@Composable
private fun SettingsScheduleScreen(
    snapshot: ScheduleSnapshot,
    syncing: Boolean,
    syncNotice: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SettingsDetailTopBar("Ustawienia rozkładu", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text("Rozkład v${snapshot.version.version} · ${snapshot.version.validFrom}") },
                        supportingContent = {
                            Text(
                                syncNotice ?: "Ostatnia udana aktualizacja: ${snapshot.lastSuccessfulUpdate.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0)}",
                            )
                        },
                        trailingContent = {
                            if (syncing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            else TextButton(onClick = onRefresh) { Text("Odśwież") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsApplicationScreen(onBack: () -> Unit, onConfigureHome: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsDetailTopBar("Ustawienia aplikacji", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                SettingsInlineRow(
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    title = "Konfiguruj ekran startowy",
                    summary = "Wybierz widoczne kafelki i ich kolejność.",
                    onClick = onConfigureHome,
                )
            }
        }
    }
}

@Composable
private fun SettingsDeveloperScreen(
    onBack: () -> Unit,
    showStopsWithoutLines: Boolean,
    onShowStopsWithoutLinesChange: (Boolean) -> Unit,
    markTrackableDepartures: Boolean,
    onMarkTrackableDeparturesChange: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SettingsDetailTopBar("Opcje deweloperskie", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Surface(
                    onClick = { onShowStopsWithoutLinesChange(!showStopsWithoutLines) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text("Pokaż przystanki bez linii") },
                        supportingContent = {
                            Text(
                                if (showStopsWithoutLines) "Widoczne także są techniczne przystanki bez rozkładu."
                                else "Domyślnie ukryte, aby nie zaśmiecać listy i mapy.",
                            )
                        },
                        trailingContent = {
                            Switch(checked = showStopsWithoutLines, onCheckedChange = onShowStopsWithoutLinesChange)
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Surface(
                    onClick = { onMarkTrackableDeparturesChange(!markTrackableDepartures) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text("Oznacz przejazdy n!=0") },
                        supportingContent = {
                            Text(
                                "Oznacza przejazdy które mają opcję śledzenia. " +
                                    "(Przejazdy dla których pole n zwracane przez `GetTimeTableReal` nie jest równe 0.)",
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = markTrackableDepartures,
                                onCheckedChange = onMarkTrackableDeparturesChange,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreenEditor(
    configuration: HomeScreenConfiguration,
    hasFavoriteStops: Boolean,
    onBack: () -> Unit,
    onConfigurationChange: (HomeScreenConfiguration) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggedTile by remember { mutableStateOf<HomeScreenTile?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestConfiguration = rememberUpdatedState(configuration)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ekran startowy", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Włącz kafelki, które chcesz widzieć na ekranie startowym. Przytrzymaj element i przeciągnij go w górę lub w dół, aby zmienić kolejność.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(configuration.order, key = { _, tile -> tile.storageKey }) { _, tile ->
                val isDragged = draggedTile == tile
                val canShowTile = tile != HomeScreenTile.FAVORITE_STOPS || hasFavoriteStops
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isDragged) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .pointerInput(tile.storageKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedTile = tile
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggedTile = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedTile = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val current = latestConfiguration.value
                                    val currentIndex = current.order.indexOf(tile)
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tile.storageKey }
                                    val targetIndex = when {
                                        dragOffset > 0f -> currentIndex + 1
                                        dragOffset < 0f -> currentIndex - 1
                                        else -> currentIndex
                                    }
                                    if (item != null && abs(dragOffset) >= item.size / 2f &&
                                        targetIndex in current.order.indices
                                    ) {
                                        val reordered = current.order.toMutableList().apply {
                                            removeAt(currentIndex)
                                            add(targetIndex, tile)
                                        }
                                        onConfigurationChange(current.copy(order = reordered))
                                        dragOffset = 0f
                                    }
                                },
                            )
                        },
                ) {
                    ListItem(
                        headlineContent = { Text(tile.label) },
                        supportingContent = {
                            Text(
                                when {
                                    !canShowTile -> "Dodaj najpierw ulubiony przystanek."
                                    tile in configuration.visible -> "Widoczny na ekranie startowym"
                                    else -> "Ukryty na ekranie startowym"
                                },
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DragHandle, contentDescription = "Przeciągnij, aby zmienić kolejność")
                                Switch(
                                    checked = canShowTile && tile in configuration.visible,
                                    enabled = canShowTile,
                                    onCheckedChange = { enabled ->
                                        val visible = configuration.visible.toMutableSet().apply {
                                            if (enabled) add(tile) else remove(tile)
                                        }
                                        onConfigurationChange(configuration.copy(visible = visible))
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationStatusCard(access: UserLocationAccess) {
    if (access.state is UserLocationState.Available) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    if (access.state is UserLocationState.Available) Icons.Default.MyLocation else Icons.Default.LocationOff,
                    null,
                )
            },
            headlineContent = {
                Text(
                    when (access.state) {
                        UserLocationState.Locating -> "Ustalanie Twojej lokalizacji…"
                        UserLocationState.RequestingPermission -> "Potrzebujemy lokalizacji do sortowania przystanków"
                        UserLocationState.PermissionDenied -> "Lokalizacja nie została udostępniona"
                        is UserLocationState.Unavailable -> "Lokalizacja jest chwilowo niedostępna"
                        is UserLocationState.Available -> ""
                    },
                )
            },
            trailingContent = {
                if (access.state !is UserLocationState.Available && access.state !is UserLocationState.Locating) {
                    TextButton(onClick = access.request) { Text("Włącz") }
                }
            },
        )
    }
}

@Composable
private fun StopRow(
    stop: StopData,
    userLocation: UserLocation?,
    downloadedDataCenter: UserLocation,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val lines = stop.timetables.map { it.line }.distinct().joinToString(" · ")
                val reference = userLocation ?: downloadedDataCenter
                Text(
                    "${stop.distanceTo(reference.latitude, reference.longitude)} m · linie $lines",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
private fun LoadingScreen(cityName: String, onChangeCity: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Pobieranie pełnego rozkładu dla $cityName…")
        TextButton(onClick = onChangeCity) { Text("Wybierz inne miasto") }
    }
}

@Composable
private fun ErrorScreen(message: String, retry: () -> Unit, onChangeCity: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp), MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text("Nie udało się wczytać danych", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = retry) { Text("Spróbuj ponownie") }
        TextButton(onClick = onChangeCity) { Text("Wybierz inne miasto") }
    }
}

@Composable
private fun MissingScreen(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message)
        TextButton(onClick = onBack) { Text("Wróć") }
    }
}

@Composable
private fun EmptySearch() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Brak pasujących przystanków")
            Text("Spróbuj innej nazwy lub numeru linii.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val STOP_PAGE_SIZE = 5
