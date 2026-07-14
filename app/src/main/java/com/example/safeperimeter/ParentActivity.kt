package com.example.safeperimeter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * PARENT MODE - scans for nearby child devices broadcasting our service
 * UUID, shows live signal strength / estimated distance, and lets the
 * parent lock onto one device and start perimeter monitoring.
 */
@SuppressLint("MissingPermission")
class ParentActivity : AppCompatActivity() {

    data class Discovered(val address: String, val name: String?, var rssi: Int, var lastSeen: Long)

    private var scanner: BluetoothLeScanner? = null
    private val devices = LinkedHashMap<String, Discovered>()
    private lateinit var adapter: DeviceAdapter
    private var rssiThreshold = BleConstants.DEFAULT_RSSI_THRESHOLD

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = devices.getOrPut(result.device.address) {
                Discovered(result.device.address, result.device.name, result.rssi, System.currentTimeMillis())
            }
            d.rssi = result.rssi
            d.lastSeen = System.currentTimeMillis()
            runOnUiThread { adapter.submit(devices.values.toList()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)

        val list = findViewById<RecyclerView>(R.id.deviceList)
        adapter = DeviceAdapter { selected -> startMonitoring(selected) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val thresholdLabel = findViewById<TextView>(R.id.txtThreshold)
        val seek = findViewById<SeekBar>(R.id.seekThreshold)
        // SeekBar 0..40 maps to -100..-60 dBm
        seek.progress = rssiThreshold + 100
        thresholdLabel.text = getString(R.string.threshold_label, rssiThreshold)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                rssiThreshold = progress - 100
                thresholdLabel.text = getString(R.string.threshold_label, rssiThreshold)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnStopMonitoring).setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
        }

        scanner = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter.bluetoothLeScanner
    }

    override fun onResume() {
        super.onResume()
        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.SERVICE_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    override fun onPause() {
        super.onPause()
        scanner?.stopScan(scanCallback)
    }

    private fun startMonitoring(device: Discovered) {
        val intent = Intent(this, MonitorService::class.java).apply {
            putExtra(BleConstants.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(BleConstants.EXTRA_DEVICE_NAME, device.name ?: getString(R.string.unknown_device))
            putExtra(BleConstants.EXTRA_RSSI_THRESHOLD, rssiThreshold)
        }
        startForegroundService(intent)
    }

    inner class DeviceAdapter(val onClick: (Discovered) -> Unit) :
        RecyclerView.Adapter<DeviceAdapter.VH>() {

        private var items: List<Discovered> = emptyList()

        fun submit(newItems: List<Discovered>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.txtDeviceTitle)
            val subtitle: TextView = v.findViewById(R.id.txtDeviceSubtitle)
            val monitor: Button = v.findViewById(R.id.btnMonitor)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = items[position]
            val dist = BleConstants.estimateDistanceMeters(d.rssi)
            holder.title.text = d.name ?: d.address
            holder.subtitle.text = getString(R.string.device_subtitle, d.rssi, dist)
            holder.monitor.setOnClickListener { onClick(d) }
        }
    }
}
