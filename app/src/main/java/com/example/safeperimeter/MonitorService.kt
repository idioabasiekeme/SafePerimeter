package com.example.safeperimeter

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Foreground service that continuously scans for the selected child
 * device and evaluates the safety perimeter:
 *
 *  1. RSSI below threshold (smoothed over a moving window)  -> "leaving perimeter" alert
 *  2. No advertisement received for LOSS_TIMEOUT_MS         -> "out of range" alert
 *
 * Alerts = high-priority notification + alarm sound + vibration.
 */
@SuppressLint("MissingPermission")
class MonitorService : Service() {

    private var scanner: BluetoothLeScanner? = null
    private var targetAddress: String? = null
    private var targetName: String = "Child device"
    private var rssiThreshold: Int = BleConstants.DEFAULT_RSSI_THRESHOLD

    private val rssiWindow = ArrayDeque<Int>()
    private var lastSeen = 0L
    private var alertActive = false
    private val handler = Handler(Looper.getMainLooper())

    private val watchdog = object : Runnable {
        override fun run() {
            if (lastSeen > 0 && System.currentTimeMillis() - lastSeen > BleConstants.LOSS_TIMEOUT_MS) {
                raiseAlert(getString(R.string.alert_out_of_range, targetName))
            }
            handler.postDelayed(this, 3_000)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address != targetAddress) return
            lastSeen = System.currentTimeMillis()

            rssiWindow.addLast(result.rssi)
            if (rssiWindow.size > BleConstants.SMOOTHING_WINDOW) rssiWindow.removeFirst()
            val smoothed = rssiWindow.average().toInt()
            val distance = BleConstants.estimateDistanceMeters(smoothed)

            if (rssiWindow.size == BleConstants.SMOOTHING_WINDOW && smoothed < rssiThreshold) {
                raiseAlert(getString(R.string.alert_leaving, targetName, smoothed, distance))
            } else if (smoothed >= rssiThreshold + HYSTERESIS_DB) {
                clearAlert()
                updateStatus(getString(R.string.status_inside, targetName, smoothed, distance))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetAddress = intent?.getStringExtra(BleConstants.EXTRA_DEVICE_ADDRESS)
        targetName = intent?.getStringExtra(BleConstants.EXTRA_DEVICE_NAME) ?: targetName
        rssiThreshold = intent?.getIntExtra(
            BleConstants.EXTRA_RSSI_THRESHOLD, BleConstants.DEFAULT_RSSI_THRESHOLD
        ) ?: BleConstants.DEFAULT_RSSI_THRESHOLD

        createChannels()
        startForeground(ONGOING_ID, buildStatusNotification(getString(R.string.status_starting, targetName)))

        scanner = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter.bluetoothLeScanner
        val filter = ScanFilter.Builder().setServiceUuid(BleConstants.SERVICE_UUID).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(listOf(filter), settings, scanCallback)

        lastSeen = System.currentTimeMillis()
        handler.post(watchdog)
        return START_REDELIVER_INTENT
    }

    private fun raiseAlert(message: String) {
        updateStatus(message)
        if (alertActive) return
        alertActive = true

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notificationManager().notify(ALERT_ID, notification)

        // Alarm sound
        runCatching {
            RingtoneManager.getRingtone(
                this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )?.play()
        }
        // Vibration
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1))
    }

    private fun clearAlert() {
        if (!alertActive) return
        alertActive = false
        notificationManager().cancel(ALERT_ID)
    }

    private fun updateStatus(text: String) {
        notificationManager().notify(ONGOING_ID, buildStatusNotification(text))
    }

    private fun buildStatusNotification(text: String) =
        NotificationCompat.Builder(this, STATUS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.monitoring_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, ParentActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannels() {
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdog)
        scanner?.stopScan(scanCallback)
        notificationManager().cancel(ALERT_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STATUS_CHANNEL = "monitor_status"
        const val ALERT_CHANNEL = "perimeter_alerts"
        const val ONGOING_ID = 1
        const val ALERT_ID = 2
        const val HYSTERESIS_DB = 5 // dB above threshold required to clear an alert
    }
}
