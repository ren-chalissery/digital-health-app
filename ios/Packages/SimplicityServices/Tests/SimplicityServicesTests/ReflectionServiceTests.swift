import Foundation
import SimplicityApi
import SimplicityTesting
import Testing

@testable import SimplicityServices

@Suite("ReflectionService", .serialized)
final class ReflectionServiceTests: SimplicityTestCase {

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func entry(_ body: String) -> ReflectionResponse {
        ReflectionResponse(body: body, id: UUID(), title: "An entry")
    }

    @Test("an empty search is sent as no search at all, which the server treats differently")
    func emptyQueryBecomesNil() async throws {
        let captured = CapturedQuery()
        let service = ReflectionServiceImpl(list: { query in
            await captured.set(query)
            return []
        })

        _ = try await service.list(query: "   ")

        #expect(await captured.wasSet)
        #expect(await captured.value == nil)
    }

    @Test("a real search is passed through, trimmed")
    func passesQueryThrough() async throws {
        let captured = CapturedQuery()
        let service = ReflectionServiceImpl(list: { query in
            await captured.set(query)
            return []
        })

        _ = try await service.list(query: " pacing ")

        #expect(await captured.value == "pacing")
    }

    @Test("writing sends the title and body it was given")
    func writeSendsFields() async throws {
        let captured = CapturedRequest()
        let service = ReflectionServiceImpl(write: { request in
            await captured.set(request)
            return Self.entry(request.body)
        })

        _ = try await service.write(title: "A title", body: "A body")

        #expect(await captured.value?.title == "A title")
        #expect(await captured.value?.body == "A body")
    }

    @Test("editing sends the id it was given")
    func editSendsId() async throws {
        let captured = CapturedId()
        let target = UUID()
        let service = ReflectionServiceImpl(edit: { id, request in
            await captured.set(id)
            return Self.entry(request.body)
        })

        _ = try await service.edit(id: target, title: nil, body: "Changed")

        #expect(await captured.value == target)
    }

    @Test("a failure propagates rather than becoming an empty journal")
    func propagatesFailure() async {
        let service = ReflectionServiceImpl(list: { _ in throw TestError.unreachable })

        await #expect(throws: TestError.self) { try await service.list(query: nil) }
    }

    private actor CapturedQuery {
        private(set) var value: String?
        private(set) var wasSet = false
        func set(_ query: String?) {
            value = query
            wasSet = true
        }
    }

    private actor CapturedRequest {
        private(set) var value: WriteReflectionRequest?
        func set(_ request: WriteReflectionRequest) { value = request }
    }

    private actor CapturedId {
        private(set) var value: UUID?
        func set(_ id: UUID) { value = id }
    }
}

@Suite("AssistantService", .serialized)
final class AssistantServiceTests: SimplicityTestCase {

    @Test("wraps the question in the request the API expects")
    func wrapsQuestion() async throws {
        let captured = CapturedQuestion()
        let service = AssistantServiceImpl(ask: { _, request in
            await captured.set(request.question)
            return AnswerResponse(answer: "Yes.", answered: true, citations: [])
        })

        _ = try await service.ask(orgId: UUID(), question: "What is pacing?")

        #expect(await captured.value == "What is pacing?")
    }

    @Test("an unanswered response is returned, not turned into an error")
    func unansweredIsNotAnError() async throws {
        // The whole design of Phase 4 rests on this: refusing to answer is the assistant working.
        let service = AssistantServiceImpl(ask: { _, _ in
            AnswerResponse(answered: false, citations: [])
        })

        let answer = try await service.ask(orgId: UUID(), question: "Should I prescribe?")

        #expect(answer.answered == false)
    }

    private actor CapturedQuestion {
        private(set) var value: String?
        func set(_ question: String) { value = question }
    }
}
