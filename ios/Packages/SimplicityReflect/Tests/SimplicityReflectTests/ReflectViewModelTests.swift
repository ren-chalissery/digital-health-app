import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityReflect

@Suite("ReflectViewModel", .serialized)
@MainActor
final class ReflectViewModelTests: SimplicityTestCase {

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func entry(
        id: UUID = UUID(),
        title: String? = "An entry",
        body: String = "Some reflection."
    ) -> ReflectionResponse {
        ReflectionResponse(body: body, id: id, title: title)
    }

    private func makeSUT(
        entries: [ReflectionResponse] = [],
        saveFails: Bool = false,
        listFails: Bool = false
    ) -> (ReflectViewModel, MockReflectionService) {
        let service = MockReflectionService(policy: .relaxed)
        if listFails {
            given(service).list(query: .any).willThrow(TestError.unreachable)
        } else {
            given(service).list(query: .any).willReturn(entries)
        }
        if saveFails {
            given(service).write(title: .any, body: .any).willThrow(TestError.unreachable)
            given(service).edit(id: .any, title: .any, body: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(service).write(title: .any, body: .any).willReturn(Self.entry())
            given(service).edit(id: .any, title: .any, body: .any).willReturn(Self.entry())
        }

        Container.shared.reflectionService.register { service }
        return (ReflectViewModel(), service)
    }

    // MARK: Warnings

    @Test("warnings appear as the body is typed, not only when it is saved")
    func warnsWhileTyping() {
        let (model, _) = makeSUT()

        model.body = "Spoke with ZZZ0016 today"

        #expect(model.warnings.contains { $0.kind == "an NHI number" })
    }

    @Test("the title is checked too, because a name in a title is still a name")
    func warnsAboutTheTitle() {
        let (model, _) = makeSUT()

        model.title = "Call with ada@example.org"

        #expect(model.warnings.contains { $0.kind == "an email address" })
    }

    @Test("ordinary writing produces no warnings")
    func noWarningsForProse() {
        let (model, _) = makeSUT()

        model.body = "I rushed the opening and will slow down next time."

        #expect(model.warnings.isEmpty)
    }

    // MARK: Saving

    @Test("an empty entry cannot be saved, because it is not a reflection")
    func cannotSaveEmpty() async {
        let (model, service) = makeSUT()

        await model.save()

        #expect(model.canSave == false)
        verify(service).write(title: .any, body: .any).called(0)
    }

    @Test("whitespace alone cannot be saved either")
    func cannotSaveWhitespace() async {
        let (model, service) = makeSUT()
        model.body = "   \n  "

        await model.save()

        verify(service).write(title: .any, body: .any).called(0)
    }

    @Test("a new entry is written, not edited")
    func newEntryIsWritten() async {
        let (model, service) = makeSUT()
        model.body = "Something worth keeping."

        await model.save()

        verify(service).write(title: .any, body: .any).called(1)
        verify(service).edit(id: .any, title: .any, body: .any).called(0)
    }

    @Test("an entry being edited is edited, not written again")
    func editedEntryIsEdited() async {
        let (model, service) = makeSUT()
        model.edit(Self.entry())
        model.body = "Changed my mind."

        await model.save()

        verify(service).edit(id: .any, title: .any, body: .any).called(1)
        verify(service).write(title: .any, body: .any).called(0)
    }

    @Test("a successful save clears the fields, so the next entry starts empty")
    func successClearsFields() async {
        let (model, _) = makeSUT()
        model.title = "A title"
        model.body = "A body"

        await model.save()

        #expect(model.title.isEmpty)
        #expect(model.body.isEmpty)
        #expect(model.editingId == nil)
    }

    @Test("a failed save keeps the text, which exists nowhere else")
    func failureKeepsTheText() async {
        // Nothing is written to disk — no draft, no cache — so clearing here would destroy it.
        let (model, _) = makeSUT(saveFails: true)
        model.title = "A title"
        model.body = "Something I do not want to lose."

        await model.save()

        #expect(model.body == "Something I do not want to lose.")
        #expect(model.title == "A title")
        #expect(model.errorMessage != nil)
        #expect(model.isSaving == false)
    }

    // MARK: Editing

    @Test("editing loads the entry into the fields")
    func editPopulatesFields() {
        let (model, _) = makeSUT()
        let target = Self.entry(title: "Old title", body: "Old body")

        model.edit(target)

        #expect(model.title == "Old title")
        #expect(model.body == "Old body")
        #expect(model.isEditing)
    }

    @Test("clearing abandons an edit rather than leaving it half-applied")
    func clearAbandonsEdit() {
        let (model, _) = makeSUT()
        model.edit(Self.entry())

        model.clear()

        #expect(model.isEditing == false)
        #expect(model.body.isEmpty)
    }

    // MARK: Listing

    @Test("a failed load says so, so an empty list is not mistaken for an empty journal")
    func failedLoad() async {
        let (model, _) = makeSUT(listFails: true)

        await model.load()

        #expect(model.entries.isEmpty)
        #expect(model.errorMessage != nil)
        #expect(model.isLoading == false)
    }

    @Test("searching asks the server rather than filtering what is already loaded")
    func searchAsksTheServer() async {
        let (model, service) = makeSUT()
        model.query = "pacing"

        await model.search()

        verify(service).list(query: .value("pacing")).called(1)
    }

    // MARK: Deleting

    @Test("deleting removes the entry from the list")
    func deleteRemovesEntry() async {
        let target = Self.entry()
        let (model, _) = makeSUT(entries: [target, Self.entry()])
        await model.load()

        await model.delete(target)

        #expect(model.entries.contains { $0.id == target.id } == false)
    }

    @Test("deleting the entry being edited abandons the edit")
    func deleteAbandonsMatchingEdit() async {
        let target = Self.entry()
        let (model, _) = makeSUT(entries: [target])
        await model.load()
        model.edit(target)

        await model.delete(target)

        #expect(model.isEditing == false)
    }
}
