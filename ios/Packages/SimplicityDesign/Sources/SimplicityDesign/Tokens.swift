import SwiftUI

#if canImport(UIKit)
import UIKit
#endif

/// A fixed scale. A feature needing a value not on it should change the scale rather than
/// hard-code a number — that is the whole reason this exists.
///
/// The same seven steps exist on web as `--space-1` to `--space-7` and on Android as `Spacing`.
public enum Spacing {
    public static let x1: CGFloat = 4
    public static let x2: CGFloat = 8
    public static let x3: CGFloat = 12
    public static let x4: CGFloat = 16
    public static let x5: CGFloat = 24
    public static let x6: CGFloat = 32
    public static let x7: CGFloat = 48
}

/// Corner radii, kept separate from `Spacing` because the two are not interchangeable: a control
/// whose radius happens to equal its padding does so by coincidence, and reading one from the
/// other makes both harder to change.
public enum Radius {
    public static let small: CGFloat = 6
    public static let medium: CGFloat = 10
    public static let large: CGFloat = 16
}

/// The minimum height of anything tappable, per the Human Interface Guidelines.
public enum Layout {
    public static let minimumTapTarget: CGFloat = 44
}

/// The app icon's green, walked across lightness at a fixed hue of 151 degrees and 50 per cent
/// saturation. Shared verbatim with web (`styles.scss`) and Android (`Theme.kt`); changing a value
/// here means changing it in all three.
///
/// These are the raw steps. Views should reach for the semantic names below instead, so that the
/// dark theme keeps working.
public enum BrandRamp {
    public static let step50: UInt32 = 0xF0FAF5
    public static let step100: UInt32 = 0xDDF4E8
    public static let step200: UInt32 = 0xBAE8D2
    public static let step300: UInt32 = 0x8CD9B4
    public static let step400: UInt32 = 0x57C791
    public static let step500: UInt32 = 0x38A872
    public static let step600: UInt32 = 0x2B8258
    public static let step700: UInt32 = 0x216343
    public static let step800: UInt32 = 0x194D34
    public static let step900: UInt32 = 0x123624

    /// The two figures in the icon. Warm, non-interactive emphasis only — both are far too
    /// low-contrast against white to carry text.
    public static let cream: UInt32 = 0xF4ECE1
    public static let sand: UInt32 = 0xE6D6BD
}

public extension Color {

    // MARK: Brand

    /// Solid buttons, links, selected state. Lightens to ramp step 400 in the dark theme, because
    /// step 700 on a dark surface is 1.6:1 and effectively invisible.
    static let brandPrimary = Color(light: BrandRamp.step700, dark: BrandRamp.step400)
    static let brandPrimaryHover = Color(light: BrandRamp.step800, dark: BrandRamp.step300)
    /// Tinted backing for a badge or a selected row, never for text.
    static let brandPrimarySoft = Color(light: BrandRamp.step100, dark: 0x17301F)

    // MARK: Surfaces

    static let brandBackground = Color(light: 0xF6F8F6, dark: 0x0F1613)
    /// A subtle filled panel: field backgrounds, message bubbles, code blocks.
    static let brandSurface = Color(light: 0xEFF3F0, dark: 0x1D2A23)
    /// A card lifted off the background.
    static let brandSurfaceRaised = Color(light: 0xFFFFFF, dark: 0x16211B)
    static let brandBorder = Color(light: 0xDCE4DE, dark: 0x2A3B32)
    /// The visible boundary of a control. 3.1:1 against its surface, which is what WCAG asks for
    /// something that is not text but still has to be perceivable.
    static let brandBorderStrong = Color(light: 0x7C988A, dark: 0x586F64)

    // MARK: Text

    static let brandTextPrimary = Color(light: 0x16211B, dark: 0xE8EFEA)
    static let brandTextSecondary = Color(light: 0x5A6B61, dark: 0x9CB0A4)
    /// Reads against `brandPrimary`, which in the dark theme is the light colour.
    static let brandTextInverse = Color(light: 0xFFFFFF, dark: 0x0F1613)

    // MARK: Status

    static let brandDanger = Color(light: 0xA9261F, dark: 0xF2857E)
    static let brandDangerSoft = Color(light: 0xFBECEB, dark: 0x2E1614)
    static let brandSuccess = Color(light: BrandRamp.step700, dark: BrandRamp.step400)
    static let brandSuccessSoft = Color(light: BrandRamp.step100, dark: 0x17301F)
    static let brandWarning = Color(light: 0x8A5A12, dark: 0xE0B063)
    static let brandWarningSoft = Color(light: 0xFDF3E2, dark: 0x2C2113)
}

public extension Font {

    /// Built from text styles rather than fixed point sizes, so Dynamic Type scales them.
    static let brandTitle = Font.system(.title2, design: .default, weight: .semibold)
    static let brandBody = Font.system(.body)
    static let brandCaption = Font.system(.caption)
}

extension Color {

    /// Resolves against the current interface style on iOS, so one name covers both themes and no
    /// view has to know which is showing.
    ///
    /// On macOS there is no `UIColor`, and this package builds there deliberately — that is what
    /// lets it be tested with `swift test` rather than only in a simulator. The light value is
    /// used in that case, which only affects previews and tests.
    init(light: UInt32, dark: UInt32) {
        #if canImport(UIKit)
        self.init(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
        #else
        self.init(hex: light)
        #endif
    }

    /// The palette is written as hex to stay legible beside the web and Android files it has to
    /// agree with; decimal `Color(red:green:blue:)` would not survive a careful diff.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

#if canImport(UIKit)
private extension UIColor {

    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
#endif
