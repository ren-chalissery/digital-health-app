import SwiftUI

/// Renders nothing when there is no message, so callers can place it unconditionally rather than
/// wrapping every use in an `if`.
public struct ErrorBanner: View {

    // MARK: Properties

    private let message: String?

    // MARK: Init

    public init(message: String?) {
        self.message = message
    }

    // MARK: SwiftUI

    public var body: some View {
        if let message {
            Text(message)
                .font(.brandCaption)
                .foregroundStyle(Color.brandDanger)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.x3)
                // A solid token rather than the foreground at 10 per cent, which produced a
                // different tint on every background it was placed over.
                .background(Color.brandDangerSoft)
                .clipShape(RoundedRectangle(cornerRadius: Radius.small))
                // Announced when it appears, rather than only found by someone exploring the
                // screen after a failure they did not know had happened.
                .accessibilityAddTraits(.isStaticText)
                .accessibilityLabel(Text(message))
        }
    }
}
