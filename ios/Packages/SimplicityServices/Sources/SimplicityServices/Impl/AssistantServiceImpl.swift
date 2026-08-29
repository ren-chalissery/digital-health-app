import Foundation
import SimplicityApi

public final class AssistantServiceImpl: AssistantService {

    // MARK: Types

    public typealias Ask = @Sendable (UUID, AskRequest) async throws -> AnswerResponse

    // MARK: Properties

    private let askCall: Ask

    // MARK: Init

    public init(
        ask: @escaping Ask = { orgId, request in
            try await AssistantAPI.askAssistant(orgId: orgId, askRequest: request)
        }
    ) {
        self.askCall = ask
    }

    // MARK: Functions

    public func ask(orgId: UUID, question: String) async throws -> AnswerResponse {
        try await askCall(orgId, AskRequest(question: question))
    }
}
