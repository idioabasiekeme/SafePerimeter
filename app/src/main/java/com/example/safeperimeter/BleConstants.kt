package com.example.safeperimeter

import android.os.ParcelUuid

object BleConstants {
    // Custom service UUID that identifies our "child watch" advertisements.
    val SERVICE_UUID: ParcelUuid =
        ParcelUuid.fromString("0000feed-0000-1000-8000-00805f9b34fb")

    // Service-data UUID used to broadcast short parent -> child messages.
    val MSG_SERVICE_UUID: ParcelUuid =
        ParcelUuid.fromString("0000feee-0000-1000-8000-00805f9b34fb")

    // Calibrated TX power at 1 m, used for distance estimation (dBm).
    const val TX_POWER_AT_1M = -59

    // Path-loss exponent: 2.0 free space, 2.5-4.0 indoors.
    const val PATH_LOSS_EXPONENT = 2.2

    const val EXTRA_DEVICE_ADDRESS = "device_address"
    const val EXTRA_DEVICE_NAME = "device_name"
    const val EXTRA_RSSI_THRESHOLD = "rssi_threshold"

    const val DEFAULT_RSSI_THRESHOLD = -85    // dBm; weaker => "out of perimeter"
    const val LOSS_TIMEOUT_MS = 12_000L       // no advert seen for this long => alert
    const val SMOOTHING_WINDOW = 5            // moving-average window for RSSI

    fun estimateDistanceMeters(rssi: Int): Double =
        Math.pow(10.0, (TX_POWER_AT_1M - rssi) / (10.0 * PATH_LOSS_EXPONENT))
}
