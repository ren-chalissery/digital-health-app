import XCTest

/// One path only. UI tests are slow and brittle; the job here is to catch a shell that will not
/// launch or a chain that is not wired, not to assert behaviour the package tests already cover.
final class SmokeTests: XCTestCase {

    func testSignedOutAppShowsSignIn() {
        let app = XCUIApplication()
        app.launchArguments = ["--uitest-signed-out"]
        app.launch()

        XCTAssertTrue(
            app.buttons["sign-in-submit"].waitForExistence(timeout: 60),
            "The signed-out app should reach the sign-in screen"
        )

        XCTAssertFalse(
            app.tabBars.firstMatch.exists,
            "A signed-out app must not show the tabs"
        )
    }
}
