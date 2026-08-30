import Foundation
import Mockable
import SimplicityApi

/// Everything the Learn tab needs from the API.
///
/// Nothing here computes progress. Status, completion and marking are all the server's answers,
/// which is what keeps the app and the web agreeing about the same clinician.
@Mockable
public protocol LearningService: AnyObject, Sendable {
    func assignedModules(orgId: UUID) async throws -> [AssignedModuleResponse]
    func module(orgId: UUID, moduleId: UUID) async throws -> LearnerModuleResponse
    func quiz(orgId: UUID, moduleId: UUID) async throws -> QuizResponse

    @discardableResult
    func completeSection(orgId: UUID, sectionId: UUID) async throws -> LearnerModuleResponse

    func submitAttempt(
        orgId: UUID,
        moduleId: UUID,
        answers: [AnswerInput]
    ) async throws -> AttemptResultResponse

    func playback(orgId: UUID, assetId: UUID) async throws -> PlaybackResponse
}

public extension AssignedModuleResponse {

    /// Anything not finished, including a module that was finished before its content changed
    /// substantively and has come back around.
    var isOutstanding: Bool {
        status != .completed
    }
}
