package com.example.opiniaucznia

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothOpinionTransferActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MODE = "opinia_bt_mode"
        const val EXTRA_RECORD_JSON = "opinia_bt_record_json"
        const val EXTRA_IMPORTED_RECORD = "opinia_bt_imported_record"
        private const val REQUEST_ENABLE_BT = 8101
        private const val REQUEST_DISCOVERABLE = 8102
        private const val REQUEST_PERMISSIONS = 8103
        private const val MAX_TRANSFER_BYTES = 10 * 1024 * 1024
        private const val SERVICE_NAME = "OpiniaUczniaTransfer"
    }

    private val io = Executors.newSingleThreadExecutor()
    private val finished = AtomicBoolean(false)
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var receiverRegistered = false
    private var discoveryAttempts = 0
    private var discoveryRunning = false
    private var targetUuid: UUID? = null
    private var sessionToken: String = ""
    private var recordJson: String = ""
    private var pendingReceivedJson: String? = null
    private var statusView: TextView? = null
    private var actionButton: Button? = null
    private var qrImage: ImageView? = null
    private var root: LinearLayout? = null
    private val seenAddresses = mutableSetOf<String>()

    private val foundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && seenAddresses.add(device.address)) {
                        // Device names are deliberately not shown. The session UUID determines the target.
                        io.execute { tryConnectToSession(device) }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    discoveryRunning = false
                    if (pendingReceivedJson == null && !finished.get()) {
                        if (discoveryAttempts < 3) startDiscoveryAttempt()
                        else runOnUiThread { showStatus("Nie odnaleziono telefonu z tą sesją. Upewnij się, że na drugim telefonie nadal widać kod QR, i spróbuj ponownie.", "Spróbuj ponownie") { startDiscoveryAttempt(reset = true) } }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        buildBaseUi()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "receive"
        recordJson = intent.getStringExtra(EXTRA_RECORD_JSON).orEmpty()
        if (mode == "send" && recordJson.isBlank()) {
            showStatus("Nie przekazano danych opinii do wysłania.", "Zamknij") { finish() }
            return
        }
        ensurePermissions(mode)
    }

    private fun buildBaseUi() {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 36)
        }
        root = column
        val title = TextView(this).apply {
            text = "Transfer opinii przez QR + Bluetooth"
            textSize = 22f
        }
        statusView = TextView(this).apply {
            text = "Przygotowywanie połączenia…"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        qrImage = ImageView(this).apply {
            adjustViewBounds = true
            visibility = ImageView.GONE
            contentDescription = "Kod QR sesji transferu"
        }
        actionButton = Button(this).apply {
            text = "Anuluj"
            setOnClickListener { cancelTransfer() }
        }
        column.addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        column.addView(statusView)
        column.addView(qrImage, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        column.addView(actionButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun showStatus(message: String, buttonText: String? = "Anuluj", action: (() -> Unit)? = { finish() }) {
        runOnUiThread {
            statusView?.text = message
            actionButton?.text = buttonText ?: ""
            actionButton?.visibility = if (buttonText == null) Button.GONE else Button.VISIBLE
            actionButton?.setOnClickListener { action?.invoke() }
        }
    }

    private fun requiredPermissions(mode: String): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_CONNECT
            if (mode == "send") list += Manifest.permission.BLUETOOTH_ADVERTISE
            if (mode != "send") list += Manifest.permission.BLUETOOTH_SCAN
        } else if (mode != "send") {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (mode != "send") list += Manifest.permission.CAMERA
        return list.distinct().toTypedArray()
    }

    private fun ensurePermissions(mode: String) {
        val missing = requiredPermissions(mode).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else ensureBluetoothEnabled(mode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                ensureBluetoothEnabled(intent.getStringExtra(EXTRA_MODE) ?: "receive")
            } else {
                showStatus("Do transferu potrzebne są wymagane uprawnienia Bluetooth${if (intent.getStringExtra(EXTRA_MODE) == "send") "." else " i aparatu."}", "Zamknij") { finish() }
            }
        }
    }

    private fun ensureBluetoothEnabled(mode: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showStatus("Ten telefon nie obsługuje Bluetooth.", "Zamknij") { finish() }
            return
        }
        val enabled = try { adapter.isEnabled } catch (_: SecurityException) { false }
        if (!enabled) {
            showStatus("Aplikacja poprosi system Android o włączenie Bluetooth.", null, null)
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
        } else continueWithMode(mode)
    }

    @Deprecated("Handled for Android system Bluetooth and ZXing scan activities")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK && bluetoothAdapter?.isEnabled == true) continueWithMode(intent.getStringExtra(EXTRA_MODE) ?: "receive")
            else showStatus("Bluetooth nie został włączony. Transfer nie został rozpoczęty.", "Spróbuj ponownie") { ensureBluetoothEnabled(intent.getStringExtra(EXTRA_MODE) ?: "receive") }
            return
        }
        if (requestCode == REQUEST_DISCOVERABLE) {
            if (resultCode > 0) startSenderServer()
            else showStatus("Aby drugi telefon mógł odnaleźć tę sesję, zezwól na widoczność Bluetooth.", "Ponów próbę") { requestDiscoverability() }
            return
        }
        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scan != null) {
            if (scan.contents.isNullOrBlank()) {
                showStatus("Skanowanie anulowano.", "Skanuj ponownie") { launchQrScanner() }
            } else handleScannedQr(scan.contents)
        }
    }

    private fun continueWithMode(mode: String) {
        if (mode == "send") requestDiscoverability() else launchQrScanner()
    }

    private fun requestDiscoverability() {
        showStatus("Potwierdź w systemowym oknie widoczność Bluetooth, aby drugi telefon mógł odnaleźć tę sesję.", null, null)
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300), REQUEST_DISCOVERABLE)
    }

    private fun startSenderServer() {
        val uuid = UUID.randomUUID()
        targetUuid = uuid
        sessionToken = randomToken()
        val qrText = "OPINIA_BT1|$uuid|$sessionToken"
        showStatus("Uruchamiam sesję. Pozostaw ten ekran otwarty i zeskanuj QR drugim telefonem", null, null)
        io.execute {
            try {
                val server = bluetoothAdapter!!.listenUsingRfcommWithServiceRecord(SERVICE_NAME, uuid)
                serverSocket = server
                runOnUiThread {
                    qrImage?.setImageBitmap(makeQr(qrText))
                    qrImage?.visibility = ImageView.VISIBLE
                    statusView?.text = "Gotowe. Na drugim telefonie wybierz \u201eImportuj opini\u0119 przez QR\u201d i zeskanuj ten kod."
                    actionButton?.text = "Zakończ transfer"
                    actionButton?.setOnClickListener { cancelTransfer() }
                }
                while (!finished.get()) {
                    val socket = server.accept()
                    activeSocket = socket
                    try {
                        val input = DataInputStream(socket.inputStream)
                        val output = DataOutputStream(socket.outputStream)
                        val receivedToken = input.readUTF()
                        if (!constantTimeEquals(receivedToken, sessionToken)) {
                            socket.close()
                            activeSocket = null
                            continue
                        }
                        val bytes = recordJson.toByteArray(Charsets.UTF_8)
                        require(bytes.size <= MAX_TRANSFER_BYTES) { "Plik opinii jest zbyt duży." }
                        output.writeInt(bytes.size)
                        output.write(bytes)
                        output.writeUTF(sha256(bytes))
                        output.flush()
                        socket.close()
                        activeSocket = null
                        showStatus("Opinia została wysłana. Możesz zamknąć ten ekran.", "Zamknij") { finish() }
                        break
                    } catch (e: Exception) {
                        runCatching { socket.close() }
                        activeSocket = null
                    }
                }
            } catch (e: SecurityException) {
                showStatus("Android zablokował dostęp do Bluetooth. Sprawdź uprawnienia aplikacji.", "Zamknij") { finish() }
            } catch (e: Exception) {
                if (!finished.get()) showStatus("Nie udało się uruchomić sesji Bluetooth: ${e.message ?: "błąd połączenia"}", "Ponów próbę") { requestDiscoverability() }
            }
        }
    }

    private fun launchQrScanner() {
        showStatus("Skieruj aparat na kod QR wyświetlany na telefonie wysyłającym.", null, null)
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Zeskanuj kod QR telefonu wysyłającego")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .initiateScan()
    }

    private fun handleScannedQr(raw: String) {
        val parts = raw.split('|')
        if (parts.size != 3 || parts[0] != "OPINIA_BT1") {
            showStatus("To nie jest kod sesji transferu opinii.", "Skanuj ponownie") { launchQrScanner() }
            return
        }
        val uuid = runCatching { UUID.fromString(parts[1]) }.getOrNull()
        if (uuid == null || parts[2].length < 30) {
            showStatus("Kod QR jest nieprawidłowy.", "Skanuj ponownie") { launchQrScanner() }
            return
        }
        targetUuid = uuid
        sessionToken = parts[2]
        discoveryAttempts = 0
        seenAddresses.clear()
        startDiscoveryAttempt(reset = true)
    }

    private fun startDiscoveryAttempt(reset: Boolean = false) {
        val adapter = bluetoothAdapter ?: return
        if (reset) {
            discoveryAttempts = 0
            seenAddresses.clear()
        }
        if (finished.get() || pendingReceivedJson != null) return
        if (discoveryRunning) runCatching { adapter.cancelDiscovery() }
        discoveryAttempts++
        discoveryRunning = true
        try {
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(foundReceiver, filter, Context.RECEIVER_EXPORTED)
                else registerReceiver(foundReceiver, filter)
                receiverRegistered = true
            }
            showStatus("Szukam telefonu wskazanego przez kod QR... Nie trzeba wybierać nazwy urządzenia.", null, null)
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            discoveryRunning = false
            showStatus("Brakuje uprawnienia do wyszukiwania urządzeń Bluetooth.", "Zamknij") { finish() }
        }
    }

    private fun tryConnectToSession(device: BluetoothDevice) {
        if (finished.get() || pendingReceivedJson != null) return
        val uuid = targetUuid ?: return
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(uuid)
            activeSocket = socket
            if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter?.cancelDiscovery()
            socket.connect()
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            output.writeUTF(sessionToken)
            output.flush()
            val length = input.readInt()
            require(length in 1..MAX_TRANSFER_BYTES) { "Nieprawidłowy rozmiar pliku." }
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val expectedHash = input.readUTF()
            require(constantTimeEquals(expectedHash, sha256(bytes))) { "Kontrola integralności pliku nie powiodła się." }
            val json = String(bytes, Charsets.UTF_8)
            val record = JSONObject(json)
            JSONObject(record.optString("draft"))
            require(record.optJSONObject("answers") != null) { "Opinia nie zawiera ocen i notatek." }
            pendingReceivedJson = json
            runCatching { socket.close() }
            activeSocket = null
            runOnUiThread { showImportPreview(record) }
        } catch (_: Exception) {
            runCatching { socket?.close() }
            if (activeSocket === socket) activeSocket = null
        }
    }

    private fun showImportPreview(record: JSONObject) {
        runOnUiThread {
            bluetoothAdapter?.cancelDiscovery()
            discoveryRunning = false
            qrImage?.visibility = ImageView.GONE
            statusView?.text = "Połączono i odebrano plik. Sprawdż podsumowanie przed zapisaniem."
            actionButton?.text = "Anuluj import"
            actionButton?.setOnClickListener { pendingReceivedJson = null; finish() }
            val summary = TextView(this).apply {
                textSize = 17f
                text = "Uczeń: ${record.optString("name", "nie podano")}\nSzkoła: ${record.optString("school", "nie podano")}\nKlasa: ${record.optString("className", "nie podano")}\nData urodzenia: ${record.optString("birthDate", "nie podano")}\n\nOpinia zostanie dodana jako nowy rekord. Istniejące dane nie zostaną nadpisane."
                setPadding(0, 16, 0, 16)
            }
            root?.addView(summary, root?.indexOfChild(actionButton) ?: -1)
            val approve = Button(this).apply {
                text = "Zatwierdź import"
                setOnClickListener {
                    val payload = pendingReceivedJson ?: return@setOnClickListener
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_IMPORTED_RECORD, payload))
                    finish()
                }
            }
            root?.addView(approve, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun makeQr(value: String): Bitmap {
        val size = 900
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until size) for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aa = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aa, bb)
    }

    private fun cancelTransfer() {
        finished.set(true)
        runCatching { bluetoothAdapter?.cancelDiscovery() }
        runCatching { activeSocket?.close() }
        runCatching { serverSocket?.close() }
        finish()
    }

    override fun onDestroy() {
        finished.set(true)
        runCatching { bluetoothAdapter?.cancelDiscovery() }
        runCatching { activeSocket?.close() }
        runCatching { serverSocket?.close() }
        if (receiverRegistered) runCatching { unregisterReceiver(foundReceiver) }
        io.shutdownNow()
        super.onDestroy()
    }
}

