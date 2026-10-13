import SwiftUI

public enum CompanionPerformanceTier {
    case idle       // 0-15% Napping
    case low        // 16-40% Waking up / Yawning
    case moderate   // 41-65% Walking
    case high       // 66-85% Sprinting
    case critical   // 86-98% Strain / Overheat
    case peak       // 99-100% Melted Pancake
}

public struct NotchView: View {
    @ObservedObject var networkMonitor: NetworkMonitor
    @ObservedObject var cpuMonitor: CPUMonitor
    @State private var isExpanded = false
    @State private var isPetted = false

    public init(networkMonitor: NetworkMonitor, cpuMonitor: CPUMonitor) {
        self.networkMonitor = networkMonitor
        self.cpuMonitor = cpuMonitor
    }

    public var currentTier: CompanionPerformanceTier {
        let cpu = cpuMonitor.cpuUsagePercentage
        let downMb = networkMonitor.downloadSpeed / (1024 * 1024)

        if cpu >= 99 { return .peak }
        if cpu >= 86 || downMb >= 80 { return .critical }
        if cpu >= 66 || downMb >= 40 { return .high }
        if cpu >= 41 || downMb >= 10 { return .moderate }
        if cpu >= 16 || downMb >= 1 { return .low }
        return .idle
    }

    public var body: some View {
        VStack(spacing: 8) {
            // Center Notch Pill Only
            HStack(spacing: 8) {
                // Left Wing: Live Network Speeds
                let down = NetworkMonitor.formatSpeed(networkMonitor.downloadSpeed)
                let up = NetworkMonitor.formatSpeed(networkMonitor.uploadSpeed)

                HStack(spacing: 3) {
                    Text("↓")
                        .foregroundColor(.green)
                        .fontWeight(.bold)
                    Text("\(down.value) \(down.unit)")
                        .foregroundColor(.white)
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                }

                HStack(spacing: 3) {
                    Text("↑")
                        .foregroundColor(.blue)
                        .fontWeight(.bold)
                    Text("\(up.value) \(up.unit)")
                        .foregroundColor(.white)
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                }

                // Center Notch Spacer
                Spacer()
                    .frame(width: 140)

                // Right Wing: Latency & CPU
                Circle()
                    .fill(pingColor)
                    .frame(width: 6, height: 6)
                    .shadow(color: pingColor, radius: 3)

                Text("CPU \(Int(cpuMonitor.cpuUsagePercentage))%")
                    .foregroundColor(.purple)
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))

                // Living Procedural Calico Cat perched safely in the visible notch area
                LivingCatView(tier: currentTier, isPetted: isPetted)
                    .frame(width: 58, height: 42)
                    .offset(y: 2)
                    .shadow(color: Color.black.opacity(0.8), radius: 6, x: 0, y: 3)
                    .scaleEffect(isPetted ? 1.25 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.5), value: isPetted)
                    .onTapGesture {
                        withAnimation { isPetted = true }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { isPetted = false }
                    }
            }
            .padding(.horizontal, 14)
            .frame(height: 38)
            .background(Color.black)
            .cornerRadius(18)
            .onTapGesture {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                    isExpanded.toggle()
                }
            }

            // Expandable Liquid Glass Notch HUD
            if isExpanded {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        LivingCatView(tier: currentTier, isPetted: isPetted)
                            .frame(width: 44, height: 44)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(12)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Mochi the Living Calico")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                            Text(tierDescription)
                                .font(.system(size: 10.5))
                                .foregroundColor(.green)
                        }
                        Spacer()
                        Text("16 ms · Ultra Low")
                            .font(.system(size: 10, weight: .semibold, design: .monospaced))
                            .foregroundColor(.green)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.green.opacity(0.15))
                            .cornerRadius(12)
                    }

                    Divider().background(Color.white.opacity(0.1))

                    HStack(spacing: 16) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("DOWNLOAD")
                                .font(.system(size: 8.5, weight: .bold))
                                .foregroundColor(.gray)
                            let d = NetworkMonitor.formatSpeed(networkMonitor.downloadSpeed)
                            Text("↓ \(d.value) \(d.unit)")
                                .font(.system(size: 15, weight: .bold, design: .monospaced))
                                .foregroundColor(.green)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("UPLOAD")
                                .font(.system(size: 8.5, weight: .bold))
                                .foregroundColor(.gray)
                            let u = NetworkMonitor.formatSpeed(networkMonitor.uploadSpeed)
                            Text("↑ \(u.value) \(u.unit)")
                                .font(.system(size: 15, weight: .bold, design: .monospaced))
                                .foregroundColor(.blue)
                        }
                    }
                }
                .padding(16)
                .frame(width: 340)
                .background(.ultraThinMaterial)
                .cornerRadius(20)
                .shadow(color: .black.opacity(0.6), radius: 24)
                .transition(.scale(scale: 0.95).combined(with: .opacity))
            }
        }
    }

    private var tierDescription: String {
        if isPetted { return "Loving the pets! 💖" }
        switch currentTier {
        case .idle: return "Idle Load (0–15%) · Napping"
        case .low: return "Low Load (16–40%) · Waking up"
        case .moderate: return "Moderate Load (41–65%) · Steady pace"
        case .high: return "High Load (66–85%) · Treadmill sprint"
        case .critical: return "Critical Load (86–98%) · Need cool-down!"
        case .peak: return "Peak Load (99–100%) · Overheat stall"
        }
    }

    private var pingColor: Color {
        switch currentTier {
        case .critical, .peak: return .red
        case .high: return .orange
        default: return .green
        }
    }
}

