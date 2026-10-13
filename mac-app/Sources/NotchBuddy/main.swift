import AppKit
import SwiftUI

// Shape-Aware Hosting View: Intelligently accepts clicks ONLY on the actual visible bar and Glitch,
// while letting all clicks on the empty space (where Chrome tabs are) pass straight through to background apps!
final class ShapeHitTestHostingView<Content: View>: NSHostingView<Content> {
    var isExpanded: Bool = false

    override func hitTest(_ point: NSPoint) -> NSView? {
        let h = bounds.height
        let w = bounds.width

        // 1. Top Notch Bar (Full 520pt width, top 36pt flush with menu bar / notch)
        if point.y >= h - 36 && point.y <= h && point.x >= 0 && point.x <= w {
            return super.hitTest(point)
        }

        // 2. Glitch Ghost (Centered 80pt wide below notch bar)
        let ghostLeft = (w - 84) / 2
        let ghostRight = (w + 84) / 2
        let ghostBottom = h - 110
        if !isExpanded && point.x >= ghostLeft && point.x <= ghostRight && point.y >= ghostBottom && point.y < h - 36 {
            return super.hitTest(point)
        }

        // 3. Expanded HUD Popover (When opened)
        if isExpanded {
            let hudLeft = (w - 446) / 2
            let hudRight = (w + 446) / 2
            if point.x >= hudLeft && point.x <= hudRight && point.y >= 0 && point.y < h - 36 {
                return super.hitTest(point)
            }
        }

        // 4. Everything else (including the spaces to the left and right of Glitch where Chrome tabs sit)
        // returns nil, which AppKit translates into instant passthrough clicks to underlying windows!
        return nil
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    var panel: NSPanel!
    var hostingView: ShapeHitTestHostingView<NotchView>!
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
        hostingView = ShapeHitTestHostingView(rootView: notchView)

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
        hostingView?.isExpanded = expanded
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
