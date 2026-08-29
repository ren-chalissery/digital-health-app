// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityDesign",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityDesign", targets: ["SimplicityDesign"])
    ],
    targets: [
        .target(name: "SimplicityDesign"),
        .testTarget(name: "SimplicityDesignTests", dependencies: ["SimplicityDesign"])
    ],
    swiftLanguageModes: [.v5]
)
