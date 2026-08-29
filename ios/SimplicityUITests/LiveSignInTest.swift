import XCTest

/// Signs in against the real Cognito pool with a disposable account.
///
/// Not part of the normal suite — it needs credentials and a network, and it is skipped without
/// them. It exists because it is the only thing that proves Amplify's SRP implementation and the
/// pool agree; every other test substitutes a mock for exactly the code most likely to be wrong.
final class LiveSignInTest: XCTestCase {

    func testSignsInAgainstRealCognito() throws {
        let environment = ProcessInfo.processInfo.environment
        guard
            let email = environment["LIVE_EMAIL"],
            let password = environment["LIVE_PASSWORD"]
        else {
            throw XCTSkip("Set LIVE_EMAIL and LIVE_PASSWORD to run this")
        }

        let app = XCUIApplication()
        app.launchArguments = ["--uitest-signed-out"]
        app.launch()

        let emailField = app.textFields.firstMatch
        XCTAssertTrue(emailField.waitForExistence(timeout: 60))
        emailField.tap()
        emailField.typeText(email)

        let passwordField = app.secureTextFields.firstMatch
        passwordField.tap()
        passwordField.typeText(password)

        app.buttons["sign-in-submit"].tap()

        // A brand-new account has no profile, so a correct sign-in lands on the profile wizard
        // rather than the tabs. Either proves Cognito accepted the exchange.
        let reachedOnboarding = app.buttons["profile-submit"].waitForExistence(timeout: 60)
        let reachedTabs = app.buttons["main-tabs"].waitForExistence(timeout: 1)
            || app.tabBars.firstMatch.exists

        if !(reachedOnboarding || reachedTabs) {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            add(shot)
            XCTFail("Still on the signed-out screen. Hierarchy:\n\(app.debugDescription)")
        }
    }
}
