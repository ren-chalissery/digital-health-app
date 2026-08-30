// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityAdmin",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityAdmin", targets: ["SimplicityAdmin"])
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
            name: "SimplicityAdmin",
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
            name: "SimplicityAdminTests",
            dependencies: ["SimplicityAdmin", "SimplicityTesting"]
        )
    ],
    swiftLanguageModes: [.v5]
)
