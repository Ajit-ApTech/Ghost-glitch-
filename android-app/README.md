# Notch Buddy — Android Edition (Option B: Punch-Hole Halo & Glitch Companion)

A standalone native Android application that brings **Notch Buddy's** live telemetry meter and expressive procedural glitch companion to Android smartphones.

---

## 🌟 Key Features

1. **Option B: Punch-Hole Camera Halo**:
   - Automatically detects the selfie camera cutout (center, corner, teardrop) using Android's `DisplayCutout` API.
   - Renders a glowing, sweeping neon gradient halo ring around the camera lens that dynamically changes color based on network or CPU load (Emerald 🟢 → Cyan 🔵 → Amber 🟡 → Glitch Magenta 🔴).
2. **Alive Procedural Glitch Companion**:
   - 60 FPS hardware-accelerated Canvas renderer (no static assets/PNGs).
   - Bézier curve head dome with 3 floating sine-wave tentacles.
   - Natural blinking eyes, interactive pupil dilation, and dynamic mouth expressions (Happy cat smile, Focus oval, Strain zigzag, Panic screaming open).
   - Digital glitch effects: chromatic aberration (RGB split) and horizontal scanline jitter.
3. **Floating System Overlay**:
   - Uses `WindowManager` with `TYPE_APPLICATION_OVERLAY` to float over home screen, browsers, YouTube, and games.
4. **Expanding Obsidian HUD**:
   - Tap the companion or halo to expand an OLED Obsidian dark flyout panel showing real-time Download/Upload speed, CPU load, and top bandwidth consuming apps.
5. **Configurable Haptics (Default: OFF)**:
   - Optional subtle haptic pulses on glitch spikes, with a direct toggle in settings.

---

## 🛠 Prerequisites & Requirements

- **Android Version**: Android 8.0 (API Level 26) or higher.
- **Development Tool**: Android Studio Hedgehog / Iguana / Jellyfish (or Gradle 8.3+ with JDK 17).
- **Permissions Required**:
  - `Display over other apps` (`SYSTEM_ALERT_WINDOW`)
  - `Ignore Battery Optimization` (to prevent OEM background killers from stopping the service)
  - `App Usage Access` (`PACKAGE_USAGE_STATS`, optional for top apps list)

---

## 🚀 How to Build & Run

### Method 1: Android Studio (Recommended)
1. Open **Android Studio**.
2. Select **Open** and choose the `android-app` folder inside this repository:
   `/Users/ajitprajapati/reverse engineer /android-app`
3. Wait for Gradle sync to complete.
4. Connect your Android phone via USB (with USB Debugging enabled in Developer Options) or start an Android Emulator.
5. Click **Run** (Shift + F10).

### Method 2: Command Line (Gradle)
```bash
cd android-app

# Build Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected device via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Architecture Overview

```text
com.notchbuddy.android
├── MainActivity.kt               # Setup UI, permission grants & live preview
├── companion/
│   └── GhostRenderer.kt          # 60 FPS Canvas companion & glitch physics
├── cutout/
│   └── CutoutDetector.kt         # DisplayCutout camera detection
├── model/
│   └── CompanionState.kt         # Moods, SpeedUnits, Telemetry models
├── overlay/
│   ├── OverlayService.kt           # Foreground Service with WindowManager
│   ├── PunchHoleSpeedRingView.kt   # Camera punch-hole circular progress ring & speed bar
│   ├── DraggableCompanionView.kt   # Freely draggable living Glitch companion
│   └── ExpandingHudSheet.kt        # Full Mac Obsidian HUD & in-HUD settings flip
├── settings/
│   └── AppSettings.kt            # SharedPreferences & StateFlow manager
└── telemetry/
    ├── NetworkSpeedMonitor.kt    # TrafficStats network bandwidth monitor
    ├── CpuMonitor.kt             # /proc/stat CPU load sampler
    └── TopAppsMonitor.kt         # Per-app data usage (NetworkStatsManager)
```
