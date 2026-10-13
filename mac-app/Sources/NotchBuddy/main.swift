import AppKit
import SwiftUI

final class AppDelegate: NSObject, NSApplicationDelegate {
    var panel: NSPanel!
    let networkMonitor = NetworkMonitor()
    let cpuMonitor = CPUMonitor()

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory) // Hides from Dock, behaves as menu bar companion

        let notchView = NotchView(networkMonitor: networkMonitor, cpuMonitor: cpuMonitor)
        let hostingView = NSHostingView(rootView: notchView)

        // Create borderless floating panel
        panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 380, height: 260),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: false
        )

        panel.isFloatingPanel = true
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = false
        panel.contentView = hostingView

        positionPanelAtNotch()
        panel.orderFrontRegardless()
    }

    private func positionPanelAtNotch() {
        guard let screen = NSScreen.main else { return }
        let screenFrame = screen.frame

        // Calculate notch or top center positioning
        let panelWidth: CGFloat = 380
        let xPos = screenFrame.origin.x + (screenFrame.width - panelWidth) / 2
        let yPos = screenFrame.origin.y + screenFrame.height - 40

        panel.setFrameOrigin(NSPoint(x: xPos, y: yPos))
    }
}

// Entry Point
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
