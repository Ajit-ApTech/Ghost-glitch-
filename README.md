# Ghost Glitch Speed Meter 👻⚡

> A sleek, interactive network speed meter and living procedural companion designed for Apple Silicon MacBook camera notches (M1/M2/M3/M4) and web browsers.

[![macOS](https://img.shields.io/badge/macOS-Apple%20Silicon-blue?logo=apple)](https://apple.com)
[![Swift](https://img.shields.io/badge/Swift-5.9+-orange?logo=swift)](https://swift.org)
[![JavaScript](https://img.shields.io/badge/JavaScript-Vanilla-yellow?logo=javascript)](https://developer.mozilla.org)
[![CSS3](https://img.shields.io/badge/CSS3-Modern%20Glassmorphism-blue?logo=css3)](https://developer.mozilla.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 🌟 Highlights

**Ghost Glitch Speed Meter** blends real-time hardware telemetry with a charming, living digital companion:
- **Flanks the Notch**: Speed meters sit on the left wing; ping latency and CPU sit on the right wing.
- **Glitch Ghost Companion**: Floats directly beneath the notch cutout, reacting dynamically with 6 distinct LCD visor facial expressions based on network activity and system stress.
- **Full-featured Expanded HUD**: Click the bar or companion to reveal real-time network graphs, top bandwidth-consuming apps, monthly data quota tracking, and customized reaction thresholds.

---

## 🚀 Key Features

### 👻 1. Living Glitch Ghost Companion
- **Armless, organic floating ghost body** with an animated wavy 3-bump bottom skirt.
- **Glossy LCD visor display** on the chest with real-time cursor eye-tracking catchlights.
- **6 Performance-Reactive Expressions**:
  | Speed Range | Expression | Visor Face | Visual Aura |
  | :--- | :---: | :---: | :--- |
  | **< 10 KB/s** | 😴 **Sleep** | Crescent closed eyes + drifting `Z` | Dim slate-blue calm aura |
  | **10 KB/s – 500 KB/s** | 😊 **Happy** | Emerald green dot eyes + eye-tracking | Soft green glow |
  | **500 KB/s – 1 MB/s** | 🎯 **Focus** | Vibrant cyan-blue eyes | Electric blue aura |
  | **1 MB/s – 5 MB/s** | 😤 **Strain** | Downward arc eyes + animated sweat drop | Amber-orange high load aura |
  | **5 MB/s – 10 MB/s** | 😱 **Panic** | Rapidly shaking red `X` eyes | Pulsing crimson warning glow |
  | **> 10 MB/s** | 💀 **Dead / Overloaded** | Glitched `X` eyes + animated drool | Maxed out red aura |
- **Interactive Petting**: Click the ghost to pet it (`💖 Loving`). Sparkles and hearts cascade outward to the sides and drift downward into visible screen space—specifically engineered to avoid being obscured by MacBook camera bezels!

### ⚡ 2. Intelligent Network Telemetry
- **Dynamic Unit Switching**: Automatically displays low speeds in `KB/s` and seamlessly shifts to `MB/s` as throughput climbs.
- **Bytes ↔ Bits Toggle**: Easily switch between standard Byte rates (`KB/s`, `MB/s`) and Telecom Bit rates (`Kbps`, `Mbps`) with 1 click.
- **Real-Time Sparkline**: Ultra-smooth live Canvas graph plotting recent network spikes.
- **CPU & Latency Monitor**: Real-time ping latency with glowing indicator dot and CPU load percentage.

### 📊 3. Bandwidth Apps & Data Quota Tracking
- **Top Bandwidth Apps**: Identifies and ranks top network-consuming applications (e.g., Chrome, Steam, Slack).
- **Monthly Quota Progress Bar**: Visual progress indicator tracking remaining monthly bandwidth against user limits.
- **Quick-Copy IP Chip**: One-click copying of active local and gateway IP addresses.

### ⚙️ 4. Customizable Speed Thresholds
- Built-in settings panel to manually customize reaction speed thresholds (Sleep, Happy, Focus, Strain, Panic, Dead) to fit your personal internet connection tier.

---

## 💻 Tech Stack & Architecture

- **Web Prototype**:
  - `index.html`: Clean semantic structure with Apple Liquid Retina display simulation.
  - `index.css`: Modern macOS dark-mode glassmorphism (`backdrop-filter: blur()`), custom micro-animations, and responsive notch layout.
  - `app.js`: Procedural canvas rendering engine for the Glitch Ghost, particle physics system, and real-time telemetry simulator.
- **Native macOS App (`mac-app/`)**:
  - **Swift & SwiftUI**: Native macOS status bar / floating panel application.
  - **AppKit Borderless Panel**: Window level configured to `.floating` / `.statusBar` with `.canJoinAllSpaces` and `.fullScreenAuxiliary` so it stays visible above all workspaces.
  - **Hardware Notch Alignment**: Uses `NSScreen.safeAreaInsets.top` and `auxiliaryTopLeftArea` APIs to detect pixel-perfect notch dimensions on Apple Silicon MacBooks (M1, M2, M3, M4).
- **Native Android App (`android-app/`)**:
  - **Kotlin & Jetpack Compose**: High-performance declarative Android UI with Material3.
  - **Option B Punch-Hole Halo**: Automatically detects selfie camera cutouts with `DisplayCutout` and renders an animated glowing neon ring around the lens.
  - **Procedural Canvas Companion**: 60 FPS hardware-accelerated Canvas ghost with sine-wave wavy tentacles, eye tracking, and glitch effects.
  - **System Overlay (`TYPE_APPLICATION_OVERLAY`)**: Floats smoothly over any Android screen with an expanding Obsidian HUD card on tap.


---

## 🛠️ Getting Started

### Option 1: Running the Web Prototype Locally
Clone the repository and open the prototype in your browser:
```bash
git clone https://github.com/Ajit-ApTech/Ghost-glitch-.git
cd Ghost-glitch-

# Start a simple HTTP server:
python3 -m http.server 8088
```
Then visit **`http://localhost:8088`** in your browser.

---

### Option 2: Installing the Native macOS App to `/Applications`

Run the included 1-click installer:
```bash
./install-app.sh
```
This builds an optimized production release and installs **`Notch Buddy.app`** directly into your **`/Applications`** folder:
- **Spotlight Launch**: Press `Cmd + Space`, type `Notch Buddy`, and press Enter.
- **Launchpad**: Available directly in your Applications list.
- **Launch on Startup / Login**:
  Open **System Settings > General > Login Items**, click `+`, and select **Notch Buddy** from `/Applications`.

You can also run it directly in developer mode via:
```bash
./start-notch-buddy.command
```

---

### Option 3: Building & Running the Native Android App (`android-app/`)

1. Open **Android Studio**.
2. Select **Open** and select the `android-app` folder.
3. Connect an Android phone (Android 8.0+) with USB Debugging enabled, or start an emulator.
4. Click **Run** to launch.
5. In the app, tap **Grant** for *Display over other apps*, then tap **Launch Punch-Hole Overlay**!


---

## 🎮 Interactive Controls in Web Demo
- **Click the Ghost**: Pet Glitch to trigger a heart & sparkle burst.
- **Click the Notch Bar**: Open and close the expanded HUD details popover.
- **Bits / Bytes Toggle**: Click `Bytes (B/s)` in the HUD to toggle to `Bits (b/s)`.
- **Performance Studio**: Use the test sliders to simulate varying network speeds and CPU spikes to preview Glitch's expressions.

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).
