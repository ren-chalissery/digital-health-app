import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class OrganisationWizardViewModel {

    // MARK: Types

    public typealias Create = @Sendable (CreateOrganisationRequest) async throws
        -> OrganisationResponse

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public var name: String = .empty
    public var organisationType: CreateOrganisationRequest.OrganisationType = .clinic
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?
    public private(set) var didComplete = false

    private let create: Create

    // MARK: Init

    public init(
        create: @escaping Create = { request in
            try await OrganisationsAPI.createOrganisation(createOrganisationRequest: request)
        }
    ) {
        self.create = create
    }

    // MARK: Functions

    public func submit() async {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            errorMessage = String(localized: "onboarding_org_missing_name", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            _ = try await create(
                CreateOrganisationRequest(name: trimmed, organisationType: organisationType)
            )
            // Creating an organisation makes the creator its administrator and sets it active
            // server-side; the refresh is what tells the shell that onboarding is over.
            try await session.refresh()
            didComplete = true
        } catch {
            errorMessage = String(localized: "onboarding_failed", bundle: .module)
        }
    }
}
