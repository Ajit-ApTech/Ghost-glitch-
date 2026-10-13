import SwiftUI
import AppKit

// MARK: - Companion Reaction Mode & Expression Tiers
public enum ReactionMode: String, CaseIterable {
    case network = "Network Speed"
    case cpu     = "CPU Load"
}

public enum GhostExpressionTier: String, CaseIterable {
    case sleep    = "sleep"    // Peaceful / Idle
    case happy    = "happy"    // Light traffic
    case focus    = "focus"    // Moderate traffic (ocular targeting)
    case strain   = "strain"   // Heavy load
    case panic    = "panic"    // Extreme traffic / stress
    case dead     = "dead"     // Saturated / pegged
}

// MARK: - Pet Particle Model
struct PetParticle: Identifiable {
    let id = UUID()
    let emoji: String
    let dx: CGFloat
    let dy: CGFloat
    var progress: CGFloat = 0.0
}

// MARK: - Shared App Settings (Synced across Menu Bar, Notch Companion & HUD Popover)
public class AppSettings: ObservableObject {
    public static let shared = AppSettings()

    @Published public var reactionMode: ReactionMode {
        didSet {
            UserDefaults.standard.set(reactionMode == .cpu ? "cpu" : "network", forKey: "notch_reaction_mode")
        }
    }

    @Published public var useBits: Bool {
        didSet {
            UserDefaults.standard.set(useBits, forKey: "notch_use_bits")
        }
    }

    public init() {
        let savedMode = UserDefaults.standard.string(forKey: "notch_reaction_mode") ?? "network"
        self.reactionMode = savedMode == "cpu" ? .cpu : .network
        self.useBits = UserDefaults.standard.bool(forKey: "notch_use_bits")
    }
}

// MARK: - 1. Notch Bar View (Strictly 34pt tall, snug against M4 camera notch)
public struct NotchBarView: View {
    @ObservedObject var networkMonitor: NetworkMonitor
    @ObservedObject var cpuMonitor: CPUMonitor
    @ObservedObject var appSettings: AppSettings = .shared
    let onToggleHUD: () -> Void

    @State private var speedHistory: [Double] = Array(repeating: 0, count: 20)

    public init(
        networkMonitor: NetworkMonitor,
        cpuMonitor: CPUMonitor,
        appSettings: AppSettings = .shared,
        onToggleHUD: @escaping () -> Void
    ) {
        self.networkMonitor = networkMonitor
        self.cpuMonitor = cpuMonitor
        self.appSettings = appSettings
        self.onToggleHUD = onToggleHUD
    }

    public var body: some View {
        HStack(spacing: 0) {
            // ── Left Wing (156pt): Stacked Speeds + Green Network Sparkline hugging notch ──
            HStack(spacing: 8) {
                let d = formatSpeed(networkMonitor.downloadSpeed, useBits: appSettings.useBits)
                let u = formatSpeed(networkMonitor.uploadSpeed, useBits: appSettings.useBits)

                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 3) {
                        Text("↓")
                            .foregroundColor(Color(red: 0.25, green: 0.79, blue: 0.50))
                            .fontWeight(.black)
                            .font(.system(size: 9.5))
                        Text(d.val)
                            .foregroundColor(.white)
                            .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                        Text(d.unit)
                            .foregroundColor(Color(white: 0.65))
                            .font(.system(size: 8.5, weight: .semibold, design: .monospaced))
                    }
                    HStack(spacing: 3) {
                        Text("↑")
                            .foregroundColor(Color(red: 0.35, green: 0.65, blue: 1.0))
                            .fontWeight(.black)
                            .font(.system(size: 9.5))
                        Text(u.val)
                            .foregroundColor(.white)
                            .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                        Text(u.unit)
                            .foregroundColor(Color(white: 0.65))
                            .font(.system(size: 8.5, weight: .semibold, design: .monospaced))
                    }
                }
                .fixedSize()

                // Live Green Network Speed Sparkline — expands to fill the wing and touches the notch!
                MiniSparklineCanvas(data: speedHistory, color: Color(red: 0.25, green: 0.79, blue: 0.50))
                    .frame(height: 16)
                    .frame(maxWidth: .infinity)
            }
            .padding(.leading, 12)
            .padding(.trailing, 2)
            .frame(width: 156, alignment: .leading)

            // ── Snug Center Notch Cutout Gap (168pt for M4 MacBook Air camera bezel) ──
            Spacer()
                .frame(width: 168)

