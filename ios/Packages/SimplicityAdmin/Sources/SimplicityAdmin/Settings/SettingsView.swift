import SimplicityApi
import SimplicityDesign
import SwiftUI

public enum AdminDestination: Hashable {
    case members
    case teams
    case invitations
    case team(id: UUID, name: String)
}

public struct SettingsView: View {

    // MARK: Properties

    @State private var model = SettingsViewModel()
    @State private var isConfirmingLeave = false
    private let onSignOut: () -> Void
    private let onSwitched: () -> Void
    private let onOpen: (AdminDestination) -> Void

    // MARK: Init

    public init(
        onSignOut: @escaping () -> Void,
        onSwitched: @escaping () -> Void,
        onOpen: @escaping (AdminDestination) -> Void
    ) {
        self.onSignOut = onSignOut
        self.onSwitched = onSwitched
        self.onOpen = onOpen
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            person

            if !model.memberships.isEmpty {
                organisation
            }

            // Hidden for a member. This is presentation, not security — the server authorises
            // every one of these calls regardless.
            if model.isOrgAdmin {
                administration
            }

            dangerous
        }
        .navigationTitle(Text("settings_title", bundle: .module))
        .task { await model.load() }
        .onChange(of: model.didSwitch) { _, switched in
            guard switched else { return }
            model.acknowledgeSwitch()
            onSwitched()
        }
        .confirmationDialog(
            Text(
                String(
                    format: String(localized: "settings_leave_confirm_title", bundle: .module),
                    model.activeOrganisation?.name ?? ""
                )
            ),
            isPresented: $isConfirmingLeave,
            titleVisibility: .visible
        ) {
            Button(role: .destructive) {
                Task { await model.leave() }
            } label: {
                Text("settings_leave", bundle: .module)
            }
        } message: {
            Text("settings_leave_confirm_body", bundle: .module)
        }
    }

    // MARK: Private

    private var person: some View {
        Section {
            VStack(alignment: .leading, spacing: Spacing.x1) {
                Text(verbatim: model.user?.fullName ?? "")
                    .font(.brandBody.weight(.semibold))
                Text(verbatim: model.user?.email ?? "")
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }
            .accessibilityElement(children: .combine)
        }
    }

    @ViewBuilder
    private var organisation: some View {
        Section(String(localized: "settings_organisation", bundle: .module)) {
            // One organisation needs no picker: a control with a single option is furniture.
            if model.memberships.count == 1 {
                membershipRow(model.memberships[0])
            } else {
                ForEach(model.memberships, id: \.orgId) { membership in
                    Button {
                        if let orgId = membership.orgId {
                            Task { await model.switchTo(orgId) }
                        }
                    } label: {
                        HStack {
                            membershipRow(membership)
                            Spacer()
                            if membership.orgId == model.activeOrganisation?.orgId {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.brandPrimary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            ErrorBanner(message: model.errorMessage)
        }
    }

    private func membershipRow(_ membership: OrganisationMembershipResponse) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x1) {
            Text(verbatim: membership.name ?? "")
                .font(.brandBody)
            Text(verbatim: (membership.orgRole ?? .orgMember).label)
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)
        }
        .accessibilityElement(children: .combine)
    }

    private var administration: some View {
        Section(String(localized: "settings_administration", bundle: .module)) {
            Button { onOpen(.members) } label: {
                Label {
                    Text("settings_members", bundle: .module)
                } icon: {
                    Image(systemName: "person.2")
                }
            }
            .accessibilityIdentifier("settings-members")

            Button { onOpen(.teams) } label: {
                Label {
                    Text("settings_teams", bundle: .module)
                } icon: {
                    Image(systemName: "person.3")
                }
            }
            .accessibilityIdentifier("settings-teams")

            Button { onOpen(.invitations) } label: {
                Label {
                    Text("settings_invitations", bundle: .module)
                } icon: {
                    Image(systemName: "envelope")
                }
            }
            .accessibilityIdentifier("settings-invitations")
        }
    }

    private var dangerous: some View {
        Section {
            if model.activeOrganisation != nil {
                Button(role: .destructive) { isConfirmingLeave = true } label: {
                    Text("settings_leave", bundle: .module)
                }
            }

            Button(role: .destructive, action: onSignOut) {
                Text("settings_sign_out", bundle: .module)
            }
            .accessibilityIdentifier("sign-out")
        }
    }
}

extension OrganisationMembershipResponse.OrgRole {

    var label: String {
        switch self {
        case .orgAdmin: String(localized: "role_org_admin", bundle: .module)
        case .orgMember: String(localized: "role_org_member", bundle: .module)
        }
    }
}
