# SafePerimeter — Bluetooth Child-Safety Proximity App

Project: **Design and Implementation of a Proximity-Based Bluetooth Security and Safety
Application for Smart Environments** — Child Safety use case
(*notify parent when child's smartwatch exits safe perimeter*).

One Android app, two roles:

| Role | Phone | What it does |
|------|-------|--------------|
| **Child Mode** | child's phone (simulates the smartwatch) | Broadcasts a BLE advertisement with a custom service UUID |
| **Parent Mode** | parent's phone | Scans for that UUID, shows live RSSI + estimated distance, and monitors a chosen device with a foreground service |

## How the perimeter works
- The parent app continuously reads the child's **RSSI** (signal strength).
- RSSI is smoothed with a 5-sample moving average to reduce noise.
- Distance estimate: d = 10^((TxPower − RSSI) / (10·n)) with TxPower = −59 dBm @ 1 m, n = 2.2.
- **Alert 1 — leaving perimeter:** smoothed RSSI drops below the adjustable threshold (default −85 dBm).
- **Alert 2 — out of range:** no advertisement received for 12 s.
- Alerts fire a high-priority notification + alarm sound + vibration. A 5 dB hysteresis
  prevents alert flapping at the boundary.

## Build
1. Install **Android Studio** (Koala or newer).
2. *File → Open* → select this folder. Let Gradle sync.
3. *Run* on two physical phones (BLE does not work on emulators):
   - Phone A → **Child Mode** → *Start broadcasting*
   - Phone B → **Parent Mode** → grant Bluetooth/notification permissions → tap **Monitor** next to the discovered device.

## Get the APK without Android Studio
Every push to this repo triggers the **Build APK** GitHub Actions workflow.
Open the **Actions** tab → latest run → download the **SafePerimeter-debug-apk** artifact.

## Demo / evaluation ideas
- Walk the child phone away and record the distance at which each alert triggers.
- Repeat indoors vs outdoors; vary the threshold slider; plot RSSI vs true distance.
- Metrics: detection latency, false-alarm rate, alert distance vs threshold.

## Notes
- minSdk 26 (Android 8.0), targetSdk 34. Handles Android 12+ runtime BLE permissions and Android 13+ notification permission.
- Real smartwatch instead of a second phone: if the watch advertises BLE constantly, replace the service-UUID scan filter with a device-address filter.

Authors: Idaraobong Etim Ansa, Endiong Cletus Eshiet
# SafePerimeter
