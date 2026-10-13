import SwiftUI

// MARK: - Companion Performance Tier
public enum GhostExpressionTier: String, CaseIterable {
    case sleep    = "sleep"    // < 10 KB/s
    case happy    = "happy"    // 10 KB - 500 KB/s
    case focus    = "focus"    // 500 KB - 1 MB/s
    case strain   = "strain"   // 1 MB - 5 MB/s
    case panic    = "panic"    // 5 MB - 10 MB/s
    case dead     = "dead"     // > 10 MB/s
}

// MARK: - Pet Particle Model
struct PetParticle: Identifiable {
    let id = UUID()
    let emoji: String
    let dx: CGFloat
    let dy: CGFloat
    var progress: CGFloat = 0.0
}

// MARK: - Main NotchView
public struct NotchView: View {
    @ObservedObject var networkMonitor: NetworkMonitor
    @ObservedObject var cpuMonitor: CPUMonitor
    
    @State private var isExpanded = false
    @State private var isPetted = false
    @State private var useBits = false
    @State private var speedHistory: [Double] = Array(repeating: 0, count: 24)
    @State private var particles: [PetParticle] = []
    
    // User configurable speed thresholds (in KB/s)
    @State private var threshSleep: Double  = 10.0
    @State private var threshHappy: Double  = 500.0
    @State private var threshFocus: Double  = 1024.0
    @State private var threshStrain: Double = 5120.0
    @State private var threshPanic: Double  = 10240.0

    public init(networkMonitor: NetworkMonitor, cpuMonitor: CPUMonitor) {
        self.networkMonitor = networkMonitor
        self.cpuMonitor = cpuMonitor
    }

    public var currentTier: GhostExpressionTier {
        let downKb = networkMonitor.downloadSpeed / 1024.0
        if downKb >= threshPanic  { return .dead }
        if downKb >= threshStrain { return .panic }
        if downKb >= threshFocus  { return .strain }
        if downKb >= threshHappy  { return .focus }
        if downKb >= threshSleep  { return .happy }
        return .sleep
    }

