// swift-tools-version: 6.2

import PackageDescription

// Deliberately does not depend on SimplicityReflect. Phase 4 promised the assistant never reads a
// reflection, and a package boundary is what holds that promise when somebody later asks for
// "help me reflect on this" — the compiler refuses rather than a reviewer having to notice.
let package = Package(
    name: "SimplicityAssistant",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityAssistant", targets: ["SimplicityAssistant"])
    ],
    dependencies: [
        .package(path: "../SimplicityApi"),
        .package(path: "../SimplicityDesign"),
        .package(path: "../SimplicityFoundation"),
        .package(path: "../SimplicityServices"),
        .package(path: "../SimplicityTesting"),
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0")),
        .package(url: "https://github.com/Kolos65/Mockable.git", exact: "0.5.0")
    ],
    targets: [
        .target(
            name: "SimplicityAssistant",
            dependencies: [
                "SimplicityApi",
                "SimplicityDesign",
                "SimplicityFoundation",
                "SimplicityServices",
                .product(name: "Factory", package: "Factory"),
                .product(name: "Mockable", package: "Mockable")
            ],
            resources: [.process("Resources")],
            swiftSettings: [.define("MOCKING", .when(configuration: .debug))]
        ),
        .testTarget(
            name: "SimplicityAssistantTests",
            dependencies: ["SimplicityAssistant", "SimplicityTesting"]
        )
    ],
    swiftLanguageModes: [.v5]
)
