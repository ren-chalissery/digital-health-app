// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityTesting",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityTesting", targets: ["SimplicityTesting"])
    ],
    dependencies: [
        .package(path: "../SimplicityFoundation"),
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0"))
    ],
    targets: [
        .target(
            name: "SimplicityTesting",
            dependencies: [
                "SimplicityFoundation",
                .product(name: "Factory", package: "Factory")
            ]
        ),
        .testTarget(name: "SimplicityTestingTests", dependencies: ["SimplicityTesting"])
    ],
    swiftLanguageModes: [.v5]
)
