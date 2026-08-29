import SimplicityDesign
import SimplicityLearn
import SwiftUI

/// The four tabs the web has. Reflect and the assistant arrive in later plans.
///
/// `tabItem` rather than iOS 18's `Tab`, because the deployment target is iOS 17.
struct MainTabView: View {

    // MARK: Properties

    let onSignOut: () -> Void

    @State private var dashboardPath: [UUID] = []
    @State private var learnPath: [UUID] = []

    // MARK: SwiftUI

    var body: some View {
        TabView {
            // A stack per tab, so opening a module from the Dashboard and from Learn keep
            // separate back histories rather than fighting over one.
            NavigationStack(path: $dashboardPath) {
                DashboardView { dashboardPath.append($0) }
                    .navigationDestination(for: UUID.self) { ModuleReaderView(moduleId: $0) }
            }
            .tabItem { Label("Dashboard", systemImage: "square.grid.2x2") }

            NavigationStack(path: $learnPath) {
                ModuleListView { learnPath.append($0) }
                    .navigationDestination(for: UUID.self) { ModuleReaderView(moduleId: $0) }
            }
            .tabItem { Label("Learn", systemImage: "book") }

            Placeholder(title: "Reflect")
                .tabItem { Label("Reflect", systemImage: "square.and.pencil") }

            settings
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }

    // MARK: Private

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
