package com.example.safeperimeter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * CHILD MODE - this phone plays the role of the child's smartwatch.
 * It continuously broadcasts a BLE advertisement carrying our custom
 * service UUID so the parent phone can recognise and track it.
 */
@SuppressLint("MissingPermission")
class ChildActivity : AppCompatActivity() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising = false
    private lateinit var status: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child)
        status = findViewById(R.id.txtChildStatus)
        toggle = findViewById(R.id.btnToggleBroadcast)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        advertiser = adapter.bluetoothLeAdvertiser

        toggle.setOnClickListener { if (advertising) stopAdvertising() else startAdvertising() }
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

    override fun onDestroy() {
        super.onDestroy()
        if (advertising) advertiser?.stopAdvertising(callback)
    }
}