    public var body: some View {
        VStack(spacing: 0) {
            // ── Notch Bar & Wing Telemetry ──────────────────────────────
            HStack(spacing: 0) {
                // Left Wing: Live Network Speeds
                HStack(spacing: 8) {
                    let d = formatSpeed(networkMonitor.downloadSpeed, useBits: useBits)
                    let u = formatSpeed(networkMonitor.uploadSpeed, useBits: useBits)

                    HStack(spacing: 3) {
                        Text("↓")
                            .foregroundColor(Color(red: 0.25, green: 0.79, blue: 0.50))
                            .fontWeight(.bold)
                            .font(.system(size: 11))
                        Text("\(d.val) \(d.unit)")
                            .foregroundColor(.white)
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    }

                    HStack(spacing: 3) {
                        Text("↑")
                            .foregroundColor(Color(red: 0.35, green: 0.65, blue: 1.0))
                            .fontWeight(.bold)
                            .font(.system(size: 11))
                        Text("\(u.val) \(u.unit)")
                            .foregroundColor(.white)
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    }
                }
                .padding(.leading, 12)
                .frame(width: 140, alignment: .leading)

                // Center Notch Cutout Spacer
                Spacer()

                // Right Wing: Latency & CPU
                HStack(spacing: 8) {
                    Circle()
                        .fill(pingColor)
                        .frame(width: 6, height: 6)
                        .shadow(color: pingColor.opacity(0.8), radius: 3)

                    HStack(spacing: 3) {
                        Text("CPU")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundColor(Color(white: 0.6))
                        Text("\(Int(cpuMonitor.cpuUsagePercentage))%")
                            .foregroundColor(Color(red: 0.74, green: 0.55, blue: 0.98))
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                    }
                }
                .padding(.trailing, 12)
                .frame(width: 140, alignment: .trailing)
            }
            .frame(width: 410, height: 34)
            .background(Color.black)
            .cornerRadius(18)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.78)) {
                    isExpanded.toggle()
                }
            }

            // ── Glitch Ghost Bay (Hangs directly below the notch) ───────
            ZStack {
                if !isExpanded {
                    GlitchGhostCanvas(tier: currentTier, isPetted: isPetted)
                        .frame(width: 80, height: 74)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            petGhost()
                        }
                        .scaleEffect(isPetted ? 1.08 : 1.0)
                        .animation(.spring(response: 0.25, dampingFraction: 0.6), value: isPetted)

                    // Pet Sparkle Particles (Fanning outward to sides & downward)
                    ForEach(particles) { p in
                        Text(p.emoji)
                            .font(.system(size: 18))
                            .offset(x: p.dx * p.progress, y: p.dy * p.progress)
                            .opacity(Double(1.0 - p.progress))
                            .scaleEffect(0.5 + p.progress * 0.7)
                    }
                }
            }
            .frame(width: 160, height: 74)

            // ── Expanded Notch HUD Popover ──────────────────────────────
            if isExpanded {
                VStack(spacing: 12) {
                    // Top Mascot & Status Badge
                    HStack(spacing: 12) {
                        GlitchGhostCanvas(tier: currentTier, isPetted: isPetted)
                            .frame(width: 48, height: 48)
                            .background(Color.white.opacity(0.06))
                            .cornerRadius(12)
                            .onTapGesture { petGhost() }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Glitch · Notch Ghost")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                            Text(tierDescription)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(accentColor)
                        }

                        Spacer()

                        Text("16 ms · Ultra Low")
                            .font(.system(size: 10, weight: .semibold, design: .monospaced))
                            .foregroundColor(Color(red: 0.25, green: 0.79, blue: 0.50))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(red: 0.25, green: 0.79, blue: 0.50).opacity(0.12))
                            .cornerRadius(8)
                    }

                    Divider().background(Color.white.opacity(0.10))

                    // Speed Meters & Bits/Bytes Toggle
                    HStack {
                        let d = formatSpeed(networkMonitor.downloadSpeed, useBits: useBits)
                        let u = formatSpeed(networkMonitor.uploadSpeed, useBits: useBits)

                        VStack(alignment: .leading, spacing: 3) {
                            Text("DOWNLOAD SPEED")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(white: 0.5))
                            Text("↓ \(d.val) \(d.unit)")
                                .font(.system(size: 17, weight: .bold, design: .monospaced))
                                .foregroundColor(Color(red: 0.25, green: 0.79, blue: 0.50))
                        }

                        Spacer()

                        VStack(alignment: .trailing, spacing: 3) {
                            Text("UPLOAD SPEED")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(white: 0.5))
                            Text("↑ \(u.val) \(u.unit)")
                                .font(.system(size: 17, weight: .bold, design: .monospaced))
                                .foregroundColor(Color(red: 0.35, green: 0.65, blue: 1.0))
                        }
                    }

                    // Unit Toggle Button
                    HStack {
                        Spacer()
                        Button(action: { useBits.toggle() }) {
                            HStack(spacing: 4) {
                                Text("Unit:")
                                    .foregroundColor(Color(white: 0.6))
                                Text(useBits ? "Bits (b/s)" : "Bytes (B/s)")
                                    .foregroundColor(.white)
                                    .fontWeight(.semibold)
                            }
                            .font(.system(size: 10, design: .monospaced))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)
                    }

                    // Top Bandwidth Consuming Apps Breakdown
                    VStack(spacing: 5) {
                        HStack {
                            Text("TOP BANDWIDTH APPS")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(white: 0.5))
                            Spacer()
                        }
                        AppBandwidthRow(name: "Google Chrome", icon: "🌐", speed: "1.4 MB/s", pct: 0.70, color: .green)
                        AppBandwidthRow(name: "Steam Client", icon: "🎮", speed: "420 KB/s", pct: 0.22, color: .blue)
                        AppBandwidthRow(name: "Slack", icon: "💬", speed: "85 KB/s", pct: 0.08, color: .purple)
                    }
                    .padding(8)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)

                    // Monthly Data Quota Progress Bar
                    VStack(alignment: .leading, spacing: 5) {
                        HStack {
                            Text("MONTHLY QUOTA")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(white: 0.5))
                            Spacer()
                            Text("48.2 / 100 GB (48%)")
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
                                    .frame(width: g.size.width * 0.482, height: 6)
                            }
                        }
                        .frame(height: 6)
                    }
                    .padding(8)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)

                    // Quick Actions / Treat Button
                    HStack {
                        Button(action: { petGhost() }) {
                            HStack(spacing: 4) {
                                Text("🍬 Give Treat")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(.pink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.pink.opacity(0.12))
                            .cornerRadius(8)
                        }
                        .buttonStyle(.plain)

                        Spacer()

                        Button(action: { isExpanded = false }) {
                            Text("Close HUD")
                                .font(.system(size: 11))
                                .foregroundColor(Color(white: 0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(14)
                .frame(width: 410)
                .background(.ultraThinMaterial)
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.7), radius: 24, y: 10)
                .transition(.scale(scale: 0.94).combined(with: .opacity))
            }
        }
        .frame(width: 430)
    }

    private func petGhost() {
        withAnimation { isPetted = true }
        
        // Spawn particles fanning outward sideways and downward away from notch
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
        if isPetted { return "💖 Loving the pets!" }
        switch currentTier {
        case .sleep:  return "Idle (< 10 KB/s) · Drifting peacefully..."
        case .happy:  return "Light Bandwidth (10–500 KB/s) · Cruising"
        case .focus:  return "Medium Load (500 KB–1 MB/s) · Streaming"
        case .strain: return "Heavy Load (1–5 MB/s) · Working hard!"
        case .panic:  return "Extreme Load (5–10 MB/s) · High Traffic!"
        case .dead:   return "Maxed Out (> 10 MB/s) · System Saturated"
        }
    }

    private var pingColor: Color {
        switch currentTier {
        case .dead, .panic: return Color(red: 0.97, green: 0.32, blue: 0.29)
        case .strain:      return Color(red: 0.98, green: 0.65, blue: 0.22)
        default:           return Color(red: 0.25, green: 0.79, blue: 0.50)
        }
    }

    private var accentColor: Color {
        switch currentTier {
        case .dead, .panic: return Color(red: 0.97, green: 0.32, blue: 0.29)
        case .strain:      return Color(red: 0.98, green: 0.65, blue: 0.22)
        case .focus:       return Color(red: 0.35, green: 0.65, blue: 1.0)
        default:           return Color(red: 0.25, green: 0.79, blue: 0.50)
        }
    }
}

