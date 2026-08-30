import Foundation
import Mockable
import SimplicityApi

/// Writing training modules.
///
/// A published version is immutable. Editing means opening a draft, changing it, and publishing
/// again — which is why there is an `openDraft` here and no "update module".
@Mockable
public protocol AuthoringService: AnyObject, Sendable {
    func modules(orgId: UUID) async throws -> [ModuleSummaryResponse]
    func module(orgId: UUID, moduleId: UUID) async throws -> AuthoredModuleResponse
    func create(orgId: UUID, title: String, summary: String?) async throws -> AuthoredModuleResponse
    func openDraft(orgId: UUID, moduleId: UUID) async throws -> AuthoredModuleResponse

    /// Replaces the whole list. Editing one section means sending them all.
    func replaceSections(
        orgId: UUID,
        moduleId: UUID,
        sections: [SectionInput]
    ) async throws -> AuthoredModuleResponse

    /// Replaces the whole quiz. An empty array removes it.
    func replaceQuiz(
        orgId: UUID,
        moduleId: UUID,
        questions: [QuestionInput]
    ) async throws -> AuthoredModuleResponse

    /// `supersedesCompletions` decides whether everybody who finished this module has to do it
    /// again. A corrected typo should not; changed guidance should.
    func publish(
        orgId: UUID,
        moduleId: UUID,
        supersedesCompletions: Bool
    ) async throws -> AuthoredModuleResponse

    /// An empty array unassigns the module from every team.
    func assignTeams(
        orgId: UUID,
        moduleId: UUID,
        teamIds: [UUID]
    ) async throws -> AuthoredModuleResponse

    func archive(orgId: UUID, moduleId: UUID) async throws
}

public extension ModuleSummaryResponse {

    var isPublished: Bool {
        publishedVersion != nil
    }

    /// Published, and edited since. A learner is still seeing the older version, which is worth
    /// saying out loud rather than leaving an author to infer from two separate badges.
    var hasUnpublishedChanges: Bool {
        hasDraft == true && isPublished
    }
}

public extension AuthoredModuleResponse {

    var hasDraft: Bool {
        draft != nil
    }

    var isPublished: Bool {
        published != nil
    }

    /// Published, and then edited since. A learner is still seeing the older version, which is
    /// worth saying out loud rather than leaving an author to infer.
    var hasUnpublishedChanges: Bool {
        hasDraft && isPublished
    }
}
