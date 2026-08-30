import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct TeamDetailView: View {

    // MARK: Properties

    @State private var model: TeamDetailViewModel

    // MARK: Init

    public init(teamId: UUID, teamName: String) {
        self._model = State(
            initialValue: TeamDetailViewModel(teamId: teamId, teamName: teamName)
        )
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            ErrorBanner(message: model.errorMessage)

            Section(String(localized: "team_members", bundle: .module)) {
                ForEach(model.members, id: \.userId) { member in
                    VStack(alignment: .leading, spacing: Spacing.x1) {
                        Text(verbatim: member.fullName ?? member.email ?? "")
                            .font(.brandBody)
                        Text(verbatim: (member.teamRole ?? .teamMember).label)
                            .font(.brandCaption)
                            .foregroundStyle(Color.brandTextSecondary)
                    }
                    .accessibilityElement(children: .combine)
                    .swipeActions {
                        Button(role: .destructive) {
                            Task { await model.remove(member) }
                        } label: {
                            Text("members_remove", bundle: .module)
                        }
                    }
                }
            }

            Section(String(localized: "team_add", bundle: .module)) {
                if model.candidates.isEmpty {
                    Text("team_no_candidates", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                } else {
                    ForEach(model.candidates, id: \.userId) { candidate in
                        Button {
                            Task { await model.add(candidate, as: .teamMember) }
                        } label: {
                            Label {
                                Text(verbatim: candidate.fullName ?? candidate.email ?? "")
                            } icon: {
                                Image(systemName: "plus.circle")
                            }
                        }
                        .disabled(model.isBusy)
                    }
                }
            }
        }
        .navigationTitle(model.teamName)
        .refreshable { await model.load() }
        .task { await model.load() }
    }
}

extension TeamMemberDetailResponse.TeamRole {

    var label: String {
        switch self {
        case .teamAdmin: String(localized: "role_team_admin", bundle: .module)
        case .teamMember: String(localized: "role_team_member", bundle: .module)
        }
    }
}
