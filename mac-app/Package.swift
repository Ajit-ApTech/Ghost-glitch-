// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NotchBuddy",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "NotchBuddy", targets: ["NotchBuddy"])
    ],
    targets: [
        .executableTarget(
            name: "NotchBuddy",
            path: "Sources/NotchBuddy"
        )
    ]
)
