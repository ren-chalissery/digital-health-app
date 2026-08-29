import SwiftUI

/// A fixed scale. A feature needing a value not on it should change the scale rather than
/// hard-code a number — that is the whole reason this exists.
public enum Spacing {
    public static let x1: CGFloat = 4
    public static let x2: CGFloat = 8
    public static let x3: CGFloat = 12
    public static let x4: CGFloat = 16
    public static let x5: CGFloat = 24
    public static let x6: CGFloat = 32
}

public extension Color {
    static let brandPrimary = Color(red: 0.11, green: 0.36, blue: 0.62)
    static let brandDanger = Color(red: 0.72, green: 0.16, blue: 0.16)

    /// Semantic colours rather than fixed ones, so dark mode and the accessibility contrast
    /// settings work without a second palette. These are SwiftUI's own rather than UIKit's, which
    /// keeps the package compiling on macOS and therefore testable with `swift test`.
    static let brandTextPrimary = Color.primary
    static let brandTextSecondary = Color.secondary
    static let brandSurface = Color.primary.opacity(0.06)
}

public extension Font {

    /// Built from text styles rather than fixed point sizes, so Dynamic Type scales them.
    static let brandTitle = Font.system(.title2, design: .default, weight: .semibold)
    static let brandBody = Font.system(.body)
    static let brandCaption = Font.system(.caption)
}
