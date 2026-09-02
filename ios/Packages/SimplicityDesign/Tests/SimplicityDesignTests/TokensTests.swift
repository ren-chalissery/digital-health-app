import SwiftUI
import Testing

@testable import SimplicityDesign

@Suite("Spacing")
struct TokensTests {

    @Test("the scale is the one the web uses, in order")
    func scaleIsOrdered() {
        let scale = [
            Spacing.x1, Spacing.x2, Spacing.x3, Spacing.x4, Spacing.x5, Spacing.x6, Spacing.x7,
        ]
        // Matches --space-1 to --space-7 in web/src/styles.scss and Spacing in the Android
        // design module. A step added here has to be added in both of those too.
        #expect(scale == [4, 8, 12, 16, 24, 32, 48])
        #expect(scale == scale.sorted())
    }

    @Test("radii are ordered and distinct from the spacing scale")
    func radiiAreOrdered() {
        let radii = [Radius.small, Radius.medium, Radius.large]
        #expect(radii == [6, 10, 16])
        #expect(radii == radii.sorted())
    }

    @Test("a tappable control clears the Human Interface Guidelines minimum")
    func tapTargetIsBigEnough() {
        #expect(Layout.minimumTapTarget >= 44)
    }
}
