import Foundation
import Darwin

/// Real macOS per-process bandwidth record
public struct AppProcessBandwidth: Identifiable, Equatable {
    public let id = UUID()
    public let name: String
    public let icon: String
    public let speedFormatted: String
    public let rawSpeed: Double
    public let isHighLoad: Bool

    public static func == (lhs: AppProcessBandwidth, rhs: AppProcessBandwidth) -> Bool {
        lhs.name == rhs.name && lhs.speedFormatted == rhs.speedFormatted
    }
}

/// Zero-latency network telemetry using BSD getifaddrs() C API & real nettop process accounting
public final class NetworkMonitor: ObservableObject {
    @Published public private(set) var downloadSpeed: Double = 0.0 // Bytes / sec
    @Published public private(set) var uploadSpeed: Double = 0.0   // Bytes / sec
    @Published public private(set) var peakDownloadSpeed: Double = 0.0
    @Published public private(set) var peakUploadSpeed: Double = 0.0
    @Published public private(set) var sessionBytesDownloaded: UInt64 = 0
    @Published public private(set) var sessionBytesUploaded: UInt64 = 0
    @Published public private(set) var localIPAddress: String = "127.0.0.1"
    @Published public private(set) var activeInterfaceName: String = "en0 (Wi-Fi)"
    @Published public private(set) var topProcesses: [AppProcessBandwidth] = []
    @Published public private(set) var isSpeedtesting: Bool = false

    private var previousInBytes: UInt64 = 0
    private var previousOutBytes: UInt64 = 0
    private var timer: DispatchSourceTimer?
    private var processTimer: DispatchSourceTimer?
    private let queue = DispatchQueue(label: "com.notchbuddy.networkmonitor", qos: .utility)
    private let processQueue = DispatchQueue(label: "com.notchbuddy.processmonitor", qos: .background)

    // Historical process byte accounting for delta calculations
    private var previousProcessTotals: [String: (bytes: UInt64, time: Date)] = [:]

    public init() {
        startMonitoring()
        detectLocalNetworkInfo()
        sampleProcesses() // Initial real process sample
    }

    deinit {
        stopMonitoring()
    }

    public func startMonitoring() {
        let (initialIn, initialOut) = readTotalInterfaceBytes()
        previousInBytes = initialIn
        previousOutBytes = initialOut

        // 1.0s interface throughput sampler
        timer = DispatchSource.makeTimerSource(queue: queue)
        timer?.schedule(deadline: .now() + 1.0, repeating: 1.0)
        timer?.setEventHandler { [weak self] in
            self?.sample()
        }
        timer?.resume()

        // 2.0s real macOS nettop per-process sampler
        processTimer = DispatchSource.makeTimerSource(queue: processQueue)
        processTimer?.schedule(deadline: .now() + 2.0, repeating: 2.0)
        processTimer?.setEventHandler { [weak self] in
            self?.sampleProcesses()
        }
        processTimer?.resume()
    }

    public func stopMonitoring() {
        timer?.cancel()
        timer = nil
        processTimer?.cancel()
        processTimer = nil
    }

