import SwiftUI
import Testing

@testable import SimplicityDesign

@Suite("Spacing")
struct TokensTests {

    @Test("the scale is the one the web uses, in order")
    func scaleIsOrdered() {
        let scale = [Spacing.x1, Spacing.x2, Spacing.x3, Spacing.x4, Spacing.x5, Spacing.x6]
        #expect(scale == [4, 8, 12, 16, 24, 32])
        #expect(scale == scale.sorted())
    }
}
