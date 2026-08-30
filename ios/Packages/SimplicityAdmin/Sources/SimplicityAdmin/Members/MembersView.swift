import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct MembersView: View {

    // MARK: Properties

    @State private var model = MembersViewModel()
    @State private var pendingRemoval: OrgMemberResponse?

    // MARK: Init

    public init() {}

    // MARK: SwiftUI

    public var body: some View {
        List {
            ErrorBanner(message: model.errorMessage)

            ForEach(model.members, id: \.userId) { member in
                row(member)
            }
        }
        .navigationTitle(Text("members_title", bundle: .module))
        .refreshable { await model.load() }
        .task { await model.load() }
        .confirmationDialog(
            Text(
                String(
                    format: String(localized: "members_remove_confirm_title", bundle: .module),
                    pendingRemoval?.fullName ?? pendingRemoval?.email ?? ""
                )
            ),
            isPresented: .constant(pendingRemoval != nil),
            titleVisibility: .visible,
            presenting: pendingRemoval
        ) { member in
            Button(role: .destructive) {
                let target = member
                pendingRemoval = nil
                Task { await model.remove(target) }
            } label: {
                Text("members_remove", bundle: .module)
            }
            Button(role: .cancel) { pendingRemoval = nil } label: { Text(verbatim: "Cancel") }
        } message: { _ in
            Text("members_remove_confirm_body", bundle: .module)
        }
    }

    // MARK: Private

    private func row(_ member: OrgMemberResponse) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x1) {
            HStack {
                Text(verbatim: member.fullName ?? member.email ?? "")
                    .font(.brandBody.weight(.semibold))
                if model.isSelf(member) {
                    Text("members_you", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                }
            }

            Text(verbatim: member.email ?? "")
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)

            if let professional = member.professionalRole, !professional.isEmpty {
                Text(verbatim: professional)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            } else {
                Text("members_no_profile", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            roleControl(member)
        }
        .padding(.vertical, Spacing.x1)
        .swipeActions {
            // No remove on your own row: removing yourself is leaving, which is a different
            // endpoint and a different consequence, and lives in Settings.
            if !model.isSelf(member) {
                Button(role: .destructive) {
                    pendingRemoval = member
                } label: {
                    Text("members_remove", bundle: .module)
                }
            }
        }
    }

    @ViewBuilder
    private func roleControl(_ member: OrgMemberResponse) -> some View {
        if model.isSelf(member) {
            Text(verbatim: (member.orgRole ?? .orgMember).label)
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)
        } else {
            Picker(
                String(localized: "role_org_member", bundle: .module),
                selection: Binding(
                    get: { member.orgRole ?? .orgMember },
                    set: { newRole in
                        Task { await model.changeRole(member, to: newRole.asChangeRequest) }
                    }
                )
            ) {
                Text(verbatim: OrgMemberResponse.OrgRole.orgMember.label)
                    .tag(OrgMemberResponse.OrgRole.orgMember)
                Text(verbatim: OrgMemberResponse.OrgRole.orgAdmin.label)
                    .tag(OrgMemberResponse.OrgRole.orgAdmin)
            }
            .pickerStyle(.menu)
            .labelsHidden()
            .disabled(model.isBusy)
        }
    }
}

extension OrgMemberResponse.OrgRole {

    var label: String {
        switch self {
        case .orgAdmin: String(localized: "role_org_admin", bundle: .module)
        case .orgMember: String(localized: "role_org_member", bundle: .module)
        }
    }

    /// The two enums are generated separately from the same values, so a hand-written bridge is
    /// the only way across.
    var asChangeRequest: ChangeOrgRoleRequest.OrgRole {
        switch self {
        case .orgAdmin: .orgAdmin
        case .orgMember: .orgMember
        }
    }
}