// ==============================================================
// NATIVE PROCEDURAL LIVING CAT (METAL-ACCELERATED SWIFTUI CANVAS)
// ==============================================================
public struct LivingCatView: View {
    public let tier: CompanionPerformanceTier
    public let isPetted: Bool

    public var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let time = timeline.date.timeIntervalSinceReferenceDate
                let w = size.width
                let h = size.height

                // Draw procedural vector calico cat
                let midX = w / 2
                let midY = h / 2

                switch tier {
                case .idle:
                    let breath = sin(time * 2.2) * 1.5
                    // Curled white body
                    let bodyRect = CGRect(x: midX - 18, y: midY - 10 + breath, width: 36, height: 24)
                    context.fill(Path(ellipseIn: bodyRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: bodyRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    // Orange calico patch
                    let patchRect = CGRect(x: midX - 4, y: midY - 12 + breath, width: 16, height: 14)
                    context.fill(Path(ellipseIn: patchRect), with: .color(.orange))

                    // Sleeping head
                    let headRect = CGRect(x: midX - 22, y: midY - 6 + breath, width: 20, height: 18)
                    context.fill(Path(ellipseIn: headRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: headRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                case .low:
                    let breath = sin(time * 3.0) * 1.0
                    let bodyRect = CGRect(x: midX - 14, y: midY - 6 + breath, width: 28, height: 22)
                    context.fill(Path(ellipseIn: bodyRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: bodyRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    let headRect = CGRect(x: midX - 12, y: midY - 18 + breath, width: 24, height: 22)
                    context.fill(Path(ellipseIn: headRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: headRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    let patchRect = CGRect(x: midX, y: midY - 20 + breath, width: 12, height: 12)
                    context.fill(Path(ellipseIn: patchRect), with: .color(.orange))

                case .moderate:
                    let step = sin(time * 8.0) * 2.0
                    let bodyRect = CGRect(x: midX - 16, y: midY - 10 + abs(step), width: 32, height: 20)
                    context.fill(Path(ellipseIn: bodyRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: bodyRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    let patchRect = CGRect(x: midX - 2, y: midY - 12 + abs(step), width: 14, height: 12)
                    context.fill(Path(ellipseIn: patchRect), with: .color(.orange))

                    let headRect = CGRect(x: midX - 20, y: midY - 16 + abs(step), width: 20, height: 18)
                    context.fill(Path(ellipseIn: headRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: headRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                case .high:
                    let bounce = abs(sin(time * 16.0)) * 2.5
                    // Treadmill track
                    let trackRect = CGRect(x: midX - 24, y: midY + 12, width: 48, height: 6)
                    context.fill(Path(roundedRect: trackRect, cornerRadius: 3), with: .color(Color(white: 0.2)))

                    // Sprinting body
                    let bodyRect = CGRect(x: midX - 16, y: midY - 8 - bounce, width: 32, height: 18)
                    context.fill(Path(ellipseIn: bodyRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: bodyRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    let patchRect = CGRect(x: midX - 4, y: midY - 10 - bounce, width: 14, height: 12)
                    context.fill(Path(ellipseIn: patchRect), with: .color(.orange))

                case .critical:
                    let bounce = abs(sin(time * 24.0)) * 3.5
                    // Treadmill track
                    let trackRect = CGRect(x: midX - 24, y: midY + 12, width: 48, height: 6)
                    context.fill(Path(roundedRect: trackRect, cornerRadius: 3), with: .color(Color(white: 0.2)))

                    // Flushed red body
                    let bodyRect = CGRect(x: midX - 16, y: midY - 8 - bounce, width: 32, height: 18)
                    context.fill(Path(ellipseIn: bodyRect), with: .color(Color(red: 1.0, green: 0.8, blue: 0.8)))
                    context.stroke(Path(ellipseIn: bodyRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    let patchRect = CGRect(x: midX - 4, y: midY - 10 - bounce, width: 14, height: 12)
                    context.fill(Path(ellipseIn: patchRect), with: .color(.red))

                case .peak:
                    let melt = sin(time * 1.5) * 1.0
                    // Melted pancake body
                    let meltRect = CGRect(x: midX - 24, y: midY + 2, width: 48 + melt, height: 12 - melt)
                    context.fill(Path(ellipseIn: meltRect), with: .color(.white))
                    context.stroke(Path(ellipseIn: meltRect), with: .color(Color(white: 0.1)), lineWidth: 1.5)

                    let patchRect = CGRect(x: midX - 2, y: midY + 2, width: 18, height: 8)
                    context.fill(Path(ellipseIn: patchRect), with: .color(.orange))
                }
            }
        }
    }
}
