@file:Suppress("DEPRECATION")

package pl.walbrzych.autobus.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal

data class UserLocation(val latitude: Double, val longitude: Double)

sealed interface UserLocationState {
    data object RequestingPermission : UserLocationState
    data object Locating : UserLocationState
    data class Available(val location: UserLocation) : UserLocationState
    data object PermissionDenied : UserLocationState
    data class Unavailable(val reason: String) : UserLocationState
}

data class UserLocationAccess(val state: UserLocationState, val request: () -> Unit)

/** Requests a one-shot foreground location; it never starts background tracking. */
@Composable
fun rememberUserLocationAccess(): UserLocationAccess {
    val context = LocalContext.current.applicationContext
    val requester = remember(context) { OneShotLocationRequester(context) }
    var state by remember { mutableStateOf<UserLocationState>(UserLocationState.RequestingPermission) }
    lateinit var request: () -> Unit
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            state = UserLocationState.Locating
            requester.request(
                onLocation = { location -> state = UserLocationState.Available(location) },
                onUnavailable = { reason -> state = UserLocationState.Unavailable(reason) },
            )
        } else {
            state = UserLocationState.PermissionDenied
        }
    }
    request = {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (permitted) {
            state = UserLocationState.Locating
            requester.request(
                onLocation = { location -> state = UserLocationState.Available(location) },
                onUnavailable = { reason -> state = UserLocationState.Unavailable(reason) },
            )
        } else {
            state = UserLocationState.RequestingPermission
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LaunchedEffect(Unit) { request() }
    DisposableEffect(requester) { onDispose { requester.cancel() } }
    return UserLocationAccess(state, request)
}

// AndroidX keeps this cancellation-capable bridge for API 26–29.
private class OneShotLocationRequester(context: Context) {
    private val appContext = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)
    private var cancellationSignal: CancellationSignal? = null

    @SuppressLint("MissingPermission") // Permissions are checked immediately before each call.
    fun request(onLocation: (UserLocation) -> Unit, onUnavailable: (String) -> Unit) {
        cancel()
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onUnavailable("Włącz usługi lokalizacji w telefonie.")
            return
        }
        providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { location -> System.currentTimeMillis() - location.time <= MAX_CACHED_LOCATION_AGE_MS }
            .maxByOrNull(Location::getTime)
            ?.let { location ->
                onLocation(UserLocation(location.latitude, location.longitude))
                return
            }
        val signal = CancellationSignal()
        cancellationSignal = signal
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                manager,
                providers.first(),
                signal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                if (location == null) onUnavailable("Nie można teraz odczytać lokalizacji.")
                else onLocation(UserLocation(location.latitude, location.longitude))
                cancellationSignal = null
            }
        }.onFailure {
            cancellationSignal = null
            onUnavailable("Nie można teraz odczytać lokalizacji.")
        }
    }

    fun cancel() {
        cancellationSignal?.cancel()
        cancellationSignal = null
    }

    private companion object {
        const val MAX_CACHED_LOCATION_AGE_MS = 2 * 60 * 1000L
    }
}
