import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("ModuleAdminViewModel", .serialized)
@MainActor
final class ModuleAdminViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func summary(
        _ title: String,
        hasDraft: Bool = false,
        publishedVersion: Int? = nil
    ) -> ModuleSummaryResponse {
        ModuleSummaryResponse(
            hasDraft: hasDraft,
            moduleId: Constants.moduleId,
            publishedVersion: publishedVersion,
            title: title
        )
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId, id: UUID(), profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        modules: [ModuleSummaryResponse] = [],
        mutationFails: Bool = false
    ) -> (ModuleAdminViewModel, MockAuthoringService) {
        let authoring = MockAuthoringService(policy: .relaxed)
        given(authoring).modules(orgId: .any).willReturn(modules)
        if mutationFails {
            given(authoring).create(orgId: .any, title: .any, summary: .any)
                .willThrow(TestError.unreachable)
            given(authoring).archive(orgId: .any, moduleId: .any).willThrow(TestError.unreachable)
        } else {
            given(authoring).create(orgId: .any, title: .any, summary: .any)
                .willReturn(AuthoredModuleResponse(moduleId: Constants.moduleId, title: "New"))
            given(authoring).archive(orgId: .any, moduleId: .any).willReturn(())
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.authoringService.register { authoring }
        Container.shared.sessionService.register { session }
        return (ModuleAdminViewModel(), authoring)
    }

    @Test("a module needs a title")
    func needsTitle() async {
        let (model, authoring) = makeSUT()
        await model.load()

        await model.create()

        #expect(model.canCreate == false)
        verify(authoring).create(orgId: .any, title: .any, summary: .any).called(0)
    }

    @Test("creating clears the form")
    func createClears() async {
        let (model, _) = makeSUT()
        await model.load()
        model.newTitle = "Pacing"
        model.newSummary = "How to pace a session"

        await model.create()

        #expect(model.newTitle.isEmpty)
        #expect(model.newSummary.isEmpty)
    }

    @Test("a failed creation keeps the typing")
    func failedCreateKeepsTyping() async {
        let (model, _) = makeSUT(mutationFails: true)
        await model.load()
        model.newTitle = "Pacing"

        await model.create()

        #expect(model.newTitle == "Pacing")
        #expect(model.errorMessage != nil)
    }

    @Test("archiving removes it from the list")
    func archiveRemoves() async {
        let existing = Self.summary("Pacing")
        let (model, _) = makeSUT(modules: [existing])
        await model.load()

        await model.archive(existing)

        #expect(model.modules.isEmpty)
    }

    @Test("a failed archive keeps it listed")
    func failedArchive() async {
        let existing = Self.summary("Pacing")
        let (model, _) = makeSUT(modules: [existing], mutationFails: true)
        await model.load()

        await model.archive(existing)

        #expect(model.modules.count == 1)
        #expect(model.errorMessage != nil)
    }

    @Test("never published and published-with-edits are different states, not one badge")
    func distinguishesPublishState() {
        // Otherwise "not published yet" and "published, with unpublished edits" look identical,
        // and they mean very different things to a learner.
        #expect(Self.summary("a", hasDraft: true).isPublished == false)
        #expect(Self.summary("a", hasDraft: true).hasUnpublishedChanges == false)
        #expect(Self.summary("a", hasDraft: true, publishedVersion: 1).hasUnpublishedChanges)
        #expect(Self.summary("a", publishedVersion: 1).hasUnpublishedChanges == false)
    }
}