// MARK: - Top Bandwidth App Row Component
struct AppBandwidthRow: View {
    let name: String
    let icon: String
    let speed: String
    let pct: CGFloat
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Text(icon).font(.system(size: 11))
            Text(name)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.white)
            Spacer()
            Text(speed)
                .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                .foregroundColor(color)
        }
    }
}

// MARK: - Metal-Accelerated Procedural Glitch Ghost Companion
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

                // Ghost geometry: compact size
                let bw: CGFloat = 20
                let bh: CGFloat = 34
                let top: CGFloat = cy - bh * 0.5

                // ── Ambient Glow ──────────────────────────────────────
                let glowColor: Color
                if isPetted {
                    glowColor = Color(red: 0.92, green: 0.28, blue: 0.60)
                } else {
                    switch tier {
                    case .dead, .panic: glowColor = Color(red: 0.97, green: 0.32, blue: 0.29)
                    case .strain:      glowColor = Color(red: 0.98, green: 0.65, blue: 0.22)
                    case .focus:       glowColor = Color(red: 0.35, green: 0.65, blue: 1.0)
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

                // ── LCD Visor Facial Expressions ─────────────────────
                let ecx = sx + sw / 2
                let ecy = sy + sh / 2
                let es: CGFloat = 7.5

                if isPetted {
                    // Joyful blushing eyes
                    context.stroke(
                        crescentEye(center: CGPoint(x: ecx - es, y: ecy)),
                        with: .color(Color(red: 0.92, green: 0.28, blue: 0.60)),
                        lineWidth: 1.6
                    )
                    context.stroke(
                        crescentEye(center: CGPoint(x: ecx + es, y: ecy)),
                        with: .color(Color(red: 0.92, green: 0.28, blue: 0.60)),
                        lineWidth: 1.6
                    )
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

                    case .happy:
                        // Emerald green dot eyes
                        let eyeL = CGRect(x: ecx - es - 2, y: ecy - 2, width: 4, height: 4)
                        let eyeR = CGRect(x: ecx + es - 2, y: ecy - 2, width: 4, height: 4)
                        context.fill(Path(ellipseIn: eyeL), with: .color(Color(red: 0.25, green: 0.79, blue: 0.50)))
                        context.fill(Path(ellipseIn: eyeR), with: .color(Color(red: 0.25, green: 0.79, blue: 0.50)))

                    case .focus:
                        // Electric blue eyes
                        let eyeL = CGRect(x: ecx - es - 2, y: ecy - 2, width: 4, height: 4)
                        let eyeR = CGRect(x: ecx + es - 2, y: ecy - 2, width: 4, height: 4)
                        context.fill(Path(ellipseIn: eyeL), with: .color(Color(red: 0.35, green: 0.65, blue: 1.0)))
                        context.fill(Path(ellipseIn: eyeR), with: .color(Color(red: 0.35, green: 0.65, blue: 1.0)))

                    case .strain:
                        // Downward arc eyes + sweat drop
                        context.stroke(
                            downArcEye(center: CGPoint(x: ecx - es, y: ecy)),
                            with: .color(Color(red: 0.98, green: 0.65, blue: 0.22)),
                            lineWidth: 1.5
                        )
                        context.stroke(
                            downArcEye(center: CGPoint(x: ecx + es, y: ecy)),
                            with: .color(Color(red: 0.98, green: 0.65, blue: 0.22)),
                            lineWidth: 1.5
                        )
                        // Sweat drop
                        let sweat = CGRect(x: sx + sw - 4, y: sy + 1, width: 2.5, height: 3.5)
                        context.fill(Path(ellipseIn: sweat), with: .color(Color(red: 0.35, green: 0.65, blue: 1.0)))

                    case .panic:
                        // Red X eyes vibrating
                        drawXEye(context: context, center: CGPoint(x: ecx - es, y: ecy), color: .red)
                        drawXEye(context: context, center: CGPoint(x: ecx + es, y: ecy), color: .red)

                    case .dead:
                        // Glitched X eyes + drool
                        drawXEye(context: context, center: CGPoint(x: ecx - es, y: ecy), color: .red)
                        drawXEye(context: context, center: CGPoint(x: ecx + es, y: ecy), color: .red)
                        let drool = CGRect(x: ecx + 1, y: ecy + 3, width: 2, height: 4)
                        context.fill(Path(roundedRect: drool, cornerRadius: 1), with: .color(Color.cyan))
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
