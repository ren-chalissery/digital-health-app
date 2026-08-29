#if canImport(UIKit)
import SwiftUI
import UIKit

/// iOS only: `UITextContentType` and `UIKeyboardType` have no macOS equivalent, and the package
/// stays buildable on macOS so its logic can be tested with `swift test`.
public struct FormField: View {

    // MARK: Properties

    private let label: String
    private let isSecure: Bool
    private let contentType: UITextContentType?
    private let keyboardType: UIKeyboardType
    @Binding private var text: String

    // MARK: Init

    public init(
        label: String,
        text: Binding<String>,
        isSecure: Bool = false,
        contentType: UITextContentType? = nil,
        keyboardType: UIKeyboardType = .default
    ) {
        self.label = label
        self._text = text
        self.isSecure = isSecure
        self.contentType = contentType
        self.keyboardType = keyboardType
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x1) {
            Text(label)
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)

            field
                .textFieldStyle(.plain)
                .textContentType(contentType)
                .keyboardType(keyboardType)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .padding(Spacing.x3)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
        }
    }

    @ViewBuilder
    private var field: some View {
        if isSecure {
            SecureField(label, text: $text)
        } else {
            TextField(label, text: $text)
        }
    }
}
#endif
