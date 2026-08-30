import SimplicityDesign
import SwiftUI

/// Hides the interface while the app is not frontmost.
///
/// iOS photographs the screen when an app backgrounds, and that snapshot persists and is shown in
/// the app switcher. A journal left open would become a thumbnail of somebody's clinical
/// reflection, visible to anyone who double-taps the home indicator — and a phone is handed
/// around far more often than a laptop is. The web has no equivalent exposure, which is why the
/// spec did not anticipate it.
///
/// Applied to the whole app rather than only to Reflect: the module reader and the ask sheet can
/// both be showing something worth not leaving on a lock screen.
struct PrivacyScreen: ViewModifier {

    @Environment(\.scenePhase) private var phase

    func body(content: Content) -> some View {
        content.overlay {
            // `.inactive` matters as much as `.background`: the snapshot is taken during the
            // transition, before the app is fully backgrounded.
            if phase != .active {
                Color.brandSurface
                    .ignoresSafeArea()
                    .overlay {
                        Image(systemName: "lock.fill")
                            .font(.largeTitle)
                            .foregroundStyle(Color.brandTextSecondary)
                    }
            }
        }
    }
}

extension View {

    func privacyScreen() -> some View {
        modifier(PrivacyScreen())
    }
}
