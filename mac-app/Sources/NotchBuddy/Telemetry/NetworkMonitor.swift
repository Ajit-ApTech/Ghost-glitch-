import Foundation
import Darwin

/// Zero-latency network telemetry using BSD getifaddrs() C API
public final class NetworkMonitor: ObservableObject {
    @Published public private(set) var downloadSpeed: Double = 0.0 // Bytes / sec
    @Published public private(set) var uploadSpeed: Double = 0.0   // Bytes / sec
    @Published public private(set) var sessionBytesDownloaded: UInt64 = 0
    @Published public private(set) var sessionBytesUploaded: UInt64 = 0

    private var previousInBytes: UInt64 = 0
    private var previousOutBytes: UInt64 = 0
    private var timer: DispatchSourceTimer?
    private let queue = DispatchQueue(label: "com.notchbuddy.networkmonitor", qos: .utility)

    public init() {
        startMonitoring()
    }

    deinit {
        stopMonitoring()
    }

    public func startMonitoring() {
        // Sample initial counters
        let (initialIn, initialOut) = readTotalInterfaceBytes()
        previousInBytes = initialIn
        previousOutBytes = initialOut

        timer = DispatchSource.makeTimerSource(queue: queue)
        timer?.schedule(deadline: .now() + 1.0, repeating: 1.0)
        timer?.setEventHandler { [weak self] in
            self?.sample()
        }
        timer?.resume()
    }

    public func stopMonitoring() {
        timer?.cancel()
        timer = nil
    }

    private func sample() {
        let (currentIn, currentOut) = readTotalInterfaceBytes()

        let deltaIn = currentIn >= previousInBytes ? (currentIn - previousInBytes) : 0
        let deltaOut = currentOut >= previousOutBytes ? (currentOut - previousOutBytes) : 0

        previousInBytes = currentIn
        previousOutBytes = currentOut

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            // Exponential moving average for smooth display without jitter
            self.downloadSpeed = (self.downloadSpeed * 0.3) + (Double(deltaIn) * 0.7)
            self.uploadSpeed = (self.uploadSpeed * 0.3) + (Double(deltaOut) * 0.7)
            self.sessionBytesDownloaded += deltaIn
            self.sessionBytesUploaded += deltaOut
        }
    }

    /// Iterates over BSD network interfaces via getifaddrs()
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
            // Primary network interfaces: en0 (Wi-Fi), en1, etc., ignore loopback (lo0)
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

    /// Formats raw bytes per second into human-readable strings (KB/s, MB/s, GB/s)
    public static func formatSpeed(_ bytesPerSec: Double) -> (value: String, unit: String) {
        if bytesPerSec >= 1024 * 1024 * 1024 {
            return (String(format: "%.2f", bytesPerSec / (1024 * 1024 * 1024)), "GB/s")
        } else if bytesPerSec >= 1024 * 1024 {
            return (String(format: "%.1f", bytesPerSec / (1024 * 1024)), "MB/s")
        } else {
            return (String(format: "%.1f", bytesPerSec / 1024), "KB/s")
        }
    }
}
