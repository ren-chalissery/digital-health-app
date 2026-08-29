// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityLearn",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityLearn", targets: ["SimplicityLearn"])
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
            name: "SimplicityLearn",
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
            name: "SimplicityLearnTests",
            dependencies: ["SimplicityLearn", "SimplicityTesting"]
        )
    ],
    swiftLanguageModes: [.v5]
)
