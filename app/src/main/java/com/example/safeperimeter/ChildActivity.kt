package com.example.safeperimeter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * CHILD MODE - this phone plays the role of the child's smartwatch.
 * It continuously broadcasts a BLE advertisement carrying our custom
 * service UUID so the parent phone can recognise and track it, and it
 * also listens for short broadcast messages sent from the parent phone.
 */
@SuppressLint("MissingPermission")
class ChildActivity : AppCompatActivity() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertising = false
    private var lastMessage: String? = null
    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var toggle: Button

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            runOnUiThread {
                status.text = getString(R.string.advertising_on)
                toggle.text = getString(R.string.stop_broadcast)
            }
        }
        override fun onStartFailure(errorCode: Int) {
            advertising = false
            runOnUiThread {
                status.text = getString(R.string.advertising_failed, errorCode)
                toggle.text = getString(R.string.start_broadcast)
            }
        }
    }

    /** Listens for parent messages broadcast as MSG_SERVICE_UUID service data. */
    private val msgScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getServiceData(BleConstants.MSG_SERVICE_UUID) ?: return
            val text = String(bytes, Charsets.UTF_8)
            if (text == lastMessage) return
            lastMessage = text
            runOnUiThread {
                message.text = getString(R.string.child_message, text)
                vibrate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child)
        status = findViewById(R.id.txtChildStatus)
        message = findViewById(R.id.txtChildMessage)
        toggle = findViewById(R.id.btnToggleBroadcast)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner

        toggle.setOnClickListener { if (advertising) stopAdvertising() else startAdvertising() }
    }

    override fun onResume() {
        super.onResume()
        // unfiltered scan; we pick out advertisements carrying MSG_SERVICE_UUID data
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, msgScanCallback)
    }

    override fun onPause() {
        super.onPause()
        scanner?.stopScan(msgScanCallback)
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // advertise indefinitely
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // keep the packet small; UUID identifies us
            .addServiceUuid(BleConstants.SERVICE_UUID)
            .build()

        advertiser?.startAdvertising(settings, data, callback)
            ?: run { status.text = getString(R.string.ble_advertiser_unavailable) }
    }

    private fun stopAdvertising() {
        advertiser?.stopAdvertising(callback)
        advertising = false
        status.text = getString(R.string.advertising_off)
        toggle.text = getString(R.string.start_broadcast)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (advertising) advertiser?.stopAdvertising(callback)
    }
}
