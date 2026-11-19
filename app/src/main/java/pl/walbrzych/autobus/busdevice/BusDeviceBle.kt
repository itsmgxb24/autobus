package pl.ruby.lubiechowlabs.autobus.busdevice

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal data class BusDevicePeer(val address: String, val name: String)

internal fun busDevicePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@SuppressLint("MissingPermission")
internal class BusDeviceBle(private val context: Context) : AutoCloseable {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var session: BusDeviceGatt? = null
    private var peer: BusDevicePeer? = null
    val online = MutableStateFlow(false)

    fun hasPermissions() = busDevicePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun scan() = callbackFlow {
        check(hasPermissions()) { "Zezwól aplikacji na dostęp do urządzeń w pobliżu." }
        val scanner = checkNotNull(adapter?.bluetoothLeScanner) { "Włącz Bluetooth, aby wyszukać BusDevice." }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: "BusDevice"
                trySend(BusDevicePeer(result.device.address, name))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("Wyszukiwanie BusDevice nie powiodło się ($errorCode)."))
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BusDeviceProtocol.service)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    suspend fun connect(target: BusDevicePeer): BusDeviceSettings {
        val gatt = active(target)
        return gatt.readSettings()
    }

    suspend fun save(target: BusDevicePeer, settings: BusDeviceSettings): BusDeviceSettings =
        active(target).writeSettings(settings)

    private suspend fun active(target: BusDevicePeer): BusDeviceGatt {
        session?.takeIf { peer?.address == target.address && online.value }?.let { return it }
        close()
        check(hasPermissions()) { "Zezwól aplikacji na dostęp do urządzeń w pobliżu." }
        val bluetooth = checkNotNull(adapter) { "Ten telefon nie obsługuje Bluetooth LE." }
        check(bluetooth.isEnabled) { "Włącz Bluetooth, aby połączyć się z BusDevice." }
        val device = bluetooth.getRemoteDevice(target.address)
        ensureBond(device)
        val gatt = BusDeviceGatt(context, online)
        session = gatt
        peer = target
        try {
            gatt.open(device)
            return gatt
        } catch (error: Throwable) {
            if (session === gatt) close() else gatt.close()
            throw error
        }
    }

    private suspend fun ensureBond(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val result = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val remote = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (remote?.address != device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> result.complete(Unit)
                    BluetoothDevice.BOND_NONE -> result.completeExceptionally(IOException(
                        "Parowanie nie powiodło się. Naciśnij BOOT na ESP32 i spróbuj ponownie.",
                    ))
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> return
                BluetoothDevice.BOND_NONE -> check(device.createBond()) { "Nie udało się rozpocząć parowania." }
            }
            withTimeout(50_000) { result.await() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    override fun close() {
        val previous = session
        session = null
        peer = null
        previous?.close()
        online.value = false
    }
}

@SuppressLint("MissingPermission")
private class BusDeviceGatt(private val context: Context, private val online: MutableStateFlow<Boolean>) : AutoCloseable {
    private data class Operation(val characteristic: BluetoothGattCharacteristic, val result: CompletableDeferred<ByteArray>)

    @Volatile private var link: BluetoothGatt? = null
    @Volatile private var closed = false
    @Volatile private var pending: Operation? = null
    private val ready = CompletableDeferred<Unit>()
    private val lock = Mutex()
    private var transaction = 0

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (closed) return
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                if (!gatt.discoverServices()) fail("Wyszukiwanie usług BusDevice nie powiodło się.")
            } else if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail("Połączenie z BusDevice zostało przerwane ($status).")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (closed) return
            val service = gatt.getService(BusDeviceProtocol.service)
            if (status == BluetoothGatt.GATT_SUCCESS && service?.getCharacteristic(BusDeviceProtocol.request) != null &&
                service.getCharacteristic(BusDeviceProtocol.response) != null) {
                online.value = true
                ready.complete(Unit)
            } else {
                fail("Urządzenie nie udostępnia ustawień BusDevice. Sprawdź oprogramowanie ESP32.")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            complete(characteristic, status, byteArrayOf())
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            complete(characteristic, status, value)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) complete(characteristic, status, characteristic.value ?: byteArrayOf())
        }
    }

    suspend fun open(device: BluetoothDevice) {
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        link = checkNotNull(gatt) { "Nie udało się połączyć z BusDevice." }
        if (closed) gatt.close()
        withTimeout(18_000) { ready.await() }
    }

    suspend fun readSettings(): BusDeviceSettings = lock.withLock { readConfiguration() }

    suspend fun writeSettings(settings: BusDeviceSettings): BusDeviceSettings = lock.withLock {
        val data = BusDeviceProtocol.encode(settings)
        val tx = nextTransaction()
        for (offset in data.indices step BusDeviceProtocol.Chunk) {
            val bytes = data.copyOfRange(offset, minOf(offset + BusDeviceProtocol.Chunk, data.size))
            exchange(BusDeviceProtocol.Stage, tx, offset, bytes)
        }
        exchange(BusDeviceProtocol.Commit, tx, data.size)
        readConfiguration().also { check(it == settings) { "BusDevice nie potwierdził zapisanych ustawień." } }
    }

    private suspend fun readConfiguration(): BusDeviceSettings {
        val tx = nextTransaction()
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= BusDeviceProtocol.MaxSettingsBytes) {
            val chunk = exchange(BusDeviceProtocol.Read, tx, bytes.size())
            bytes.write(chunk)
            if (chunk.size < BusDeviceProtocol.Chunk) return BusDeviceProtocol.decode(bytes.toByteArray())
        }
        throw IOException("Konfiguracja BusDevice przekracza dozwolony rozmiar.")
    }

    private fun nextTransaction(): Int {
        transaction = transaction % 255 + 1
        return transaction
    }

    @Suppress("DEPRECATION")
    private suspend fun exchange(operation: Int, tx: Int, offset: Int, bytes: ByteArray = byteArrayOf()): ByteArray {
        val gatt = checkNotNull(link?.takeUnless { closed }) { "Połącz się z BusDevice." }
        val service = checkNotNull(gatt.getService(BusDeviceProtocol.service))
        val request = checkNotNull(service.getCharacteristic(BusDeviceProtocol.request))
        val response = checkNotNull(service.getCharacteristic(BusDeviceProtocol.response))
        val frame = BusDeviceProtocol.frame(operation, tx, offset, bytes)
        operate(request) {
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(request, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                request.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                request.value = frame
                gatt.writeCharacteristic(request)
            }
        }
        return BusDeviceProtocol.payload(operate(response) { gatt.readCharacteristic(response) }, tx, offset)
    }

    private suspend fun operate(characteristic: BluetoothGattCharacteristic, start: () -> Boolean): ByteArray {
        val operation = Operation(characteristic, CompletableDeferred())
        pending = operation
        try {
            check(start()) { "Bluetooth nie rozpoczął przesyłania ustawień." }
            return withTimeout(8_000) { operation.result.await() }
        } catch (error: Throwable) {
            close()
            throw error
        } finally {
            if (pending === operation) pending = null
        }
    }

    private fun complete(characteristic: BluetoothGattCharacteristic, status: Int, bytes: ByteArray) {
        val operation = pending?.takeIf { it.characteristic.uuid == characteristic.uuid } ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) operation.result.complete(bytes.copyOf())
        else operation.result.completeExceptionally(IOException("Błąd Bluetooth podczas przesyłania ustawień ($status)."))
    }

    private fun fail(message: String) {
        val error = IOException(message)
        ready.completeExceptionally(error)
        pending?.result?.completeExceptionally(error)
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        online.value = false
        ready.cancel()
        pending?.result?.cancel()
        link?.let { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        link = null
    }
}