            // ── Right Wing (156pt): Purple CPU Sparkline originating right from notch + Ping & CPU ──
            HStack(spacing: 8) {
                // Live Purple CPU Usage Sparkline — starts right at the right edge of the notch!
                MiniSparklineCanvas(data: cpuMonitor.cpuHistory, color: Color(red: 0.74, green: 0.55, blue: 0.98))
                    .frame(height: 16)
                    .frame(maxWidth: .infinity)

                HStack(spacing: 4) {
                    Circle()
                        .fill(pingColor)
                        .frame(width: 6, height: 6)
                        .shadow(color: pingColor.opacity(0.8), radius: 3)

                    HStack(spacing: 2) {
                        Text("CPU")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundColor(Color(white: 0.6))
                        Text("\(Int(cpuMonitor.cpuUsagePercentage))%")
                            .foregroundColor(Color(red: 0.74, green: 0.55, blue: 0.98))
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                    }
                }
                .fixedSize()
            }
            .padding(.leading, 2)
            .padding(.trailing, 12)
            .frame(width: 156, alignment: .trailing)
        }
        .frame(width: 480, height: 34)
        .background(Color.black)
        .cornerRadius(17)
        .contentShape(Rectangle())
        .onTapGesture {
            onToggleHUD()
        }
        .onReceive(networkMonitor.$downloadSpeed) { newSpeed in
            speedHistory.append(newSpeed)
            if speedHistory.count > 20 { speedHistory.removeFirst() }
        }
    }

    private func formatSpeed(_ bytesPerSec: Double, useBits: Bool) -> (val: String, unit: String) {
        let speed = useBits ? bytesPerSec * 8.0 : bytesPerSec
        let bUnit = useBits ? "bps" : "B/s"
        let kUnit = useBits ? "Kbps" : "KB/s"
        let mUnit = useBits ? "Mbps" : "MB/s"
        let gUnit = useBits ? "Gbps" : "GB/s"

        if speed >= 1024 * 1024 * 1024 {
            return (String(format: "%.2f", speed / (1024 * 1024 * 1024)), gUnit)
        } else if speed >= 1024 * 1024 {
            return (String(format: "%.1f", speed / (1024 * 1024)), mUnit)
        } else if speed >= 1024 {
            return (String(format: "%.0f", speed / 1024), kUnit)
        } else {
            return (String(format: "%.0f", speed), bUnit)
        }
    }

    private var pingColor: Color {
        let cpu = cpuMonitor.cpuUsagePercentage
        if cpu >= 85 { return Color(red: 0.97, green: 0.32, blue: 0.29) }
        if cpu >= 65 { return Color(red: 0.98, green: 0.65, blue: 0.22) }
        return Color(red: 0.25, green: 0.79, blue: 0.50)
    }
}

// MARK: - 2. Glitch Companion View (Strictly 80pt wide × 74pt tall)
public struct GhostCompanionView: View {
    @ObservedObject var networkMonitor: NetworkMonitor
    @ObservedObject var cpuMonitor: CPUMonitor
    @ObservedObject var appSettings: AppSettings = .shared

    @State private var isPetted = false
    @State private var particles: [PetParticle] = []

    public init(
        networkMonitor: NetworkMonitor,
        cpuMonitor: CPUMonitor,
        appSettings: AppSettings = .shared
    ) {
        self.networkMonitor = networkMonitor
        self.cpuMonitor = cpuMonitor
        self.appSettings = appSettings
    }

    public var currentTier: GhostExpressionTier {
        if appSettings.reactionMode == .cpu {
            let cpu = cpuMonitor.cpuUsagePercentage
            if cpu >= 95 { return .dead }
            if cpu >= 85 { return .panic }
            if cpu >= 65 { return .strain }
            if cpu >= 40 { return .focus }
            if cpu >= 15 { return .happy }
            return .sleep
        } else {
            let downKb = networkMonitor.downloadSpeed / 1024.0
            if downKb >= 10240.0 { return .dead }
            if downKb >= 5120.0  { return .panic }
            if downKb >= 1024.0  { return .strain }
            if downKb >= 500.0   { return .focus }
            if downKb >= 10.0    { return .happy }
            return .sleep
        }
    }

    public var body: some View {
        ZStack {
            GlitchGhostCanvas(tier: currentTier, isPetted: isPetted)
                .frame(width: 80, height: 74)
                .contentShape(Rectangle())
                .scaleEffect(isPetted ? 1.08 : 1.0)
                .animation(.spring(response: 0.25, dampingFraction: 0.6), value: isPetted)
                .onTapGesture {
                    petGhost()
                }

            // Pet Sparkle Particles (Cascade outward to sides & down)
            ForEach(particles) { p in
                Text(p.emoji)
                    .font(.system(size: 18))
                    .offset(x: p.dx * p.progress, y: p.dy * p.progress)
                    .opacity(Double(1.0 - p.progress))
                    .scaleEffect(0.5 + p.progress * 0.7)
            }
        }
        .frame(width: 80, height: 74)
    }

    private func petGhost() {
        withAnimation { isPetted = true }

        for i in 0..<2 {
            let emojis = ["💖", "✨", "👻", "🌸", "⭐", "💫"]
            let emoji = emojis.randomElement() ?? "💖"
            let side: CGFloat = (i == 0 ? (Bool.random() ? 1 : -1) : -1)
            let dx: CGFloat = side * CGFloat.random(in: 28...56)
            let dy: CGFloat = CGFloat.random(in: 12...38)
            let p = PetParticle(emoji: emoji, dx: dx, dy: dy, progress: 0.0)
            particles.append(p)

            withAnimation(.easeOut(duration: 0.95)) {
                if let idx = particles.firstIndex(where: { $0.id == p.id }) {
                    particles[idx].progress = 1.0
                }
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            isPetted = false
            particles.removeAll()
        }
    }
}

// MARK: - 3. Expanded Notch HUD View (Dedicated 440pt popover)
public struct NotchHUDView: View {
    @ObservedObject var networkMonitor: NetworkMonitor
    @ObservedObject var cpuMonitor: CPUMonitor
    @ObservedObject var appSettings: AppSettings = .shared
    let onClose: () -> Void

