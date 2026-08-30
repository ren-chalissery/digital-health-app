import Foundation
import SimplicityApi

/// A thin pass-through to the generated client.
///
/// Each call is injected as a closure for the same reason `SessionServiceImpl` does it: the
/// generated API exposes class functions, which cannot be mocked, and a closure is the smallest
/// seam that makes this testable without wrapping the whole client by hand.
public final class LearningServiceImpl: LearningService {

    // MARK: Types

    public typealias ListAssigned = @Sendable (UUID) async throws -> [AssignedModuleResponse]
    public typealias ReadModule = @Sendable (UUID, UUID) async throws -> LearnerModuleResponse
    public typealias ReadQuiz = @Sendable (UUID, UUID) async throws -> QuizResponse
    public typealias CompleteSection = @Sendable (UUID, UUID) async throws -> LearnerModuleResponse
    public typealias SubmitAttempt = @Sendable (UUID, UUID, SubmitAttemptRequest) async throws
        -> AttemptResultResponse
    public typealias Playback = @Sendable (UUID, UUID) async throws -> PlaybackResponse

    // MARK: Properties

    private let listAssigned: ListAssigned
    private let readModule: ReadModule
    private let readQuiz: ReadQuiz
    private let completeSectionCall: CompleteSection
    private let submitAttemptCall: SubmitAttempt
    private let playbackCall: Playback

    // MARK: Init

    public init(
        listAssigned: @escaping ListAssigned = { orgId in
            try await LearningAPI.listAssignedModules(orgId: orgId)
        },
        readModule: @escaping ReadModule = { orgId, moduleId in
            try await LearningAPI.readModule(orgId: orgId, moduleId: moduleId)
        },
        readQuiz: @escaping ReadQuiz = { orgId, moduleId in
            try await LearningAPI.getQuiz(orgId: orgId, moduleId: moduleId)
        },
        completeSection: @escaping CompleteSection = { orgId, sectionId in
            try await LearningAPI.completeSection(orgId: orgId, sectionId: sectionId)
        },
        submitAttempt: @escaping SubmitAttempt = { orgId, moduleId, request in
            try await LearningAPI.submitQuizAttempt(
                orgId: orgId,
                moduleId: moduleId,
                submitAttemptRequest: request
            )
        },
        playback: @escaping Playback = { orgId, assetId in
            try await LearningAPI.getPlaybackUrl(orgId: orgId, assetId: assetId)
        }
    ) {
        self.listAssigned = listAssigned
        self.readModule = readModule
        self.readQuiz = readQuiz
        self.completeSectionCall = completeSection
        self.submitAttemptCall = submitAttempt
        self.playbackCall = playback
    }

    // MARK: Functions

    public func assignedModules(orgId: UUID) async throws -> [AssignedModuleResponse] {
        try await listAssigned(orgId)
    }

    public func module(orgId: UUID, moduleId: UUID) async throws -> LearnerModuleResponse {
        try await readModule(orgId, moduleId)
    }

    public func quiz(orgId: UUID, moduleId: UUID) async throws -> QuizResponse {
        try await readQuiz(orgId, moduleId)
    }

    @discardableResult
    public func completeSection(
        orgId: UUID,
        sectionId: UUID
    ) async throws -> LearnerModuleResponse {
        try await completeSectionCall(orgId, sectionId)
    }

    public func submitAttempt(
        orgId: UUID,
        moduleId: UUID,
        answers: [AnswerInput]
    ) async throws -> AttemptResultResponse {
        try await submitAttemptCall(orgId, moduleId, SubmitAttemptRequest(answers: answers))
    }

    public func playback(orgId: UUID, assetId: UUID) async throws -> PlaybackResponse {
        try await playbackCall(orgId, assetId)
    }
}