@Suite("ModuleEditorViewModel", .serialized)
@MainActor
final class ModuleEditorViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func section(_ title: String) -> SectionResponse {
        SectionResponse(body: "Body of \(title)", sectionId: UUID(), title: title)
    }

    nonisolated private static func module(
        draft: VersionResponse? = nil,
        published: VersionResponse? = nil
    ) -> AuthoredModuleResponse {
        AuthoredModuleResponse(
            draft: draft, moduleId: Constants.moduleId, published: published, title: "A module"
        )
    }

    nonisolated private static func version(_ sections: [SectionResponse]) -> VersionResponse {
        VersionResponse(sections: sections, status: "DRAFT", versionId: UUID())
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId, id: UUID(), profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        module: AuthoredModuleResponse? = nil,
        saveFails: Bool = false
    ) -> (ModuleEditorViewModel, MockAuthoringService) {
        let authoring = MockAuthoringService(policy: .relaxed)
        given(authoring).module(orgId: .any, moduleId: .any).willReturn(
            module ?? Self.module(draft: Self.version([Self.section("One")]))
        )
        given(authoring).openDraft(orgId: .any, moduleId: .any).willReturn(
            Self.module(draft: Self.version([Self.section("One")]))
        )
        if saveFails {
            given(authoring).replaceSections(orgId: .any, moduleId: .any, sections: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(authoring).replaceSections(orgId: .any, moduleId: .any, sections: .any)
                .willReturn(Self.module(draft: Self.version([Self.section("One")])))
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.authoringService.register { authoring }
        Container.shared.sessionService.register { session }
        return (ModuleEditorViewModel(moduleId: Constants.moduleId), authoring)
    }

    @Test("a published module with no draft shows its published content, not an empty editor")
    func startsFromPublishedContent() async {
        let published = VersionResponse(
            sections: [Self.section("Live")], status: "PUBLISHED", versionId: UUID()
        )
        let (model, _) = makeSUT(module: Self.module(published: published))

        await model.load()

        #expect(model.sections.map(\.title) == ["Live"])
    }

    @Test("a published module with no draft is not editable until a draft is opened")
    func notEditableUntilDraftOpened() async {
        let published = VersionResponse(status: "PUBLISHED", versionId: UUID())
        let (model, authoring) = makeSUT(module: Self.module(published: published))
        await model.load()

        #expect(model.isEditable == false)
        await model.save()
        verify(authoring).replaceSections(orgId: .any, moduleId: .any, sections: .any).called(0)
    }

    @Test("opening a draft makes it editable")
    func openDraftMakesEditable() async {
        let published = VersionResponse(status: "PUBLISHED", versionId: UUID())
        let (model, _) = makeSUT(module: Self.module(published: published))
        await model.load()

        await model.openDraft()

        #expect(model.isEditable)
    }

    @Test("adding a section marks the editor dirty")
    func addMarksDirty() async {
        let (model, _) = makeSUT()
        await model.load()

        model.addSection()

        #expect(model.sections.count == 2)
        #expect(model.isDirty)
    }

    @Test("saving sends every section, because the endpoint replaces the list")
    func savesEverySection() async {
        let captured = CapturedSections()
        let authoring = MockAuthoringService(policy: .relaxed)
        given(authoring).module(orgId: .any, moduleId: .any).willReturn(
            Self.module(draft: Self.version([Self.section("One"), Self.section("Two")]))
        )
        given(authoring).replaceSections(orgId: .any, moduleId: .any, sections: .any)
            .willProduce { _, _, sections in
                captured.value = sections
                return Self.module(draft: Self.version([]))
            }
        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())
        Container.shared.authoringService.register { authoring }
        Container.shared.sessionService.register { session }

        let model = ModuleEditorViewModel(moduleId: Constants.moduleId)
        await model.load()
        await model.save()

        #expect(captured.value?.count == 2)
    }

    @Test("reordering changes the order that is sent, since position is the whole meaning")
    func reorderingChangesOrder() async {
        let captured = CapturedSections()
        let authoring = MockAuthoringService(policy: .relaxed)
        given(authoring).module(orgId: .any, moduleId: .any).willReturn(
            Self.module(draft: Self.version([Self.section("One"), Self.section("Two")]))
        )
        given(authoring).replaceSections(orgId: .any, moduleId: .any, sections: .any)
            .willProduce { _, _, sections in
                captured.value = sections
                return Self.module(draft: Self.version([]))
            }
        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())
        Container.shared.authoringService.register { authoring }
        Container.shared.sessionService.register { session }

        let model = ModuleEditorViewModel(moduleId: Constants.moduleId)
        await model.load()
        model.moveSection(from: IndexSet(integer: 1), to: 0)
        await model.save()

        #expect(captured.value?.map(\.title) == ["Two", "One"])
    }

    @Test("a section with a blank heading is refused before the request")
    func blankHeadingRefused() async {
        let (model, authoring) = makeSUT()
        await model.load()
        model.addSection()

        await model.save()

        #expect(model.errorMessage != nil)
        verify(authoring).replaceSections(orgId: .any, moduleId: .any, sections: .any).called(0)
    }

    @Test("a successful save clears dirty")
    func saveClearsDirty() async {
        let (model, _) = makeSUT()
        await model.load()
        model.updateSection(id: model.sections[0].id, body: "Changed")

        await model.save()

        #expect(model.isDirty == false)
    }

    @Test("a failed save stays dirty, so nothing suggests the work was stored")
    func failedSaveStaysDirty() async {
        let (model, _) = makeSUT(saveFails: true)
        await model.load()
        model.updateSection(id: model.sections[0].id, body: "Changed")

        await model.save()

        #expect(model.isDirty)
        #expect(model.errorMessage != nil)
    }

    /// Mockable's `willProduce` is a synchronous closure, so this cannot be an actor. The lock is
    /// what makes writing from whichever thread the mock calls on safe.
    private final class CapturedSections: @unchecked Sendable {
        private let lock = NSLock()
        private var stored: [SectionInput]?

        var value: [SectionInput]? {
            get { lock.withLock { stored } }
            set { lock.withLock { stored = newValue } }
        }
    }
}
