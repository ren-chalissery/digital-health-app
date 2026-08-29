// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityFoundation",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityFoundation", targets: ["SimplicityFoundation"])
    ],
    targets: [
        .target(name: "SimplicityFoundation"),
        .testTarget(name: "SimplicityFoundationTests", dependencies: ["SimplicityFoundation"])
    ],
    swiftLanguageModes: [.v5]
)