    private func sample() {
        let (currentIn, currentOut) = readTotalInterfaceBytes()

        let deltaIn = currentIn >= previousInBytes ? (currentIn - previousInBytes) : 0
        let deltaOut = currentOut >= previousOutBytes ? (currentOut - previousOutBytes) : 0

        previousInBytes = currentIn
        previousOutBytes = currentOut

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let down = (self.downloadSpeed * 0.3) + (Double(deltaIn) * 0.7)
            let up = (self.uploadSpeed * 0.3) + (Double(deltaOut) * 0.7)
            self.downloadSpeed = down
            self.uploadSpeed = up
            if down > self.peakDownloadSpeed { self.peakDownloadSpeed = down }
            if up > self.peakUploadSpeed { self.peakUploadSpeed = up }
            self.sessionBytesDownloaded += deltaIn
            self.sessionBytesUploaded += deltaOut
        }
    }

    /// Read global hardware network bytes from BSD getifaddrs()
    private func readTotalInterfaceBytes() -> (UInt64, UInt64) {
        var ifap: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifap) == 0, let first = ifap else {
            return (0, 0)
        }
        defer { freeifaddrs(ifap) }

        var totalIn: UInt64 = 0
        var totalOut: UInt64 = 0

        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let current = cursor {
            let name = String(cString: current.pointee.ifa_name)
            if !name.hasPrefix("lo") && current.pointee.ifa_addr.pointee.sa_family == UInt8(AF_LINK) {
                if let data = current.pointee.ifa_data {
                    let networkData = data.assumingMemoryBound(to: if_data.self)
                    totalIn += UInt64(networkData.pointee.ifi_ibytes)
                    totalOut += UInt64(networkData.pointee.ifi_obytes)
                }
            }
            cursor = current.pointee.ifa_next
        }

        return (totalIn, totalOut)
    }

    /// Detect real local IP address and primary active interface
    public func detectLocalNetworkInfo() {
        var ifap: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifap) == 0, let first = ifap else { return }
        defer { freeifaddrs(ifap) }

        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let current = cursor {
            let name = String(cString: current.pointee.ifa_name)
            // Primary network interface is usually en0 (Wi-Fi on MacBooks)
            if (name == "en0" || name == "en1") && current.pointee.ifa_addr.pointee.sa_family == UInt8(AF_INET) {
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                let saLen = socklen_t(current.pointee.ifa_addr.pointee.sa_len)
                if getnameinfo(current.pointee.ifa_addr, saLen, &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST) == 0 {
                    let ip = String(cString: hostname)
                    DispatchQueue.main.async { [weak self] in
                        self?.localIPAddress = ip
                        self?.activeInterfaceName = "\(name) (Wi-Fi)"
                    }
                    return
                }
            }
            cursor = current.pointee.ifa_next
        }
    }

    /// Real per-process bandwidth accounting using macOS nettop
    private func sampleProcesses() {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/nettop")
        process.arguments = ["-P", "-L", "1", "-J", "bytes_in,bytes_out"]

        let pipe = Pipe()
        process.standardOutput = pipe

        do {
            try process.run()
            process.waitUntilExit()

            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            guard let output = String(data: data, encoding: .utf8) else { return }

            let now = Date()
            var currentSnapshot: [String: UInt64] = [:]
            var deltas: [(name: String, speed: Double, total: UInt64)] = []

            let lines = output.split(separator: "\n")
            for line in lines.dropFirst() { // skip header
                let parts = line.split(separator: ",", omittingEmptySubsequences: false)
                if parts.count >= 3 {
                    let rawProc = String(parts[0])
                    let inBytes = UInt64(parts[1]) ?? 0
                    let outBytes = UInt64(parts[2]) ?? 0
                    let totalBytes = inBytes + outBytes

                    // Clean name by removing PID suffix
                    let cleanName = cleanProcessName(rawProc)
                    if cleanName.isEmpty || cleanName == "syslogd" { continue }

                    currentSnapshot[cleanName] = totalBytes

                    if let prev = previousProcessTotals[cleanName] {
                        let dt = now.timeIntervalSince(prev.time)
                        if dt > 0.5 && totalBytes >= prev.bytes {
                            let speed = Double(totalBytes - prev.bytes) / dt
                            deltas.append((name: cleanName, speed: speed, total: totalBytes))
                        }
                    } else {
                        deltas.append((name: cleanName, speed: 0.0, total: totalBytes))
                    }
                }
            }

            for (k, v) in currentSnapshot {
                previousProcessTotals[k] = (bytes: v, time: now)
            }

            // Sort: actively transferring processes first, then by total traffic
            let sorted = deltas.sorted {
                if $0.speed > 0 || $1.speed > 0 {
                    return $0.speed > $1.speed
                }
                return $0.total > $1.total
            }

            let top4 = Array(sorted.prefix(4)).map { item -> AppProcessBandwidth in
                let speedText = item.speed > 100
                    ? NetworkMonitor.formatSpeed(item.speed).value + " " + NetworkMonitor.formatSpeed(item.speed).unit
                    : (item.total > 1024 * 1024
                        ? String(format: "%.1f MB", Double(item.total) / (1024 * 1024))
                        : "Active")
                return AppProcessBandwidth(
                    name: item.name,
                    icon: iconForProcess(item.name),
                    speedFormatted: speedText,
                    rawSpeed: item.speed,
                    isHighLoad: item.speed > 1024 * 1024
                )
            }

            DispatchQueue.main.async { [weak self] in
                if !top4.isEmpty {
                    self?.topProcesses = top4
                }
            }
        } catch {
            // Fallback gracefully
        }
    }

    private func cleanProcessName(_ raw: String) -> String {
        var base = raw
        if let dotIndex = base.lastIndex(of: ".") {
            base = String(base[..<dotIndex])
        }
        if base.hasPrefix("Google Chrome") { return "Google Chrome" }
        if base.hasPrefix("Safari") { return "Safari" }
        if base.hasPrefix("Code") || base.hasPrefix("Electron") { return "VS Code" }
        if base.hasPrefix("antigravity") { return "Antigravity IDE" }
        if base.hasPrefix("Discord") { return "Discord" }
        if base.hasPrefix("Spotify") { return "Spotify" }
        if base.hasPrefix("Steam") { return "Steam Client" }
        if base.hasPrefix("Slack") { return "Slack" }
        if base == "mDNSResponder" { return "DNS Service" }
        if base == "nsurlsessiond" { return "System Network" }
        if base == "cloudd" { return "iCloud Sync" }
        if base == "adb" { return "Android Bridge" }
        return base
    }

    private func iconForProcess(_ name: String) -> String {
        switch name {
        case "Google Chrome": return "🌐"
        case "Safari":        return "🧭"
        case "VS Code", "Antigravity IDE": return "💻"
        case "Discord":       return "🎧"
        case "Spotify":       return "🎵"
        case "Steam Client":  return "🎮"
        case "Slack":         return "💬"
        case "iCloud Sync":   return "☁️"
        case "DNS Service":   return "📡"
        case "System Network":return "⚙️"
        case "Android Bridge":return "📱"
        default:              return "⚡"
        }
    }

    /// Live Network Speed Test via URLSession to measure real live throughput
    public func runSpeedTest(completion: @escaping () -> Void) {
        guard !isSpeedtesting else { return }
        isSpeedtesting = true

        guard let url = URL(string: "https://speed.cloudflare.com/__down?bytes=20000000") else {
            isSpeedtesting = false
            completion()
            return
        }

        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        let session = URLSession(configuration: config)

        let task = session.dataTask(with: url) { [weak self] _, _, _ in
            DispatchQueue.main.async {
                self?.isSpeedtesting = false
                completion()
            }
        }
        task.resume()
    }

    /// Formats raw bytes per second into human-readable strings (KB/s, MB/s, GB/s)
    public static func formatSpeed(_ bytesPerSec: Double) -> (value: String, unit: String) {
        if bytesPerSec >= 1024 * 1024 * 1024 {
            return (String(format: "%.2f", bytesPerSec / (1024 * 1024 * 1024)), "GB/s")
        } else if bytesPerSec >= 1024 * 1024 {
            return (String(format: "%.1f", bytesPerSec / (1024 * 1024)), "MB/s")
        } else if bytesPerSec >= 1024 {
            return (String(format: "%.0f", bytesPerSec / 1024), "KB/s")
        } else {
            return (String(format: "%.0f", bytesPerSec), "B/s")
        }
    }
}