    @State private var showSettings = false
    @State private var copiedIP = false

    @State private var threshSleep: Double  = 10.0
    @State private var threshHappy: Double  = 500.0
    @State private var threshFocus: Double  = 1024.0
    @State private var threshStrain: Double = 5120.0
    @State private var threshPanic: Double  = 10240.0
    @State private var quotaLimitGB: Double = 100.0

    public init(
        networkMonitor: NetworkMonitor,
        cpuMonitor: CPUMonitor,
        appSettings: AppSettings = .shared,
        onClose: @escaping () -> Void
    ) {
        self.networkMonitor = networkMonitor
        self.cpuMonitor = cpuMonitor
        self.appSettings = appSettings
        self.onClose = onClose
    }

    public var currentTier: GhostExpressionTier {
        if appSettings.reactionMode == .cpu {
            let cpu = cpuMonitor.cpuUsagePercentage
            if cpu >= 95 { return .dead }
            if cpu >= 85 { return .panic }
            if cpu >= 65 { return .strain }
            if cpu >= 40 { return .focus }
            if cpu >= 15 { return .happy }
            return .sleep
        } else {
            let downKb = networkMonitor.downloadSpeed / 1024.0
            if downKb >= threshPanic  { return .dead }
            if downKb >= threshStrain { return .panic }
            if downKb >= threshFocus  { return .strain }
            if downKb >= threshHappy  { return .focus }
            if downKb >= threshSleep  { return .happy }
            return .sleep
        }
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Header Bar
            HStack(spacing: 12) {
                GlitchGhostCanvas(tier: currentTier, isPetted: false)
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.06))
                    .cornerRadius(12)

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("Glitch · Notch Ghost")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                        Text("(\(appSettings.reactionMode == .network ? "⚡ Net" : "🧠 CPU"))")
                            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
                            .foregroundColor(accentColor)
                    }
                    Text(tierDescription)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(accentColor)
                }

                Spacer()

                // Settings Gear Button
                Button(action: {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        showSettings.toggle()
                    }
                }) {
                    Image(systemName: showSettings ? "xmark.circle.fill" : "gearshape.fill")
                        .font(.system(size: 14))
                        .foregroundColor(showSettings ? .white : Color(white: 0.7))
                        .padding(6)
                        .background(Color.white.opacity(showSettings ? 0.20 : 0.08))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }

            Divider().background(Color.white.opacity(0.10))

            if showSettings {
                // Settings View
                VStack(alignment: .leading, spacing: 12) {
                    Text("SETTINGS & CONFIGURATION")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Color(white: 0.5))

                    VStack(alignment: .leading, spacing: 5) {
                        Text("Companion Reaction Source:")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                        Picker("", selection: $appSettings.reactionMode) {
                            Text("⚡ Internet Speed").tag(ReactionMode.network)
                            Text("🧠 CPU Load").tag(ReactionMode.cpu)
                        }
                        .pickerStyle(.segmented)
                    }

                    VStack(alignment: .leading, spacing: 5) {
                        Text("Telemetry Unit Display:")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                        Picker("", selection: $appSettings.useBits) {
                            Text("Bytes (KB/s, MB/s)").tag(false)
                            Text("Bits (Kbps, Mbps)").tag(true)
                        }
                        .pickerStyle(.segmented)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Network Speed Tiers:")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                        ThresholdRow(label: "😊 Happy Threshold:", value: $threshHappy, unit: "KB/s", min: 50, max: 2000)
                        ThresholdRow(label: "🎯 Focus Threshold:", value: $threshFocus, unit: "KB/s", min: 500, max: 5000)
                        ThresholdRow(label: "😤 Strain Threshold:", value: $threshStrain, unit: "KB/s", min: 1000, max: 15000)
                    }
                }
                .padding(8)
            } else {
                // Main Dashboard
                HStack {
                    let d = formatSpeed(networkMonitor.downloadSpeed, useBits: appSettings.useBits)
                    let u = formatSpeed(networkMonitor.uploadSpeed, useBits: appSettings.useBits)
                    let peakD = formatSpeed(networkMonitor.peakDownloadSpeed, useBits: appSettings.useBits)
                    let peakU = formatSpeed(networkMonitor.peakUploadSpeed, useBits: appSettings.useBits)

                    VStack(alignment: .leading, spacing: 3) {
                        Text("DOWNLOAD SPEED")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(white: 0.5))
                        Text("↓ \(d.val) \(d.unit)")
                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                            .foregroundColor(Color(red: 0.25, green: 0.79, blue: 0.50))
                        Text("Peak: \(peakD.val) \(peakD.unit)")
                            .font(.system(size: 9.5, weight: .medium, design: .monospaced))
                            .foregroundColor(Color(white: 0.55))
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 3) {
                        Text("UPLOAD SPEED")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(white: 0.5))
                        Text("↑ \(u.val) \(u.unit)")
                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                            .foregroundColor(Color(red: 0.35, green: 0.65, blue: 1.0))
                        Text("Peak: \(peakU.val) \(peakU.unit)")
                            .font(.system(size: 9.5, weight: .medium, design: .monospaced))
                            .foregroundColor(Color(white: 0.55))
                    }
                }

                // Real Top Bandwidth Consuming Apps (Live nettop)
                VStack(spacing: 5) {
                    HStack {
                        HStack(spacing: 4) {
                            Text("🔥")
                            Text("TOP BANDWIDTH APPS")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(white: 0.5))
                        }
                        Spacer()
                        Text("REAL-TIME")
                            .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                            .foregroundColor(.cyan)
                    }

                    if networkMonitor.topProcesses.isEmpty {
                        Text("Scanning active macOS processes...")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(Color(white: 0.5))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 4)
                    } else {
                        ForEach(networkMonitor.topProcesses) { proc in
                            HStack(spacing: 8) {
                                Text(proc.icon).font(.system(size: 11))
                                Text(proc.name)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.white)
                                Spacer()
                                Text(proc.speedFormatted)
                                    .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                                    .foregroundColor(proc.isHighLoad ? .green : Color(white: 0.75))
                            }
                        }
                    }
                }
                .padding(10)
                .background(Color.white.opacity(0.04))
                .cornerRadius(10)

                // Monthly Data Quota Progress Bar
                VStack(alignment: .leading, spacing: 5) {
                    let totalBytes = Double(networkMonitor.sessionBytesDownloaded) + Double(networkMonitor.sessionBytesUploaded)
                    let totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
                    let pct = min(1.0, totalGB / max(1.0, quotaLimitGB))

                    HStack {
                        Text("SESSION / MONTHLY DATA")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(white: 0.5))
                        Spacer()
                        Text(String(format: "%.2f / %.0f GB (%.0f%%)", totalGB, quotaLimitGB, pct * 100))
                            .font(.system(size: 10, weight: .semibold, design: .monospaced))
                            .foregroundColor(.white)
                    }
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 3)
                                .fill(Color.white.opacity(0.12))
                                .frame(height: 6)
                            RoundedRectangle(cornerRadius: 3)
                                .fill(LinearGradient(colors: [.blue, .cyan], startPoint: .leading, endPoint: .trailing))
                                .frame(width: max(4, g.size.width * CGFloat(pct)), height: 6)
                        }
                    }
                    .frame(height: 6)
                }
                .padding(10)
                .background(Color.white.opacity(0.04))
                .cornerRadius(10)

                // Real Network Info Chips
                HStack(spacing: 8) {
                    InfoChip(
                        title: "INTERFACE",
                        value: networkMonitor.activeInterfaceName
                    )

                    InfoChipWithAction(
                        title: "IP ADDRESS",
                        value: networkMonitor.localIPAddress,
                        actionLabel: copiedIP ? "copied!" : "copy",
                        actionColor: copiedIP ? .green : .cyan
                    ) {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(networkMonitor.localIPAddress, forType: .string)
                        copiedIP = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copiedIP = false }
                    }

                    let sessMb = Double(networkMonitor.sessionBytesDownloaded) / (1024.0 * 1024.0)
                    InfoChip(
                        title: "SESSION",
                        value: sessMb > 1024 ? String(format: "%.2f GB", sessMb / 1024) : String(format: "%.0f MB", sessMb)
                    )
                }

                // Action Buttons
                HStack(spacing: 8) {
                    Button(action: {
                        networkMonitor.runSpeedTest {}
                    }) {
                        HStack(spacing: 5) {
                            if networkMonitor.isSpeedtesting {
                                ProgressView().scaleEffect(0.6).frame(width: 12, height: 12)
                                Text("Testing...")
                            } else {
                                Text("⚡ Run Speedtest")
                            }
                        }
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color.blue.opacity(0.25))
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                    .disabled(networkMonitor.isSpeedtesting)

                    Button(action: { appSettings.useBits.toggle() }) {
                        Text(appSettings.useBits ? "⇄ to MB/s" : "⇄ to Mbps")
                            .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                            .foregroundColor(Color(white: 0.8))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Button(action: { onClose() }) {
                        Text("Close")
                            .font(.system(size: 11))
                            .foregroundColor(Color(white: 0.6))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .frame(width: 440)
        .background(.ultraThinMaterial)
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.7), radius: 24, y: 10)
    }

    private func formatSpeed(_ bytesPerSec: Double, useBits: Bool) -> (val: String, unit: String) {
        let speed = useBits ? bytesPerSec * 8.0 : bytesPerSec
        let bUnit = useBits ? "bps" : "B/s"
        let kUnit = useBits ? "Kbps" : "KB/s"
        let mUnit = useBits ? "Mbps" : "MB/s"
        let gUnit = useBits ? "Gbps" : "GB/s"

        if speed >= 1024 * 1024 * 1024 {
            return (String(format: "%.2f", speed / (1024 * 1024 * 1024)), gUnit)
        } else if speed >= 1024 * 1024 {
            return (String(format: "%.1f", speed / (1024 * 1024)), mUnit)
        } else if speed >= 1024 {
            return (String(format: "%.0f", speed / 1024), kUnit)
        } else {
            return (String(format: "%.0f", speed), bUnit)
        }
    }

    private var tierDescription: String {
        if appSettings.reactionMode == .cpu {
            switch currentTier {
            case .sleep:  return "CPU Idle (0–15%) · Napping peacefully"
            case .happy:  return "CPU Light (16–40%) · Cruising smoothly"
            case .focus:  return "CPU Active (41–65%) · Moderate processing"
            case .strain: return "CPU Heavy (66–85%) · Heavy workload!"
            case .panic:  return "CPU Stress (86–95%) · Cores maxing out!"
            case .dead:   return "CPU Peak (96–100%) · Pegged at limit!"
            }
        } else {
            switch currentTier {
            case .sleep:  return "Idle (< 10 KB/s) · Drifting peacefully..."
            case .happy:  return "Light Bandwidth (10–500 KB/s) · Cruising"
            case .focus:  return "Medium Load (500 KB–1 MB/s) · Locked on target"
            case .strain: return "Heavy Load (1–5 MB/s) · Working hard!"
            case .panic:  return "Extreme Load (5–10 MB/s) · High Traffic!"
            case .dead:   return "Maxed Out (> 10 MB/s) · Network Saturated"
            }
        }
    }

    private var accentColor: Color {
        switch currentTier {
        case .dead, .panic: return Color(red: 0.97, green: 0.32, blue: 0.29)
        case .strain:      return Color(red: 0.98, green: 0.65, blue: 0.22)
        case .focus:       return Color(red: 0.22, green: 0.74, blue: 0.97)
        default:           return Color(red: 0.25, green: 0.79, blue: 0.50)
        }
    }
}

