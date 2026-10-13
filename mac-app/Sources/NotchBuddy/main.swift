import AppKit
import SwiftUI

// Passthrough Hosting View: Clicks on transparent empty background pass straight through to underlying apps (Chrome, Finder, etc.)
final class PassthroughHostingView<Content: View>: NSHostingView<Content> {
    override func hitTest(_ point: NSPoint) -> NSView? {
        let view = super.hitTest(point)
        // If hit is on the root hosting view background rather than an interactive child element, pass through to underlying windows
        if view == self {
            return nil
        }
        return view
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    var panel: NSPanel!
    let networkMonitor = NetworkMonitor()
    let cpuMonitor = CPUMonitor()

    let panelWidth: CGFloat = 850
    let panelHeight: CGFloat = 620

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory) // Hides from Dock, behaves as sleek menu bar companion

        let notchView = NotchView(networkMonitor: networkMonitor, cpuMonitor: cpuMonitor)
        let hostingView = PassthroughHostingView(rootView: notchView)

        // Create borderless floating panel
        panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: panelWidth, height: panelHeight),
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

        positionPanelAtNotch()
        panel.orderFrontRegardless()

        // Re-align if display configuration changes
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(screenParametersChanged),
            name: NSApplication.didChangeScreenParametersNotification,
            object: nil
        )
    }

    @objc private func screenParametersChanged() {
        positionPanelAtNotch()
    }

    private func positionPanelAtNotch() {
        guard let screen = NSScreen.main else { return }
        let screenFrame = screen.frame

        // Center horizontally on display, align flush to top edge
        let xPos = screenFrame.origin.x + (screenFrame.width - panelWidth) / 2
        let yPos = screenFrame.origin.y + screenFrame.height - panelHeight

        panel.setFrame(NSRect(x: xPos, y: yPos, width: panelWidth, height: panelHeight), display: true)
    }
}

// Entry Point
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
