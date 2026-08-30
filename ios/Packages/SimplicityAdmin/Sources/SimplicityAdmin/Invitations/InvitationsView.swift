import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct InvitationsView: View {

    // MARK: Properties

    @State private var model = InvitationsViewModel()

    // MARK: Init

    public init() {}

    // MARK: SwiftUI

    public var body: some View {
        List {
            Section {
                TextField(
                    String(localized: "invitations_email", bundle: .module),
                    text: $model.email
                )
                .emailInput()

                Picker(
                    String(localized: "invitations_role", bundle: .module),
                    selection: $model.orgRole
                ) {
                    Text("role_org_member", bundle: .module)
                        .tag(CreateInvitationRequest.OrgRole.orgMember)
                    Text("role_org_admin", bundle: .module)
                        .tag(CreateInvitationRequest.OrgRole.orgAdmin)
                }

                Picker(
                    String(localized: "invitations_team", bundle: .module),
                    selection: $model.teamId
                ) {
                    Text("invitations_no_team", bundle: .module).tag(UUID?.none)
                    ForEach(model.teams, id: \.id) { team in
                        Text(verbatim: team.name ?? "").tag(team.id)
                    }
                }

                Button {
                    Task { await model.invite() }
                } label: {
                    Text("invitations_send", bundle: .module)
                }
                .disabled(!model.canInvite)
                .accessibilityIdentifier("invitation-send")
            }

            ErrorBanner(message: model.errorMessage)

            if model.invitations.isEmpty, !model.isLoading {
                Text("invitations_empty", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            ForEach(model.invitations, id: \.id) { invitation in
                row(invitation)
            }
        }
        .navigationTitle(Text("invitations_title", bundle: .module))
        .refreshable { await model.load() }
        .task { await model.load() }
    }

    // MARK: Private

    private func row(_ invitation: InvitationResponse) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x1) {
            Text(verbatim: invitation.email ?? "")
                .font(.brandBody)

            HStack(spacing: Spacing.x2) {
                Text(verbatim: (invitation.status ?? .pending).label)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)

                if let team = invitation.teamName, !team.isEmpty {
                    Text(verbatim: team)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                }
            }

            if let expires = invitation.expiresAt, invitation.canRevoke {
                Text(
                    String(
                        format: String(localized: "invitations_expires", bundle: .module),
                        expires.formatted(.dateTime.day().month(.abbreviated))
                    )
                )
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .swipeActions {
            // Only pending invitations. One already accepted is a membership, and removing that
            // person happens in Members.
            if invitation.canRevoke {
                Button(role: .destructive) {
                    Task { await model.revoke(invitation) }
                } label: {
                    Text("invitations_revoke", bundle: .module)
                }
            }
        }
    }
}

private extension View {

    /// `keyboardType` and `textInputAutocapitalization` are UIKit-backed and absent on macOS,
    /// where the package still has to build so its view models can be tested.
    @ViewBuilder
    func emailInput() -> some View {
        #if os(iOS)
        textContentType(.emailAddress)
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
        #else
        autocorrectionDisabled()
        #endif
    }
}

extension InvitationResponse.Status {

    var label: String {
        switch self {
        case .pending: String(localized: "invitation_status_pending", bundle: .module)
        case .accepted: String(localized: "invitation_status_accepted", bundle: .module)
        case .revoked: String(localized: "invitation_status_revoked", bundle: .module)
        case .expired: String(localized: "invitation_status_expired", bundle: .module)
        }
    }
}
