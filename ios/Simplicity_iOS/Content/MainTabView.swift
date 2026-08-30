import SimplicityAssistant
import SimplicityDesign
import SimplicityLearn
import SimplicityReflect
import SwiftUI

/// The four tabs the web has.
///
/// `tabItem` rather than iOS 18's `Tab`, because the deployment target is iOS 17.
struct MainTabView: View {

    // MARK: Properties

    let onSignOut: () -> Void

    @State private var dashboardPath: [UUID] = []
    @State private var learnPath: [UUID] = []
    @State private var isAsking = false

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
                    .toolbar { askButton }
            }
            .tabItem { Label("Learn", systemImage: "book") }

            NavigationStack {
                ReflectView()
            }
            .tabItem { Label("Reflect", systemImage: "square.and.pencil") }

            settings
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .sheet(isPresented: $isAsking) {
            AskView { moduleId in
                learnPath.append(moduleId)
            }
        }
    }

    // MARK: Private

    /// A sheet from Learn rather than a fifth tab, for the reason Phase 4 gave: a fifth tab would
    /// mean reworking the shell for a feature nobody has used yet.
    private var askButton: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Button {
                isAsking = true
            } label: {
                Label("Ask about the training", systemImage: "questionmark.circle")
            }
            .accessibilityIdentifier("ask-open")
        }
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
