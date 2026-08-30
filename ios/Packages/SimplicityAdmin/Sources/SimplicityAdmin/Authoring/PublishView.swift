import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct PublishView: View {

    // MARK: Properties

    @State private var model: PublishViewModel

    // MARK: Init

    public init(moduleId: UUID) {
        self._model = State(initialValue: PublishViewModel(moduleId: moduleId))
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            ErrorBanner(message: model.errorMessage)

            Section(String(localized: "publish_teams", bundle: .module)) {
                if model.teams.isEmpty {
                    Text("publish_no_teams", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                } else {
                    ForEach(model.teams, id: \.id) { team in
                        teamRow(team)
                    }

                    Button {
                        Task { await model.assign() }
                    } label: {
                        Text("publish_assign", bundle: .module)
                    }
                    .disabled(model.isBusy)
                    .accessibilityIdentifier("assign-teams")
                }
            }

            Section {
                Toggle(isOn: $model.supersedesCompletions) {
                    VStack(alignment: .leading, spacing: Spacing.x1) {
                        Text("publish_supersedes", bundle: .module)
                            .font(.brandBody)
                        Text("publish_supersedes_hint", bundle: .module)
                            .font(.brandCaption)
                            .foregroundStyle(Color.brandTextSecondary)
                    }
                }
                .accessibilityIdentifier("supersedes-toggle")

                Button {
                    Task { await model.publish() }
                } label: {
                    Text("publish_now", bundle: .module)
                }
                .disabled(model.isBusy || !model.hasSections)
                .accessibilityIdentifier("publish-now")

                if model.didPublish {
                    Text("publish_done", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandPrimary)
                }
            }
        }
        .navigationTitle(Text("publish_title", bundle: .module))
        .task { await model.load() }
    }

    // MARK: Private

    private func teamRow(_ team: TeamResponse) -> some View {
        Button {
            if let id = team.id { model.toggle(id) }
        } label: {
            HStack {
                Text(verbatim: team.name ?? "")
                    .foregroundStyle(Color.brandTextPrimary)
                Spacer()
                if let id = team.id, model.selectedTeamIds.contains(id) {
                    Image(systemName: "checkmark")
                        .foregroundStyle(Color.brandPrimary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
