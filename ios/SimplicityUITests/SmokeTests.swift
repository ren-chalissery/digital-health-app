import XCTest

/// One path only. UI tests are slow and brittle; their job here is to catch a shell that will not
/// launch, not to assert behaviour the package tests already cover.
final class SmokeTests: XCTestCase {

    func testAppLaunches() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["app-root"].waitForExistence(timeout: 30))
    }
}
