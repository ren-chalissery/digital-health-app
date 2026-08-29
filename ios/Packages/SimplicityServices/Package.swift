// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityServices",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityServices", targets: ["SimplicityServices"])
    ],
    dependencies: [
        .package(path: "../SimplicityApi"),
        .package(path: "../SimplicityFoundation"),
        .package(path: "../SimplicityTesting"),
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0")),
        .package(url: "https://github.com/Kolos65/Mockable.git", exact: "0.5.0")
    ],
    targets: [
        .target(
            name: "SimplicityServices",
            dependencies: [
                "SimplicityApi",
                "SimplicityFoundation",
                .product(name: "Factory", package: "Factory"),
                .product(name: "Mockable", package: "Mockable")
            ],
            swiftSettings: [.define("MOCKING", .when(configuration: .debug))]
        ),
        .testTarget(
            name: "SimplicityServicesTests",
            dependencies: ["SimplicityServices", "SimplicityTesting"]
        )
    ],
    swiftLanguageModes: [.v5]
)
