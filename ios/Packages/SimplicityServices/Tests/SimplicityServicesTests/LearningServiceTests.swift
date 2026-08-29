import Foundation
import SimplicityApi
import SimplicityTesting
import Testing

@testable import SimplicityServices

@Suite("LearningService", .serialized)
final class LearningServiceTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func assigned(
        title: String,
        status: AssignedModuleResponse.Status
    ) -> AssignedModuleResponse {
        AssignedModuleResponse(moduleId: UUID(), status: status, title: title)
    }

    nonisolated private static func learnerModule(
        status: LearnerModuleResponse.Status
    ) -> LearnerModuleResponse {
        LearnerModuleResponse(moduleId: Constants.moduleId, status: status, title: "A module")
    }

    // MARK: Outstanding

    @Test("a completed module is not outstanding")
    func completedIsNotOutstanding() {
        #expect(Self.assigned(title: "a", status: .completed).isOutstanding == false)
    }

    @Test("a module not started is outstanding")
    func notStartedIsOutstanding() {
        #expect(Self.assigned(title: "a", status: .notStarted).isOutstanding)
    }

    @Test("a module in progress is outstanding")
    func inProgressIsOutstanding() {
        #expect(Self.assigned(title: "a", status: .inProgress).isOutstanding)
    }

    @Test("a module needing redoing is outstanding again")
    func needsRedoingIsOutstanding() {
        #expect(Self.assigned(title: "a", status: .needsRedoing).isOutstanding)
    }

    // MARK: Pass-through

    @Test("returns assigned modules in the server's order, which the client must not re-sort")
    func preservesServerOrder() async throws {
        let service = LearningServiceImpl(
            listAssigned: { _ in
                [
                    Self.assigned(title: "zebra", status: .notStarted),
                    Self.assigned(title: "apple", status: .notStarted)
                ]
            }
        )

        let modules = try await service.assignedModules(orgId: Constants.orgId)

        #expect(modules.map(\.title) == ["zebra", "apple"])
    }

    @Test("completing a section returns the server's recomputed module rather than a local guess")
    func completeSectionReturnsServerModule() async throws {
        let service = LearningServiceImpl(
            completeSection: { _, _ in Self.learnerModule(status: .completed) }
        )

        let module = try await service.completeSection(orgId: Constants.orgId, sectionId: UUID())

        #expect(module.status == .completed)
    }

    @Test("submits the answers it was given, unchanged")
    func submitsAnswersUnchanged() async throws {
        let captured = CapturedAnswers()
        let service = LearningServiceImpl(
            submitAttempt: { _, _, request in
                await captured.set(request.answers)
                return AttemptResultResponse(passed: true)
            }
        )
        let question = UUID()
        let option = UUID()

        _ = try await service.submitAttempt(
            orgId: Constants.orgId,
            moduleId: Constants.moduleId,
            answers: [AnswerInput(optionId: option, questionId: question)]
        )

        let sent = await captured.value
        #expect(sent?.count == 1)
        #expect(sent?.first?.questionId == question)
        #expect(sent?.first?.optionId == option)
    }

    @Test("a failing call propagates rather than becoming an empty result")
    func propagatesFailure() async {
        let service = LearningServiceImpl(listAssigned: { _ in throw TestError.unreachable })

        await #expect(throws: TestError.self) {
            try await service.assignedModules(orgId: Constants.orgId)
        }
    }

    private actor CapturedAnswers {
        private(set) var value: [AnswerInput]?
        func set(_ answers: [AnswerInput]) { value = answers }
    }
}
