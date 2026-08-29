import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class ModuleReaderViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.learningService) private var learning
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let moduleId: UUID

    public private(set) var isLoading = false
    public private(set) var isSaving = false
    public private(set) var errorMessage: String?
    public private(set) var module: LearnerModuleResponse?
    public private(set) var quiz: QuizResponse?

    public var statusLabel: String {
        (module?.status ?? .notStarted).label
    }

    public var sections: [SectionResponse] {
        module?.sections ?? []
    }

    /// False for a module with no sections. A vacuous "all read" would unlock a quiz on an empty
    /// module.
    public var allSectionsRead: Bool {
        !sections.isEmpty && sections.allSatisfy(isRead)
    }

    /// Where to resume. A module is several sections long and nobody finishes one in a sitting.
    public var firstUnreadSectionId: UUID? {
        sections.first { !isRead($0) }?.sectionId
    }

    private var completedSectionIds: Set<UUID> {
        Set(module?.completedSectionIds ?? [])
    }

    // MARK: Init

    public init(moduleId: UUID) {
        self.moduleId = moduleId
    }

    // MARK: Functions

    public func isRead(_ section: SectionResponse) -> Bool {
        guard let sectionId = section.sectionId else { return false }
        return completedSectionIds.contains(sectionId)
    }

    public func load() async {
        guard let orgId = await session.current?.activeOrganisationId else {
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let loaded = try await learning.module(orgId: orgId, moduleId: moduleId)
            module = loaded
            if loaded.hasQuiz == true {
                quiz = try await learning.quiz(orgId: orgId, moduleId: moduleId)
            }
        } catch {
            errorMessage = String(localized: "reader_load_failed", bundle: .module)
        }
    }

    public func markRead(_ section: SectionResponse) async {
        guard
            let orgId = await session.current?.activeOrganisationId,
            let sectionId = section.sectionId,
            !isSaving
        else {
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            // The response carries the recomputed status, so finishing the last section shows as
            // complete without a second call.
            module = try await learning.completeSection(orgId: orgId, sectionId: sectionId)
        } catch {
            // Progress that was not recorded must not look recorded. A clinician who believes
            // they finished a module and did not is worse off than one who knows to try again.
            errorMessage = String(localized: "reader_progress_failed", bundle: .module)
        }
    }

    /// Called by the quiz when passing recomputes the module's status.
    public func moduleChanged(_ updated: LearnerModuleResponse) {
        module = updated
    }
}