// MARK: - Mini Sparkline Canvas in Wing (Custom Colors Supported)
struct MiniSparklineCanvas: View {
    let data: [Double]
    let color: Color

    var body: some View {
        Canvas { context, size in
            guard data.count > 1 else { return }
            let maxVal = max(data.max() ?? 10.0, 10.0)
            let stepX = size.width / CGFloat(data.count - 1)

            var path = Path()
            for (i, val) in data.enumerated() {
                let x = CGFloat(i) * stepX
                let y = size.height - 2 - (CGFloat(min(1.0, val / maxVal)) * (size.height - 4))
                if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
                else { path.addLine(to: CGPoint(x: x, y: y)) }
            }
            context.stroke(path, with: .color(color), lineWidth: 1.5)
        }
    }
}

// MARK: - Info Chip Components
struct InfoChip: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 8.5, weight: .bold))
                .foregroundColor(Color(white: 0.5))
            Text(value)
                .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                .foregroundColor(.white)
        }
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.04))
        .cornerRadius(8)
    }
}

struct InfoChipWithAction: View {
    let title: String
    let value: String
    let actionLabel: String
    let actionColor: Color
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 8.5, weight: .bold))
                .foregroundColor(Color(white: 0.5))
            HStack(spacing: 4) {
                Text(value)
                    .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                    .foregroundColor(.white)
                Spacer()
                Button(action: action) {
                    Text(actionLabel)
                        .font(.system(size: 8.5, weight: .bold))
                        .foregroundColor(actionColor)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(actionColor.opacity(0.15))
                        .cornerRadius(4)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.04))
        .cornerRadius(8)
    }
}

