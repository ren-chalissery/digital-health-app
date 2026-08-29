import SimplicityDesign
import SwiftUI

/// The four tabs the web has. Learn, Reflect and the assistant arrive in later plans; the tabs
/// exist now so the shell they hang off is settled and tested.
///
/// `tabItem` rather than iOS 18's `Tab`, because the deployment target is iOS 17.
struct MainTabView: View {

    // MARK: Properties

    let onSignOut: () -> Void

    // MARK: SwiftUI

    var body: some View {
        TabView {
            Placeholder(title: "Dashboard")
                .tabItem { Label("Dashboard", systemImage: "square.grid.2x2") }

            Placeholder(title: "Learn")
                .tabItem { Label("Learn", systemImage: "book") }

            Placeholder(title: "Reflect")
                .tabItem { Label("Reflect", systemImage: "square.and.pencil") }

            settings
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .accessibilityIdentifier("main-tabs")
    }

    private var settings: some View {
        NavigationStack {
            List {
                Button("Sign out", role: .destructive, action: onSignOut)
                    .accessibilityIdentifier("sign-out")
            }
            .navigationTitle("Settings")
        }
    }
}

private struct Placeholder: View {

    let title: String

    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                title,
                systemImage: "clock",
                description: Text(verbatim: "Coming in a later release.")
            )
            .navigationTitle(title)
        }
    }
}
