// swift-tools-version: 6.2

import PackageDescription

// Hand-written, and listed in .openapi-generator-ignore so a regeneration does not replace it.
// The target has no explicit path, so it picks up both Generated/ and the hand-written transport
// files beside it.
let package = Package(
    name: "SimplicityApi",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityApi", targets: ["SimplicityApi"])
    ],
    dependencies: [
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0"))
    ],
    targets: [
        .target(
            name: "SimplicityApi",
            dependencies: [.product(name: "Factory", package: "Factory")]
        ),
        .testTarget(name: "SimplicityApiTests", dependencies: ["SimplicityApi"])
    ],
    swiftLanguageModes: [.v5]
)
