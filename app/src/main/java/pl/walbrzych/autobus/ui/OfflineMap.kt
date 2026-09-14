package pl.walbrzych.autobus.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.LiveVehicle
import pl.walbrzych.autobus.data.destinationLabel

/** Interactive map of every stop from the active downloaded schedule. */
@Composable
fun OfflineMap(
    stops: List<StopData>,
    onStopClick: (StopData) -> Unit,
    modifier: Modifier = Modifier,
    showStopNames: Boolean = false,
    vehicles: List<LiveVehicle> = emptyList(),
    userLocation: UserLocation? = null,
    onMapClick: (() -> Unit)? = null,
    interactive: Boolean = false,
    roundedCorners: Boolean = true,
) {
    TransitMap(
        stops = stops, onStopClick = onStopClick, modifier = modifier,
        showStopNames = showStopNames, line = null, direction = null, vehicles = vehicles,
        userLocation = userLocation, onMapClick = onMapClick, interactive = interactive,
        roundedCorners = roundedCorners,
    )
}

/** A selected-line-only map using the ordered stop sequence stored in KIERUNKI.trasa. */
@Composable
fun LineRouteMap(
    stopsForRoute: List<StopData>,
    line: String,
    direction: String,
    modifier: Modifier = Modifier,
    vehicles: List<LiveVehicle> = emptyList(),
    onStopClick: ((StopData) -> Unit)? = null,
    onMapClick: (() -> Unit)? = null,
    interactive: Boolean = false,
    roundedCorners: Boolean = true,
) {
    TransitMap(
        stops = stopsForRoute, onStopClick = onStopClick, modifier = modifier,
        showStopNames = true, line = line, direction = direction, vehicles = vehicles, userLocation = null,
        onMapClick = onMapClick, interactive = interactive, roundedCorners = roundedCorners,
    )
}

/** Full-screen OSM view: pan and zoom freely, then tap a stop marker for its details. */
@Composable
fun FullscreenStopMap(
    stops: List<StopData>,
    userLocation: UserLocation?,
    onBack: () -> Unit,
    onStopClick: (StopData) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        OfflineMap(
            stops = stops,
            onStopClick = onStopClick,
            userLocation = userLocation,
            roundedCorners = false,
            interactive = true,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
        }
    }
}

/** Full-screen, interactive view of a selected line variant. */
@Composable
fun FullscreenLineRouteMap(
    stopsForRoute: List<StopData>,
    line: String,
    direction: String,
    onBack: () -> Unit,
    onStopClick: (StopData) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LineRouteMap(
            stopsForRoute = stopsForRoute,
            line = line,
            direction = direction,
            onStopClick = onStopClick,
            interactive = true,
            roundedCorners = false,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
        }
    }
}

