import SimplicityDesign
import SwiftUI

/// The editor holds text in memory only.
///
/// Nothing here is written to disk — no draft, no autosave, no cache. A journal entry on disk is a
/// candidate for an iCloud backup, and a copy of somebody's clinical reflection outside the
/// database it was promised to stay in. The cost is that force-quitting mid-entry loses it.
struct ReflectionEditor: View {

    // MARK: Properties

    @Binding var title: String
    @Binding var text: String
    let warnings: [IdentifierWarning]
    let isSaving: Bool
    let isEditing: Bool
    let canSave: Bool
    let onSave: () -> Void
    let onCancel: () -> Void

    // MARK: SwiftUI

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x3) {
            TextField(String(localized: "reflect_entry_title", bundle: .module), text: $title)
                .font(.brandBody.weight(.semibold))
                .privateText()

            TextEditor(text: $text)
                .font(.brandBody)
                .frame(minHeight: 140)
                .scrollContentBackground(.hidden)
                .privateText()
                .overlay(alignment: .topLeading) {
                    if text.isEmpty {
                        Text("reflect_entry_body", bundle: .module)
                            .font(.brandBody)
                            .foregroundStyle(Color.brandTextSecondary)
                            .padding(.top, Spacing.x2)
                            .allowsHitTesting(false)
                    }
                }

            if !warnings.isEmpty {
                warningList
            }

            HStack(spacing: Spacing.x3) {
                if isEditing {
                    Button(action: onCancel) {
                        Text("reflect_cancel", bundle: .module).font(.brandCaption)
                    }
                }

                PrimaryButton(title: saveTitle, isLoading: isSaving, action: onSave)
                    .disabled(!canSave)
                    .accessibilityIdentifier("reflect-save")
            }
        }
        .padding(Spacing.x4)
        .background(Color.brandSurface)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.x3))
    }

    // MARK: Private

    private var saveTitle: String {
        if isSaving {
            return String(localized: "reflect_saving", bundle: .module)
        }
        return isEditing
            ? String(localized: "reflect_save_edit", bundle: .module)
            : String(localized: "reflect_save", bundle: .module)
    }

    /// Secondary rather than red, and phrased as a question rather than a refusal. This warns; it
    /// never blocks, because a filter that refuses to save teaches evasion.
    private var warningList: some View {
        VStack(alignment: .leading, spacing: Spacing.x1) {
            Text("reflect_warning_intro", bundle: .module)
                .font(.brandCaption.weight(.semibold))
                .foregroundStyle(Color.brandTextSecondary)

            ForEach(warnings, id: \.kind) { warning in
                Text(
                    String(
                        format: String(localized: "reflect_warning", bundle: .module),
                        warning.kind,
                        warning.explanation
                    )
                )
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension View {

    /// Autocorrection off wherever clinical text might be typed.
    ///
    /// iOS learns from what people type and shares that dictionary between apps. This is the only
    /// lever available short of marking the field as secure entry, which would make a journal
    /// unusable — so it is a mitigation, not a guarantee.
    @ViewBuilder
    func privateText() -> some View {
        #if os(iOS)
        autocorrectionDisabled()
            .textInputAutocapitalization(.sentences)
            .textContentType(nil)
        #else
        autocorrectionDisabled()
        #endif
    }
}
