import Foundation
import Darwin

/// Zero-lag CPU usage monitoring using Mach Kernel host_processor_info()
public final class CPUMonitor: ObservableObject {
    @Published public private(set) var cpuUsagePercentage: Double = 0.0

    private var previousCpuInfo: processor_info_array_t?
    private var previousNumCpuInfo: mach_msg_type_number_t = 0
    private var numCPUsU: natural_t = 0
    private var timer: DispatchSourceTimer?
    private let queue = DispatchQueue(label: "com.notchbuddy.cpumonitor", qos: .utility)

    public init() {
        var mib: [Int32] = [CTL_HW, HW_NCPU]
        var sizeOfNumCPUs: size_t = MemoryLayout<natural_t>.size
        let status = sysctl(&mib, 2, &numCPUsU, &sizeOfNumCPUs, nil, 0)
        if status != 0 {
            numCPUsU = 1
        }
        startMonitoring()
    }

    deinit {
        stopMonitoring()
    }

    public func startMonitoring() {
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
        var numCPUInfo: mach_msg_type_number_t = 0
        var cpuInfo: processor_info_array_t?

        let result = host_processor_info(
            mach_host_self(),
            PROCESSOR_CPU_LOAD_INFO,
            &numCPUsU,
            &cpuInfo,
            &numCPUInfo
        )

        guard result == KERN_SUCCESS, let cpuInfo = cpuInfo else { return }

        var inUse: Int32 = 0
        var total: Int32 = 0

        if let prevCpuInfo = previousCpuInfo {
            for i in 0..<Int(numCPUsU) {
                let base = Int(CPU_STATE_MAX) * i
                let user = cpuInfo[base + Int(CPU_STATE_USER)] - prevCpuInfo[base + Int(CPU_STATE_USER)]
                let system = cpuInfo[base + Int(CPU_STATE_SYSTEM)] - prevCpuInfo[base + Int(CPU_STATE_SYSTEM)]
                let nice = cpuInfo[base + Int(CPU_STATE_NICE)] - prevCpuInfo[base + Int(CPU_STATE_NICE)]
                let idle = cpuInfo[base + Int(CPU_STATE_IDLE)] - prevCpuInfo[base + Int(CPU_STATE_IDLE)]

                let coreTotal = user + system + nice + idle
                let coreInUse = user + system + nice

                inUse += coreInUse
                total += coreTotal
            }
        }

        if let prev = previousCpuInfo {
            let prevSize = vm_size_t(previousNumCpuInfo) * vm_size_t(MemoryLayout<integer_t>.stride)
            vm_deallocate(mach_task_self_, vm_address_t(bitPattern: prev), prevSize)
        }

        previousCpuInfo = cpuInfo
        previousNumCpuInfo = numCPUInfo

        let usage = total > 0 ? (Double(inUse) / Double(total)) * 100.0 : 0.0

        DispatchQueue.main.async { [weak self] in
            self?.cpuUsagePercentage = usage
        }
    }
}
