// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityAuth",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityAuth", targets: ["SimplicityAuth"])
    ],
    dependencies: [
        .package(path: "../SimplicityApi"),
        .package(path: "../SimplicityDesign"),
        .package(path: "../SimplicityFoundation"),
        .package(path: "../SimplicityServices"),
        .package(path: "../SimplicityTesting"),
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0")),
        .package(url: "https://github.com/Kolos65/Mockable.git", exact: "0.5.0"),
        .package(url: "https://github.com/aws-amplify/amplify-swift.git", .upToNextMajor(from: "2.0.0"))
    ],
    targets: [
        .target(
            name: "SimplicityAuth",
            dependencies: [
                "SimplicityApi",
                "SimplicityDesign",
                "SimplicityFoundation",
                "SimplicityServices",
                .product(name: "Factory", package: "Factory"),
                .product(name: "Mockable", package: "Mockable"),
                .product(name: "Amplify", package: "amplify-swift"),
                .product(name: "AWSCognitoAuthPlugin", package: "amplify-swift"),
                // AuthCognitoTokensProvider lives here, not in the plugin.
                .product(name: "AWSPluginsCore", package: "amplify-swift")
            ],
            resources: [.process("Resources")],
            swiftSettings: [.define("MOCKING", .when(configuration: .debug))]
        ),
        .testTarget(
            name: "SimplicityAuthTests",
            dependencies: ["SimplicityAuth", "SimplicityTesting"]
        )
    ],
    swiftLanguageModes: [.v5]
)