// MARK: - Threshold Slider Row
struct ThresholdRow: View {
    let label: String
    @Binding var value: Double
    let unit: String
    let min: Double
    let max: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack {
                Text(label)
                    .font(.system(size: 10))
                    .foregroundColor(Color(white: 0.7))
                Spacer()
                Text(String(format: "%.0f %@", value, unit))
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
            }
            Slider(value: $value, in: min...max)
        }
    }
}

// MARK: - Metal-Accelerated Procedural Glitch Ghost Companion with Real Mouse Eye-Tracking
public struct GlitchGhostCanvas: View {
    public let tier: GhostExpressionTier
    public let isPetted: Bool

    public var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                let w = size.width
                let h = size.height
                let cx = w / 2

                // Gentle floating bob
                let floatY = sin(t * 2.3) * 3.5
                let cy = h * 0.46 + floatY

                // Jitter for panic/dead tiers
                var jx: CGFloat = 0
                if tier == .panic { jx = CGFloat(sin(t * 35.0)) * 2.0 }
                if tier == .dead  { jx = CGFloat(sin(t * 50.0)) * 3.0 }

                // Ghost geometry: sleek compact size
                let bw: CGFloat = 20
                let bh: CGFloat = 34
                let top: CGFloat = cy - bh * 0.5

                // ── Global Mouse Cursor Eye-Tracking ───────────────────
                let mouse = NSEvent.mouseLocation
                let screenWidth = NSScreen.main?.frame.width ?? 1440
                let screenHeight = NSScreen.main?.frame.height ?? 900
                let notchCenterX = screenWidth / 2.0
                let notchCenterY = screenHeight - 40.0
                let rawDx = (mouse.x - notchCenterX) / 320.0
                let rawDy = (notchCenterY - mouse.y) / 280.0
                let eyeOffsetX = max(-2.4, min(2.4, rawDx * 2.4))
                let eyeOffsetY = max(-1.8, min(1.8, rawDy * 1.8))

                // ── Ambient Glow ──────────────────────────────────────
                let glowColor: Color
                if isPetted {
                    glowColor = Color(red: 0.92, green: 0.28, blue: 0.60)
                } else {
                    switch tier {
                    case .dead, .panic: glowColor = Color(red: 0.97, green: 0.32, blue: 0.29)
                    case .strain:      glowColor = Color(red: 0.98, green: 0.65, blue: 0.22)
                    case .focus:       glowColor = Color(red: 0.22, green: 0.74, blue: 0.97)
                    case .happy:       glowColor = Color(red: 0.25, green: 0.79, blue: 0.50)
                    case .sleep:       glowColor = Color(red: 0.58, green: 0.64, blue: 0.72)
                    }
                }

