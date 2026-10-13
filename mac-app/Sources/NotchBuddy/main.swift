import AppKit
import SwiftUI

final class AppDelegate: NSObject, NSApplicationDelegate {
    var panel: NSPanel!
    let networkMonitor = NetworkMonitor()
    let cpuMonitor = CPUMonitor()

    let panelWidth: CGFloat = 520
    var isExpanded: Bool = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory) // Hides from Dock, behaves as sleek menu bar companion

        let notchView = NotchView(
            networkMonitor: networkMonitor,
            cpuMonitor: cpuMonitor,
            onToggleExpand: { [weak self] expanded in
                self?.setExpanded(expanded)
            }
        )
        let hostingView = NSHostingView(rootView: notchView)

        // Collapsed height (110pt) covers the notch bar (34pt) and companion (74pt) only
        let initialHeight: CGFloat = 110
        panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: panelWidth, height: initialHeight),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: false
        )

        panel.isFloatingPanel = true
        panel.level = .statusBar // Floats on top of all windows and menu bar
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = false
        panel.contentView = hostingView
        panel.ignoresMouseEvents = false

        positionPanelAtNotch(height: initialHeight)
        panel.orderFrontRegardless()

        // Re-align if display configuration changes
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(screenParametersChanged),
            name: NSApplication.didChangeScreenParametersNotification,
            object: nil
        )
    }

    func setExpanded(_ expanded: Bool) {
        self.isExpanded = expanded
        let targetHeight: CGFloat = expanded ? 580 : 110
        positionPanelAtNotch(height: targetHeight)
    }

    @objc private func screenParametersChanged() {
        let currentHeight: CGFloat = isExpanded ? 580 : 110
        positionPanelAtNotch(height: currentHeight)
    }

    private func positionPanelAtNotch(height: CGFloat) {
        guard let screen = NSScreen.main else { return }
        let screenFrame = screen.frame

        // Center horizontally on display, align flush to top edge
        let xPos = screenFrame.origin.x + (screenFrame.width - panelWidth) / 2
        let yPos = screenFrame.origin.y + screenFrame.height - height

        panel.setFrame(NSRect(x: xPos, y: yPos, width: panelWidth, height: height), display: true)
    }
}

// Entry Point
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
