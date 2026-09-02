import SwiftUI

/// What a field is for, rather than which UIKit constants to set.
///
/// Callers name the meaning and this decides the keyboard, the autofill hint and the
/// capitalisation. Keeping `UITextContentType` out of the signature is what lets this package
/// build on macOS, and therefore be tested with `swift test`.
public enum FieldKind {
    case email
    case password
    case newPassword
    case oneTimeCode
    case personName
    case phone
    case plain
}

public struct FormField: View {

    // MARK: Properties

    private let label: String
    private let kind: FieldKind
    @Binding private var text: String

    // MARK: Init

    public init(label: String, text: Binding<String>, kind: FieldKind = .plain) {
        self.label = label
        self._text = text
        self.kind = kind
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x1) {
            Text(label)
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)

            field
                .textFieldStyle(.plain)
                .fieldKind(kind)
                .padding(Spacing.x3)
                .frame(minHeight: Layout.minimumTapTarget)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.small))
                // Without a boundary the field is only findable by its placeholder, which
                // disappears the moment anything is typed.
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.small)
                        .stroke(Color.brandBorderStrong, lineWidth: 1)
                )
        }
    }

    @ViewBuilder
    private var field: some View {
        if kind == .password || kind == .newPassword {
            SecureField(label, text: $text)
        } else {
            TextField(label, text: $text)
        }
    }
}

private extension View {

    @ViewBuilder
    func fieldKind(_ kind: FieldKind) -> some View {
        #if os(iOS)
        self
            .textContentType(kind.contentType)
            .keyboardType(kind.keyboardType)
            .textInputAutocapitalization(kind.capitalisation)
            .autocorrectionDisabled(kind.disablesAutocorrection)
        #else
        self
        #endif
    }
}

#if os(iOS)
import UIKit

private extension FieldKind {

    var contentType: UITextContentType? {
        switch self {
        case .email: .username
        case .password: .password
        case .newPassword: .newPassword
        case .oneTimeCode: .oneTimeCode
        case .personName: .name
        case .phone: .telephoneNumber
        case .plain: nil
        }
    }

    var keyboardType: UIKeyboardType {
        switch self {
        case .email: .emailAddress
        case .oneTimeCode: .numberPad
        case .phone: .phonePad
        default: .default
        }
    }

    var capitalisation: TextInputAutocapitalization {
        switch self {
        case .personName: .words
        case .plain: .sentences
        default: .never
        }
    }

    /// Off everywhere except free text. An autocorrected email address or code is a support
    /// ticket, and a corrected name is an insult.
    var disablesAutocorrection: Bool {
        self != .plain
    }
}
#endif
