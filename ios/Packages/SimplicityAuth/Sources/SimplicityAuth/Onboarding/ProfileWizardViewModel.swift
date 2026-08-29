import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class ProfileWizardViewModel {

    // MARK: Types

    public typealias Update = @Sendable (UpdateProfileRequest) async throws -> CurrentUserResponse

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public var fullName: String = .empty
    public var phone: String = .empty
    public var professionalRole: String = .empty
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?
    public private(set) var didComplete = false

    private let update: Update

    // MARK: Init

    public init(
        update: @escaping Update = { request in
            try await CurrentUserAPI.updateProfile(updateProfileRequest: request)
        }
    ) {
        self.update = update
    }

    // MARK: Functions

    public func submit() async {
        let name = fullName.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else {
            errorMessage = String(localized: "onboarding_profile_missing_name", bundle: .module)
            return
        }
        guard !professionalRole.isEmpty else {
            errorMessage = String(localized: "onboarding_profile_missing_role", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        let trimmedPhone = phone.trimmingCharacters(in: .whitespaces)
        let request = UpdateProfileRequest(
            fullName: name,
            phone: trimmedPhone.isEmpty ? nil : trimmedPhone,
            professionalRole: professionalRole
        )

        do {
            _ = try await update(request)
            // The session is refreshed rather than patched locally, because profileCompleted is
            // what the shell routes on and a stale cached user bounces straight back here. That
            // exact loop bit the web app when responses were being parsed as blobs.
            try await session.refresh()
            didComplete = true
        } catch {
            errorMessage = String(localized: "onboarding_failed", bundle: .module)
        }
    }
}
