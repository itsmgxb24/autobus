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
    interactive: Boolean = true,
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
) {
    TransitMap(
        stops = stopsForRoute, onStopClick = null, modifier = modifier,
        showStopNames = true, line = line, direction = direction, vehicles = vehicles, userLocation = null,
        onMapClick = null, interactive = true, roundedCorners = true,
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
                        icon = LiveVehicleDrawable(routeColor)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
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
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        ) {
            Text(
                text = when {
                    vehicles.isNotEmpty() -> "Pojazdy z serwera MyBus · linia ${line.orEmpty()}"
                    line != null -> "Przebieg linii $line z kolejności przystanków w bazie"
                    else -> "© OpenStreetMap"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

private fun centerOf(stops: List<StopData>): GeoPoint =
    if (stops.isEmpty()) GeoPoint(0.0, 0.0)
    else GeoPoint(stops.map { it.latitude }.average(), stops.map { it.longitude }.average())

private data class MapRenderTag(val userLocationKey: String?)

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

private class LiveVehicleDrawable(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    override fun draw(canvas: Canvas) {
        paint.color = color
        canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), 12f, 12f, paint)
        paint.color = Color.WHITE; paint.textSize = 27f
        canvas.drawText("BUS", bounds.exactCenterX(), bounds.exactCenterY() + 9f, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 54
    override fun getIntrinsicHeight(): Int = 36
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
