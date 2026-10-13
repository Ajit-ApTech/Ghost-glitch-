import AppKit
import SwiftUI

final class AppDelegate: NSObject, NSApplicationDelegate {
    var barPanel: NSPanel!
    var ghostPanel: NSPanel!
    var hudPanel: NSPanel!

    let networkMonitor = NetworkMonitor()
    let cpuMonitor = CPUMonitor()
    let appSettings = AppSettings.shared

    let barWidth: CGFloat = 480
    let barHeight: CGFloat = 34

    let ghostWidth: CGFloat = 80
    let ghostHeight: CGFloat = 74

    let hudWidth: CGFloat = 440
    let hudHeight: CGFloat = 480

    var isHUDOpen = false
    var globalEventMonitor: Any?
    var localEventMonitor: Any?

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory) // Hides from Dock, behaves as menu bar companion

        setupBarPanel()
        setupGhostPanel()
        setupHUDPanel()

        // Re-align panels on screen configuration changes
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(screenParametersChanged),
            name: NSApplication.didChangeScreenParametersNotification,
            object: nil
        )
    }

    // ── 1. Top Notch Bar Panel (Strictly 34pt tall, never covers Chrome tabs) ──
    private func setupBarPanel() {
        guard let screen = NSScreen.main else { return }
        let sf = screen.frame

        let barView = NotchBarView(
            networkMonitor: networkMonitor,
            cpuMonitor: cpuMonitor,
            appSettings: appSettings,
            onToggleHUD: { [weak self] in
                self?.toggleHUD()
            }
        )
        let hostingView = NSHostingView(rootView: barView)

        let xPos = sf.origin.x + (sf.width - barWidth) / 2
        let yPos = sf.origin.y + sf.height - barHeight

        barPanel = NSPanel(
            contentRect: NSRect(x: xPos, y: yPos, width: barWidth, height: barHeight),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: false
        )
        barPanel.isFloatingPanel = true
        barPanel.level = .statusBar
        barPanel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        barPanel.backgroundColor = .clear
        barPanel.isOpaque = false
        barPanel.hasShadow = false
        barPanel.contentView = hostingView

        barPanel.orderFrontRegardless()
    }

    // ── 2. Glitch Companion Panel (Strictly 80pt wide, leaves all Chrome tabs 100% open) ──
    private func setupGhostPanel() {
        guard let screen = NSScreen.main else { return }
        let sf = screen.frame

        let ghostView = GhostCompanionView(
            networkMonitor: networkMonitor,
            cpuMonitor: cpuMonitor,
            appSettings: appSettings
        )
        let hostingView = NSHostingView(rootView: ghostView)

        let xPos = sf.origin.x + (sf.width - ghostWidth) / 2
        let yPos = sf.origin.y + sf.height - barHeight - ghostHeight

        ghostPanel = NSPanel(
            contentRect: NSRect(x: xPos, y: yPos, width: ghostWidth, height: ghostHeight),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: false
        )
        ghostPanel.isFloatingPanel = true
        ghostPanel.level = .statusBar
        ghostPanel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        ghostPanel.backgroundColor = .clear
        ghostPanel.isOpaque = false
        ghostPanel.hasShadow = false
        ghostPanel.contentView = hostingView

        ghostPanel.orderFrontRegardless()
    }

    // ── 3. Expanded HUD Dropdown Panel (Only visible when HUD is open) ──
    private func setupHUDPanel() {
        guard let screen = NSScreen.main else { return }
        let sf = screen.frame

        let hudView = NotchHUDView(
            networkMonitor: networkMonitor,
            cpuMonitor: cpuMonitor,
            appSettings: appSettings,
            onClose: { [weak self] in
                self?.closeHUD()
            }
        )
        let hostingView = NSHostingView(rootView: hudView)

        let xPos = sf.origin.x + (sf.width - hudWidth) / 2
        let yPos = sf.origin.y + sf.height - barHeight - hudHeight

        hudPanel = NSPanel(
            contentRect: NSRect(x: xPos, y: yPos, width: hudWidth, height: hudHeight),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: false
        )
        hudPanel.isFloatingPanel = true
        hudPanel.level = .statusBar
        hudPanel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        hudPanel.backgroundColor = .clear
        hudPanel.isOpaque = false
        hudPanel.hasShadow = false
        hudPanel.contentView = hostingView
    }

    // ── HUD Toggle & Click-Outside Auto-Close ──
    func toggleHUD() {
        if isHUDOpen {
            closeHUD()
        } else {
            openHUD()
        }
    }

    func openHUD() {
        isHUDOpen = true
        alignPanels()
        ghostPanel.orderOut(nil) // Glitch is displayed in the HUD badge
        hudPanel.orderFrontRegardless()

        // Auto-close when clicking outside the HUD dropdown!
        globalEventMonitor = NSEvent.addGlobalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { [weak self] event in
            guard let self = self, self.isHUDOpen else { return }
            let clickLoc = NSEvent.mouseLocation
            // If click is outside the HUD panel frame and outside the notch bar, close!
            if !self.hudPanel.frame.contains(clickLoc) && !self.barPanel.frame.contains(clickLoc) {
                DispatchQueue.main.async {
                    self.closeHUD()
                }
            }
        }

        localEventMonitor = NSEvent.addLocalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { [weak self] event in
            guard let self = self, self.isHUDOpen else { return event }
            let clickLoc = NSEvent.mouseLocation
            if !self.hudPanel.frame.contains(clickLoc) && !self.barPanel.frame.contains(clickLoc) {
                DispatchQueue.main.async {
                    self.closeHUD()
                }
            }
            return event
        }
    }

    func closeHUD() {
        isHUDOpen = false
        hudPanel.orderOut(nil)
        ghostPanel.orderFrontRegardless()

        if let gem = globalEventMonitor {
            NSEvent.removeMonitor(gem)
            globalEventMonitor = nil
        }
        if let lem = localEventMonitor {
            NSEvent.removeMonitor(lem)
            localEventMonitor = nil
        }
    }

    @objc private func screenParametersChanged() {
        alignPanels()
    }

    private func alignPanels() {
        guard let screen = NSScreen.main else { return }
        let sf = screen.frame

        let barX = sf.origin.x + (sf.width - barWidth) / 2
        let barY = sf.origin.y + sf.height - barHeight
        barPanel.setFrame(NSRect(x: barX, y: barY, width: barWidth, height: barHeight), display: true)

        let ghostX = sf.origin.x + (sf.width - ghostWidth) / 2
        let ghostY = sf.origin.y + sf.height - barHeight - ghostHeight
        ghostPanel.setFrame(NSRect(x: ghostX, y: ghostY, width: ghostWidth, height: ghostHeight), display: true)

        let hudX = sf.origin.x + (sf.width - hudWidth) / 2
        let hudY = sf.origin.y + sf.height - barHeight - hudHeight
        hudPanel.setFrame(NSRect(x: hudX, y: hudY, width: hudWidth, height: hudHeight), display: true)
    }
}

// Entry Point
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
