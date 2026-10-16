# Ghost Glitch — Android Edition 👻⚡

A standalone native Android application that brings **Ghost Glitch's** live telemetry meter and expressive procedural glitch companion to Android smartphones.

---

## 🌟 Key Features

1. **Independent Overlay Controls**:
   - Launch or turn off the **Floating Companion** independently.
   - Launch or turn off the **Camera Notch Speed Ring** independently.
   - Background service automatically manages lifecycle and stops when both overlays are off.

2. **Punch-Hole Camera Speed Ring**:
   - Anchored at the front camera cutout with an outer halo ring (~33dp diameter).
   - Single-line horizontal layout: `↓ Down  ↑ Up  ( ◯ )  CPU %`.
   - Scaled status bar typography (`12sp`) matching the system clock, battery, and network icons.
   - Dynamic `WindowInsets.displayCutout` detection adjusts ring position and radius automatically.

3. **Alive Procedural Glitch Companion**:
   - 60 FPS hardware-accelerated Canvas renderer (no static assets/PNGs).
   - 60dp draggable cyber-ghost body with dark glossy LCD chest visor and glowing emerald eyes.
   - Double-tap the companion anywhere on screen to expand the Obsidian Telemetry HUD.

4. **Integrated Live CDN Speed Test Engine**:
   - Built-in real-time HTTP throughput test measuring Ping latency, Download Mbps, and Upload Mbps.
   - Live testing indicator and results banner directly in the HUD sheet.

5. **Accurate Network Interface Detection**:
   - Distinguishes between **Mobile Data (4G/5G LTE)** and **Wi-Fi**.
   - Displays real interface name and active IP address.

6. **Top Bandwidth Apps Breakdown**:
   - Displays real friendly app names (Chrome, YouTube, Spotify, WhatsApp) and system services.

7. **Background Persistence**:
   - Configured with `START_STICKY` and `android:stopWithTask="false"`.
   - Overlays stay active even when the app is swiped away from Recents.

---

## 🛠 Prerequisites & Requirements

- **Android Version**: Android 8.0 (API Level 26) or higher.
- **Development Tool**: Android Studio or Gradle 8.3+ with Java 17+.
- **Permissions**:
  - `Display over other apps` (`SYSTEM_ALERT_WINDOW`)
  - `Ignore Battery Optimization`
  - `Internet Access` (`INTERNET`)
  - `Network State` (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`)
  - `App Usage Access` (`PACKAGE_USAGE_STATS`, optional for top apps list)

---

## 🚀 How to Build & Run

### Method 1: Android Studio (Recommended)
1. Open **Android Studio**.
2. Select **Open** and choose the `android-app` folder.
3. Connect your Android phone via USB (with USB Debugging enabled) or start an emulator.
4. Click **Run ▶️** (Shift + F10).

### Method 2: Command Line (Gradle)
```bash
cd android-app

# Build Debug APK
./gradlew assembleDebug

# Output APK:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to your connected device:
adb -d install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Architecture Overview

```text
com.notchbuddy.android
├── MainActivity.kt               # Jetpack Compose dashboard & independent overlay controls
├── companion/
│   └── GhostRenderer.kt          # 60 FPS Canvas companion & visor eye physics
├── cutout/
│   └── CutoutDetector.kt         # DisplayCutout camera detection
├── model/
│   └── CompanionState.kt         # Moods, SpeedUnits, Telemetry models
├── overlay/
│   ├── OverlayService.kt         # Persistent Foreground Service (START_STICKY)
│   ├── PunchHoleSpeedRingView.kt # Camera punch-hole progress ring & horizontal speed meter
│   ├── DraggableCompanionView.kt # 60dp draggable living Glitch companion
│   └── ExpandingHudSheet.kt      # Obsidian HUD, live speed test & settings panel
├── settings/
│   └── AppSettings.kt            # SharedPreferences & StateFlow manager
└── telemetry/
    ├── NetworkSpeedMonitor.kt    # TrafficStats bandwidth & network interface detection
    ├── SpeedTestEngine.kt        # CDN download/upload speed test & ping measurement
    ├── CpuMonitor.kt             # /proc/stat CPU load sampler
    └── TopAppsMonitor.kt         # Per-app data usage (NetworkStatsManager)
```

