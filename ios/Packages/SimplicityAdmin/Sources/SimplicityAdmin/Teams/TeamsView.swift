import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct TeamsView: View {

    // MARK: Properties

    @State private var model = TeamsViewModel()
    @State private var pendingDeletion: TeamResponse?
    private let onOpen: (AdminDestination) -> Void

    // MARK: Init

    public init(onOpen: @escaping (AdminDestination) -> Void) {
        self.onOpen = onOpen
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            Section {
                TextField(
                    String(localized: "teams_new_name", bundle: .module),
                    text: $model.newTeamName
                )
                TextField(
                    String(localized: "teams_new_description", bundle: .module),
                    text: $model.newTeamDescription
                )
                Button {
                    Task { await model.create() }
                } label: {
                    Text("teams_create", bundle: .module)
                }
                .disabled(!model.canCreate)
                .accessibilityIdentifier("team-create")
            }

            ErrorBanner(message: model.errorMessage)

            if model.teams.isEmpty, !model.isLoading {
                Text("teams_empty", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            ForEach(model.teams, id: \.id) { team in
                Button {
                    if let id = team.id {
                        onOpen(.team(id: id, name: team.name ?? ""))
                    }
                } label: {
                    VStack(alignment: .leading, spacing: Spacing.x1) {
                        Text(verbatim: team.name ?? "")
                            .font(.brandBody)
                            .foregroundStyle(Color.brandTextPrimary)
                        if let description = team.description, !description.isEmpty {
                            Text(verbatim: description)
                                .font(.brandCaption)
                                .foregroundStyle(Color.brandTextSecondary)
                        }
                        Text(
                            String(
                                format: String(localized: "teams_member_count", bundle: .module),
                                team.memberCount ?? 0
                            )
                        )
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                    }
                    .accessibilityElement(children: .combine)
                }
                .buttonStyle(.plain)
                .swipeActions {
                    Button(role: .destructive) {
                        pendingDeletion = team
                    } label: {
                        Text("teams_delete", bundle: .module)
                    }
                }
            }
        }
        .navigationTitle(Text("teams_title", bundle: .module))
        .refreshable { await model.load() }
        .task { await model.load() }
        .confirmationDialog(
            Text(
                String(
                    format: String(localized: "teams_delete_confirm_title", bundle: .module),
                    pendingDeletion?.name ?? ""
                )
            ),
            isPresented: .constant(pendingDeletion != nil),
            titleVisibility: .visible,
            presenting: pendingDeletion
        ) { team in
            Button(role: .destructive) {
                let target = team
                pendingDeletion = nil
                Task { await model.delete(target) }
            } label: {
                Text("teams_delete", bundle: .module)
            }
            Button(role: .cancel) { pendingDeletion = nil } label: { Text(verbatim: "Cancel") }
        } message: { _ in
            Text("teams_delete_confirm_body", bundle: .module)
        }
    }
}
