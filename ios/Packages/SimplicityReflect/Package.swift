// swift-tools-version: 6.2

import PackageDescription

// Deliberately does not depend on SimplicityAssistant, and nothing depends on this from there.
// Phase 4 promised the assistant never reads a reflection; a package boundary is what holds that
// promise when somebody later asks for "help me reflect on this".
let package = Package(
    name: "SimplicityReflect",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityReflect", targets: ["SimplicityReflect"])
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
            name: "SimplicityReflect",
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
            name: "SimplicityReflectTests",
            dependencies: ["SimplicityReflect", "SimplicityTesting"]
        )
    ],
    swiftLanguageModes: [.v5]
)