/** A map that owns one marker and updates that marker in place when a refresh arrives. */
@Composable
fun VehicleLocationMap(
    vehicle: LiveVehicle,
    line: String,
    directionLabel: String,
    nextStopName: String?,
    nextStopId: String?,
    etaLabel: String?,
    routeStops: List<StopData>,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val vehicleContainerColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val vehicleContentColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    val vehicleLocationColor = MaterialTheme.colorScheme.primary.toArgb()
    val vehicleLocationContentColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    var vehicleMapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = vehicleMapView?.onResume() ?: Unit
            override fun onPause(owner: LifecycleOwner) = vehicleMapView?.onPause() ?: Unit
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier) {
        AndroidView(
            factory = { viewContext ->
                Configuration.getInstance().userAgentValue =
                    "${viewContext.packageName}/${pl.walbrzych.autobus.BuildConfig.VERSION_NAME}"
                MapView(viewContext).apply {
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(16.5)
                    val marker = Marker(this).apply {
                        setAnchor(LiveVehicleDrawable.LOCATION_ANCHOR_X, LiveVehicleDrawable.LOCATION_ANCHOR_Y)
                    }
                    overlays += marker
                    tag = VehicleMapRenderTag(marker)
                    vehicleMapView = this
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
                }
            },
            update = { mapView ->
                val renderTag = mapView.tag as VehicleMapRenderTag
                val position = GeoPoint(vehicle.latitude, vehicle.longitude)
                renderTag.marker.position = position
                renderTag.marker.title = buildString {
                    append("Linia ")
                    append(line.ifBlank { vehicle.line.ifBlank { "—" } })
                    append(" · pojazd ")
                    append(vehicle.sideNumber)
                }
                renderTag.marker.subDescription = vehicle.destination.ifBlank {
                    "Pozycja z serwera MyBus"
                }
                renderTag.marker.icon = LiveVehicleDrawable(
                    context = mapView.context,
                    containerColor = vehicleContainerColor,
                    contentColor = vehicleContentColor,
                    locationColor = vehicleLocationColor,
                    locationContentColor = vehicleLocationContentColor,
                    line = line.ifBlank { vehicle.line.ifBlank { "—" } },
                    directionLabel = directionLabel,
                    nextStopName = nextStopName,
                    etaLabel = etaLabel,
                )
                renderTag.updateRouteStops(mapView, routeStops, nextStopId)
                if (!renderTag.initialPositionApplied) {
                    mapView.controller.setCenter(position)
                    mapView.post { mapView.controller.setCenter(position) }
                    renderTag.initialPositionApplied = true
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TransitMap(
    stops: List<StopData>,
    onStopClick: ((StopData) -> Unit)?,
    modifier: Modifier,
    showStopNames: Boolean,
    line: String?,
    direction: String?,
    vehicles: List<LiveVehicle>,
    userLocation: UserLocation?,
    onMapClick: (() -> Unit)?,
    interactive: Boolean,
    roundedCorners: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val routeColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val liveVehicleContainerColor = MaterialTheme.colorScheme.tertiaryContainer.toArgb()
    val liveVehicleContentColor = MaterialTheme.colorScheme.onTertiaryContainer.toArgb()
    val liveVehicleLocationColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val liveVehicleLocationContentColor = MaterialTheme.colorScheme.onTertiary.toArgb()

    // MapView.onDetachedFromWindow already performs osmdroid's final onDetach. This
    // effect must not be keyed by mapView: assigning a freshly created MapView used to
    // dispose the initial effect and detach that very view before marker setup.
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mapView?.onResume() ?: Unit
            override fun onPause(owner: LifecycleOwner) = mapView?.onPause() ?: Unit
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val containerModifier = if (roundedCorners) modifier.clip(MaterialTheme.shapes.extraLarge) else modifier
    Box(modifier = containerModifier) {
        AndroidView(
            factory = { viewContext ->
                Configuration.getInstance().userAgentValue =
                    "${viewContext.packageName}/${pl.walbrzych.autobus.BuildConfig.VERSION_NAME}"
                MapView(viewContext).apply {
                    setMultiTouchControls(interactive)
                    isEnabled = interactive
                    isTilesScaledToDpi = true
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(13.5)
                    centerMapOn(userLocation?.asGeoPoint() ?: centerOf(stops))
                    mapView = this
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        onResume()
                    }
                }
            },
            update = { view ->
                val previous = view.tag as? MapRenderTag
                val current = MapRenderTag(userLocation?.key())
                if (previous?.userLocationKey != current.userLocationKey && userLocation != null) {
                    view.centerMapOn(userLocation.asGeoPoint())
                }
                view.tag = current
                view.overlays.clear()
                if (line != null && stops.size > 1) {
                    view.overlays += Polyline().apply {
                        setPoints(stops.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 10f
                    }
                }
                val markerClick: ((StopData) -> Unit)? = onMapClick?.let { openMap ->
                    { _: StopData -> openMap() }
                } ?: onStopClick
                stops.forEach { stop ->
                    view.overlays += stopMarker(view, stop, showStopNames, markerClick)
                }
                vehicles.filter { vehicle ->
                    vehicle.latitude in -90.0..90.0 && vehicle.longitude in -180.0..180.0
                }.forEach { vehicle ->
                    view.overlays += Marker(view).apply {
                        position = GeoPoint(vehicle.latitude, vehicle.longitude)
                        title = "Linia ${vehicle.line.ifBlank { "—" }} · pojazd ${vehicle.sideNumber}"
                        subDescription = vehicle.destination.ifBlank { "Pozycja z serwera MyBus" }
                        icon = LiveVehicleDrawable(
                            context = view.context,
                            containerColor = liveVehicleContainerColor,
                            contentColor = liveVehicleContentColor,
                            locationColor = liveVehicleLocationColor,
                            locationContentColor = liveVehicleLocationContentColor,
                            line = vehicle.line.ifBlank { "—" },
                            directionLabel = vehicle.destinationLabel().ifBlank {
                                vehicle.variant.ifBlank { vehicle.directionCode }
                            },
                        )
                        setAnchor(LiveVehicleDrawable.LOCATION_ANCHOR_X, LiveVehicleDrawable.LOCATION_ANCHOR_Y)
                    }
                }
                userLocation?.let { location ->
                    view.overlays += Marker(view).apply {
                        position = location.asGeoPoint()
                        title = "Twoja lokalizacja"
                        subDescription = "Jednorazowa pozycja urządzenia"
                        icon = UserLocationDrawable()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!interactive && onMapClick != null) {
            // This layer consumes drags and pinches before they reach MapView. Only a
            // genuine tap invokes the callback, keeping the compact preview static.
            Box(
                modifier = Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMapClick,
                ),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

private fun centerOf(stops: List<StopData>): GeoPoint =
    if (stops.isEmpty()) GeoPoint(0.0, 0.0)
    else GeoPoint(stops.map { it.latitude }.average(), stops.map { it.longitude }.average())

private data class MapRenderTag(val userLocationKey: String?)

private class VehicleMapRenderTag(
    val marker: Marker,
    var initialPositionApplied: Boolean = false,
) {
    private var routeStopIds: List<String> = emptyList()
    private var nextStopId: String? = null
    private val routeStopMarkers = mutableListOf<Marker>()

    fun updateRouteStops(mapView: MapView, routeStops: List<StopData>, nextStopId: String?) {
        val newIds = routeStops.map(StopData::id)
        if (newIds == routeStopIds && nextStopId == this.nextStopId) return
        routeStopMarkers.forEach(mapView.overlays::remove)
        routeStopMarkers.clear()
        routeStopMarkers += routeStops.map { stop ->
            stopMarker(mapView, stop, showName = false, onStopClick = null).apply {
                title = if (stop.id == nextStopId) "Następny przystanek · ${stop.name}" else stop.name
                subDescription = if (stop.id == nextStopId) {
                    "Następny przystanek pojazdu"
                } else {
                    "Przystanek wariantu trasy pojazdu"
                }
            }
        }
        routeStopMarkers.forEach(mapView.overlays::add)
        routeStopIds = newIds
        this.nextStopId = nextStopId
    }
}

private fun UserLocation.asGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
private fun UserLocation.key(): String = "$latitude,$longitude"

private fun MapView.centerMapOn(point: GeoPoint) {
    controller.setCenter(point)
    // The first center assignment may precede AndroidView's measure pass. Repeating it on
    // the view queue guarantees that the user's position is centered after layout.
    post { controller.setCenter(point) }
}

private fun stopMarker(
    mapView: MapView,
    stop: StopData,
    showName: Boolean,
    onStopClick: ((StopData) -> Unit)?,
): Marker = Marker(mapView).apply {
    position = GeoPoint(stop.latitude, stop.longitude)
    title = stop.name
    subDescription = "Przystanek ${stop.id}"
    icon = StopMarkerDrawable(MarkerIconTypeface.from(mapView.context))
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    if (showName) snippet = stop.name
    if (onStopClick != null) {
        setOnMarkerClickListener { _, _ -> onStopClick(stop); true }
    }
}

private object MarkerIconTypeface {
    private var cached: Typeface? = null

    @Synchronized
    fun from(context: Context): Typeface = cached ?: Typeface.createFromAsset(
        context.applicationContext.assets,
        "fonts/materialdesignicons-webfont.ttf",
    ).also { cached = it }
}

private class StopMarkerDrawable(typeface: Typeface) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        this.typeface = typeface
    }
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY() - 10f
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        canvas.drawCircle(cx, cy, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 24f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 30f
        canvas.drawText("󱀔", cx, cy + 10f, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 60
    override fun getIntrinsicHeight(): Int = 60
}

private class LiveVehicleDrawable(
    context: Context,
    private val containerColor: Int,
    private val contentColor: Int,
    private val locationColor: Int,
    private val locationContentColor: Int,
    private val line: String,
    private val directionLabel: String,
    private val nextStopName: String? = null,
    private val etaLabel: String? = null,
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nerdTypeface = NerdMarkerTypeface.from(context)
    private val textTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    override fun draw(canvas: Canvas) {
        val overallLeft = bounds.left.toFloat()
        val overallTop = bounds.top.toFloat()
        val unit = density
        val cardLeft = overallLeft + CARD_LEFT_DP * unit
        val cardTop = overallTop + CARD_TOP_DP * unit
        val cardWidth = CARD_WIDTH_DP * unit
        val cardHeight = CARD_HEIGHT_DP * unit
        val locationX = overallLeft + LOCATION_X_DP * unit
        val locationY = overallTop + LOCATION_Y_DP * unit

        // The circle is the precise GPS point; the connector keeps the compact
        // information card legible without pretending that its corner is the bus.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * unit
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = locationColor
        canvas.drawLine(
            locationX + 5f * unit,
            locationY + 5f * unit,
            cardLeft + 7f * unit,
            cardTop + 8f * unit,
            paint,
        )
        paint.style = Paint.Style.FILL
        paint.color = locationColor
        canvas.drawCircle(locationX, locationY, 8f * unit, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * unit
        paint.color = locationContentColor
        canvas.drawCircle(locationX, locationY, 5f * unit, paint)

        paint.style = Paint.Style.FILL
        paint.color = containerColor
        canvas.drawRoundRect(
            cardLeft,
            cardTop,
            cardLeft + cardWidth,
            cardTop + cardHeight,
            18f * unit,
            18f * unit,
            paint,
        )

        paint.typeface = nerdTypeface
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 25f * unit
        paint.color = contentColor
        canvas.drawText("󰃧", cardLeft + 20f * unit, cardTop + 29f * unit, paint)

        paint.typeface = textTypeface
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 21f * unit
        canvas.drawText(ellipsize(line, 34f * unit), cardLeft + 42f * unit, cardTop + 24f * unit, paint)

        paint.textSize = 10f * unit
        val subtitle = listOf(line, directionLabel).filter(String::isNotBlank).joinToString(" • ")
        canvas.drawText(
            ellipsize(subtitle, 96f * unit),
            cardLeft + 42f * unit,
            cardTop + 39f * unit,
            paint,
        )

        val eta = etaLabel.orEmpty()
        if (eta.isNotBlank()) {
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 12f * unit
            canvas.drawText(ellipsize(eta, 38f * unit), cardLeft + cardWidth - 10f * unit, cardTop + 27f * unit, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f * unit
        val nextStop = nextStopName?.let { "Następny: $it" } ?: "Następny przystanek: —"
        canvas.drawText(
            ellipsize(nextStop, cardWidth - 52f * unit),
            cardLeft + 42f * unit,
            cardTop + cardHeight - 10f * unit,
            paint,
        )
    }
    private fun ellipsize(text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        val available = (maxWidth - paint.measureText(suffix)).coerceAtLeast(0f)
        val count = paint.breakText(text, true, available, null)
        return text.take(count) + suffix
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = (OVERALL_WIDTH_DP * density).toInt()
    override fun getIntrinsicHeight(): Int = (OVERALL_HEIGHT_DP * density).toInt()

    companion object {
        const val OVERALL_WIDTH_DP = 212f
        const val OVERALL_HEIGHT_DP = 86f
        const val LOCATION_X_DP = 12f
        const val LOCATION_Y_DP = 12f
        const val CARD_LEFT_DP = 30f
        const val CARD_TOP_DP = 21f
        const val CARD_WIDTH_DP = 174f
        const val CARD_HEIGHT_DP = 58f
        const val LOCATION_ANCHOR_X = LOCATION_X_DP / OVERALL_WIDTH_DP
        const val LOCATION_ANCHOR_Y = LOCATION_Y_DP / OVERALL_HEIGHT_DP
    }
}

private object NerdMarkerTypeface {
    private var cached: Typeface? = null

    @Synchronized
    fun from(context: Context): Typeface = cached ?: requireNotNull(
        ResourcesCompat.getFont(context.applicationContext, pl.walbrzych.autobus.R.font.commit_mono_nerd_font_propo_regular),
    ).also { cached = it }
}

private class UserLocationDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        paint.color = Color.argb(80, 33, 150, 243)
        canvas.drawCircle(cx, cy, 22f, paint)
        paint.color = Color.rgb(25, 118, 210)
        canvas.drawCircle(cx, cy, 11f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 5f, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 48
    override fun getIntrinsicHeight(): Int = 48
}