                let glowRect = CGRect(x: cx + jx - 30, y: cy - 30, width: 60, height: 60)
                context.fill(Path(ellipseIn: glowRect), with: .color(glowColor.opacity(0.22)))

                // ── Wavy Ghost Body Path ──────────────────────────────
                var bodyPath = Path()
                bodyPath.addArc(
                    center: CGPoint(x: cx + jx, y: top + bw),
                    radius: bw,
                    startAngle: .degrees(180),
                    endAngle: .degrees(0),
                    clockwise: false
                )
                bodyPath.addLine(to: CGPoint(x: cx + jx + bw, y: top + bh - 8))

                // Animated 3-bump wavy bottom skirt
                let wt = t * 3.2
                let wa: CGFloat = 4.5
                bodyPath.addQuadCurve(
                    to: CGPoint(x: cx + jx + bw * 0.34, y: top + bh - 2),
                    control: CGPoint(x: cx + jx + bw * 0.68 + sin(wt) * 1.0, y: top + bh + wa * 0.85)
                )
                bodyPath.addQuadCurve(
                    to: CGPoint(x: cx + jx - bw * 0.34, y: top + bh - 2),
                    control: CGPoint(x: cx + jx + sin(wt + 1.05) * 1.5, y: top + bh + wa)
                )
                bodyPath.addQuadCurve(
                    to: CGPoint(x: cx + jx - bw, y: top + bh - 8),
                    control: CGPoint(x: cx + jx - bw * 0.68 + sin(wt + 2.1) * 1.0, y: top + bh + wa * 0.85)
                )
                bodyPath.addLine(to: CGPoint(x: cx + jx - bw, y: top + bw))
                bodyPath.closeSubpath()

                // Body fill
                let bodyFill: Color = (tier == .panic || tier == .dead)
                    ? Color(red: 1.0, green: 0.86, blue: 0.86, opacity: 0.94)
                    : Color(red: 0.93, green: 0.97, blue: 1.0, opacity: 0.94)

                context.fill(bodyPath, with: .color(bodyFill))
                context.stroke(bodyPath, with: .color(Color.white.opacity(0.5)), lineWidth: 1.5)

                // ── LCD Visor Screen on Chest ─────────────────────────
                let sw: CGFloat = 30
                let sh: CGFloat = 17
                let sx: CGFloat = cx + jx - sw / 2
                let sy: CGFloat = top + bw * 0.45 - sh / 2
                let screenRect = CGRect(x: sx, y: sy, width: sw, height: sh)

                context.fill(Path(roundedRect: screenRect, cornerRadius: 5), with: .color(Color(red: 0.05, green: 0.07, blue: 0.12)))
                context.stroke(Path(roundedRect: screenRect, cornerRadius: 5), with: .color(Color(white: 0.35)), lineWidth: 0.8)

                // ── LCD Visor Facial Expressions with Cursor Eye-Tracking ──
                let ecx = sx + sw / 2
                let ecy = sy + sh / 2
                let es: CGFloat = 7.5

