import SwiftUI

public struct PrimaryButton: View {

    // MARK: Properties

    private let title: String
    private let isLoading: Bool
    private let action: () -> Void

    // MARK: Init

    public init(title: String, isLoading: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.isLoading = isLoading
        self.action = action
    }

    // MARK: SwiftUI

    public var body: some View {
        Button(action: action) {
            ZStack {
                // The title stays in place while busy so the button does not change size,
                // which would shift everything below it.
                Text(title).opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView().tint(.white)
                }
            }
            .font(.brandBody.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(Color.brandPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.x3))
        }
        .disabled(isLoading)
        .accessibilityLabel(title)
    }
}
