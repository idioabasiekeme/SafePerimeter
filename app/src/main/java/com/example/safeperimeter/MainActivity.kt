package com.example.safeperimeter

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Entry point: choose whether this phone acts as the CHILD device
 * (BLE advertiser, simulating the smartwatch) or the PARENT device
 * (BLE scanner that monitors the perimeter).
 */
class MainActivity : AppCompatActivity() {

    private val requiredPermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableListOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnChildMode).setOnClickListener {
            launchIfReady(ChildActivity::class.java)
        }
        findViewById<Button>(R.id.btnParentMode).setOnClickListener {
            launchIfReady(ParentActivity::class.java)
        }
    }

    private fun launchIfReady(target: Class<*>) {
        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bt == null || !bt.isEnabled) {
            Toast.makeText(this, R.string.enable_bluetooth, Toast.LENGTH_LONG).show()
            return
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startActivity(Intent(this, target))
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show()
        }
    }
}