                if isPetted {
                    // Joyful blushing eyes
                    context.stroke(
                        crescentEye(center: CGPoint(x: ecx - es + eyeOffsetX * 0.4, y: ecy + eyeOffsetY * 0.4)),
                        with: .color(Color(red: 0.92, green: 0.28, blue: 0.60)),
                        lineWidth: 1.6
                    )
                    context.stroke(
                        crescentEye(center: CGPoint(x: ecx + es + eyeOffsetX * 0.4, y: ecy + eyeOffsetY * 0.4)),
                        with: .color(Color(red: 0.92, green: 0.28, blue: 0.60)),
                        lineWidth: 1.6
                    )

                    // Rosy pink blush cheeks
                    let blushL = CGRect(x: ecx - es - 4.0, y: ecy + 2.5, width: 3.2, height: 2.2)
                    let blushR = CGRect(x: ecx + es + 0.8, y: ecy + 2.5, width: 3.2, height: 2.2)
                    context.fill(Path(ellipseIn: blushL), with: .color(Color(red: 0.96, green: 0.25, blue: 0.37).opacity(0.55)))
                    context.fill(Path(ellipseIn: blushR), with: .color(Color(red: 0.96, green: 0.25, blue: 0.37).opacity(0.55)))

                    // Happy smiling mouth
                    var petSmile = Path()
                    petSmile.addArc(
                        center: CGPoint(x: ecx, y: ecy + 2.0),
                        radius: 3.2,
                        startAngle: .degrees(15),
                        endAngle: .degrees(165),
                        clockwise: false
                    )
                    context.stroke(petSmile, with: .color(Color(red: 0.92, green: 0.28, blue: 0.60)), lineWidth: 1.4)
                } else {
                    switch tier {
                    case .sleep:
                        // Crescent sleeping eyes
                        context.stroke(
                            crescentEye(center: CGPoint(x: ecx - es, y: ecy + 1)),
                            with: .color(Color(red: 0.38, green: 0.58, blue: 0.88)),
                            lineWidth: 1.4
                        )
                        context.stroke(
                            crescentEye(center: CGPoint(x: ecx + es, y: ecy + 1)),
                            with: .color(Color(red: 0.38, green: 0.58, blue: 0.88)),
                            lineWidth: 1.4
                        )

                        // Animated drifting 'z' sleeping letters floating upward and fading
                        let zProg1 = (t * 1.6).truncatingRemainder(dividingBy: 2.5)
                        let z1Y = ecy - 3 - CGFloat(zProg1) * 7.5
                        let z1A = max(0.0, 1.0 - zProg1 / 2.5)
                        context.draw(
                            Text("z")
                                .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                                .foregroundColor(Color(red: 0.58, green: 0.64, blue: 0.72).opacity(z1A * 0.9)),
                            at: CGPoint(x: ecx + 13, y: z1Y)
                        )

                        let zProg2 = (t * 1.6 + 1.25).truncatingRemainder(dividingBy: 2.5)
                        let z2Y = ecy - 1 - CGFloat(zProg2) * 6.0
                        let z2A = max(0.0, 1.0 - zProg2 / 2.5)
                        context.draw(
                            Text("z")
                                .font(.system(size: 6.5, weight: .bold, design: .monospaced))
                                .foregroundColor(Color(red: 0.58, green: 0.64, blue: 0.72).opacity(z2A * 0.7)),
                            at: CGPoint(x: ecx + 9, y: z2Y)
                        )

                    case .happy:
                        // Emerald green dot eyes with real cursor eye-tracking + catchlights
                        let eyeL = CGRect(x: ecx - es - 2.1 + eyeOffsetX, y: ecy - 2.1 + eyeOffsetY, width: 4.2, height: 4.2)
                        let eyeR = CGRect(x: ecx + es - 2.1 + eyeOffsetX, y: ecy - 2.1 + eyeOffsetY, width: 4.2, height: 4.2)
                        context.fill(Path(ellipseIn: eyeL), with: .color(Color(red: 0.25, green: 0.79, blue: 0.50)))
                        context.fill(Path(ellipseIn: eyeR), with: .color(Color(red: 0.25, green: 0.79, blue: 0.50)))
                        // White catchlights
                        let clL = CGRect(x: ecx - es - 1 + eyeOffsetX, y: ecy - 1.5 + eyeOffsetY, width: 1.4, height: 1.4)
                        let clR = CGRect(x: ecx + es - 1 + eyeOffsetX, y: ecy - 1.5 + eyeOffsetY, width: 1.4, height: 1.4)
                        context.fill(Path(ellipseIn: clL), with: .color(.white))
                        context.fill(Path(ellipseIn: clR), with: .color(.white))

                        // Cute friendly curved smile mouth
                        var happySmile = Path()
                        happySmile.addArc(
                            center: CGPoint(x: ecx, y: ecy + 2.0),
                            radius: 3.4,
                            startAngle: .degrees(15),
                            endAngle: .degrees(165),
                            clockwise: false
                        )
                        context.stroke(happySmile, with: .color(Color(red: 0.25, green: 0.79, blue: 0.50)), lineWidth: 1.4)

                    case .focus:
                        // 🎯 FOCUS: Ocular targeting rings + intense pupils + focused brow lines
                        let eyeCol = Color(red: 0.22, green: 0.74, blue: 0.97)

                        // 1. Concentric ocular focus rings
                        let ringRadius: CGFloat = 4.3
                        let ringL = CGRect(x: ecx - es - ringRadius, y: ecy - ringRadius, width: ringRadius * 2, height: ringRadius * 2)
                        let ringR = CGRect(x: ecx + es - ringRadius, y: ecy - ringRadius, width: ringRadius * 2, height: ringRadius * 2)
                        context.stroke(Path(ellipseIn: ringL), with: .color(eyeCol.opacity(0.48)), lineWidth: 1.1)
                        context.stroke(Path(ellipseIn: ringR), with: .color(eyeCol.opacity(0.48)), lineWidth: 1.1)

                        // 2. Focused pupil dots with eye tracking
                        let pupilR: CGFloat = 2.8
                        let pL = CGRect(x: ecx - es - pupilR + eyeOffsetX * 0.9, y: ecy - pupilR + eyeOffsetY * 0.9, width: pupilR * 2, height: pupilR * 2)
                        let pR = CGRect(x: ecx + es - pupilR + eyeOffsetX * 0.9, y: ecy - pupilR + eyeOffsetY * 0.9, width: pupilR * 2, height: pupilR * 2)
                        context.fill(Path(ellipseIn: pL), with: .color(eyeCol))
                        context.fill(Path(ellipseIn: pR), with: .color(eyeCol))

                        // 3. Specular catchlights
                        let clL = CGRect(x: ecx - es - 1 + eyeOffsetX * 0.9, y: ecy - 1.2 + eyeOffsetY * 0.9, width: 1.2, height: 1.2)
                        let clR = CGRect(x: ecx + es - 1 + eyeOffsetX * 0.9, y: ecy - 1.2 + eyeOffsetY * 0.9, width: 1.2, height: 1.2)
                        context.fill(Path(ellipseIn: clL), with: .color(.white))
                        context.fill(Path(ellipseIn: clR), with: .color(.white))

                        // 4. Focused brow lines slanting down towards center
                        var browL = Path()
                        browL.move(to: CGPoint(x: ecx - es - 4.2, y: ecy - 4.6))
                        browL.addLine(to: CGPoint(x: ecx - 1.4, y: ecy - 3.4))
                        context.stroke(browL, with: .color(eyeCol), lineWidth: 1.3)

                        var browR = Path()
                        browR.move(to: CGPoint(x: ecx + 1.4, y: ecy - 3.4))
                        browR.addLine(to: CGPoint(x: ecx + es + 4.2, y: ecy - 4.6))
                        context.stroke(browR, with: .color(eyeCol), lineWidth: 1.3)

                    case .strain:
                        // Downward arc worried eyes
                        context.stroke(
                            downArcEye(center: CGPoint(x: ecx - es + eyeOffsetX * 0.5, y: ecy + eyeOffsetY * 0.5)),
                            with: .color(Color(red: 0.98, green: 0.65, blue: 0.22)),
                            lineWidth: 1.5
                        )
                        context.stroke(
                            downArcEye(center: CGPoint(x: ecx + es + eyeOffsetX * 0.5, y: ecy + eyeOffsetY * 0.5)),
                            with: .color(Color(red: 0.98, green: 0.65, blue: 0.22)),
                            lineWidth: 1.5
                        )

                        // Strained jagged mouth
                        var jagMouth = Path()
                        jagMouth.move(to: CGPoint(x: ecx - 3.4, y: ecy + 4.2))
                        jagMouth.addLine(to: CGPoint(x: ecx - 1.0, y: ecy + 2.8))
                        jagMouth.addLine(to: CGPoint(x: ecx + 1.2, y: ecy + 4.5))
                        jagMouth.addLine(to: CGPoint(x: ecx + 3.4, y: ecy + 3.5))
                        context.stroke(jagMouth, with: .color(Color(red: 0.98, green: 0.65, blue: 0.22)), lineWidth: 1.4)

                        // Animated pulsing cyan sweat drop on temple
                        let swPhase = (t * 3.5).truncatingRemainder(dividingBy: 2.0)
                        let swY = ecy - 4.5 + CGFloat(swPhase) * 4.2
                        let swA = sin(swPhase * Double.pi / 2.0)
                        let sweat = CGRect(x: sx + sw - 3.5, y: swY, width: 2.8, height: 3.8)
                        context.fill(Path(ellipseIn: sweat), with: .color(Color(red: 0.22, green: 0.74, blue: 0.97).opacity(0.4 + swA * 0.6)))

                    case .panic:
                        // Red X eyes vibrating
                        let shkX = CGFloat(sin(t * 45.0)) * 1.5
                        let shkY = CGFloat(cos(t * 52.0)) * 1.2
                        drawXEye(context: context, center: CGPoint(x: ecx - es + shkX, y: ecy + shkY), color: .red)
                        drawXEye(context: context, center: CGPoint(x: ecx + es + shkX, y: ecy + shkY), color: .red)

                        // Open vibrating gasping mouth
                        let panicMouth = CGRect(x: ecx - 2.2 + shkX, y: ecy + 3.2 + shkY, width: 4.4, height: 3.4)
                        context.stroke(Path(ellipseIn: panicMouth), with: .color(Color.red), lineWidth: 1.4)

                    case .dead:
                        // Glitched X eyes
                        drawXEye(context: context, center: CGPoint(x: ecx - es, y: ecy), color: Color(red: 0.75, green: 0.52, blue: 0.99))
                        drawXEye(context: context, center: CGPoint(x: ecx + es, y: ecy), color: Color(red: 0.75, green: 0.52, blue: 0.99))

                        // Animated cyan drool drip line + droplet bead
                        let droolProg = (t * 2.0).truncatingRemainder(dividingBy: 2.0)
                        let droolH: CGFloat = 2.5 + CGFloat(droolProg) * 4.5
                        var droolPath = Path()
                        droolPath.move(to: CGPoint(x: ecx + 2.5, y: ecy + 3.2))
                        droolPath.addLine(to: CGPoint(x: ecx + 2.5, y: ecy + 3.2 + droolH))
                        context.stroke(droolPath, with: .color(Color(red: 0.22, green: 0.74, blue: 0.97).opacity(0.85)), lineWidth: 1.6)

                        let beadRect = CGRect(x: ecx + 2.5 - 1.3, y: ecy + 3.2 + droolH, width: 2.6, height: 2.6)
                        context.fill(Path(ellipseIn: beadRect), with: .color(Color(red: 0.22, green: 0.74, blue: 0.97).opacity(0.95)))
                    }
                }
            }
        }
    }

    private func crescentEye(center: CGPoint) -> Path {
        var p = Path()
        p.addArc(center: center, radius: 2.8, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
        return p
    }

    private func downArcEye(center: CGPoint) -> Path {
        var p = Path()
        p.addArc(center: center, radius: 2.8, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
        return p
    }

    private func drawXEye(context: GraphicsContext, center: CGPoint, color: Color) {
        var p = Path()
        let r: CGFloat = 2.4
        p.move(to: CGPoint(x: center.x - r, y: center.y - r))
        p.addLine(to: CGPoint(x: center.x + r, y: center.y + r))
        p.move(to: CGPoint(x: center.x + r, y: center.y - r))
        p.addLine(to: CGPoint(x: center.x - r, y: center.y + r))
        context.stroke(p, with: .color(color), lineWidth: 1.4)
    }
}
