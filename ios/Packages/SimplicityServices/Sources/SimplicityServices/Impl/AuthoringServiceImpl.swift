import Foundation
import SimplicityApi

public final class AuthoringServiceImpl: AuthoringService {

    // MARK: Types

    public typealias Modules = @Sendable (UUID) async throws -> [ModuleSummaryResponse]
    public typealias Module = @Sendable (UUID, UUID) async throws -> AuthoredModuleResponse
    public typealias Create = @Sendable (UUID, CreateModuleRequest) async throws
        -> AuthoredModuleResponse
    public typealias OpenDraft = @Sendable (UUID, UUID) async throws -> AuthoredModuleResponse
    public typealias ReplaceSections = @Sendable (UUID, UUID, ReplaceSectionsRequest) async throws
        -> AuthoredModuleResponse
    public typealias ReplaceQuiz = @Sendable (UUID, UUID, ReplaceQuizRequest) async throws
        -> AuthoredModuleResponse
    public typealias Publish = @Sendable (UUID, UUID, PublishRequest) async throws
        -> AuthoredModuleResponse
    public typealias AssignTeams = @Sendable (UUID, UUID, AssignTeamsRequest) async throws
        -> AuthoredModuleResponse
    public typealias Archive = @Sendable (UUID, UUID) async throws -> Void

    // MARK: Properties

    private let modulesCall: Modules
    private let moduleCall: Module
    private let createCall: Create
    private let openDraftCall: OpenDraft
    private let replaceSectionsCall: ReplaceSections
    private let replaceQuizCall: ReplaceQuiz
    private let publishCall: Publish
    private let assignTeamsCall: AssignTeams
    private let archiveCall: Archive

    // MARK: Init

    public init(
        modules: @escaping Modules = { orgId in
            try await ModulesAPI.listModules(orgId: orgId)
        },
        module: @escaping Module = { orgId, moduleId in
            try await ModulesAPI.getModule(orgId: orgId, moduleId: moduleId)
        },
        create: @escaping Create = { orgId, request in
            try await ModulesAPI.createModule(orgId: orgId, createModuleRequest: request)
        },
        openDraft: @escaping OpenDraft = { orgId, moduleId in
            try await ModulesAPI.openModuleDraft(orgId: orgId, moduleId: moduleId)
        },
        replaceSections: @escaping ReplaceSections = { orgId, moduleId, request in
            try await ModulesAPI.replaceModuleSections(
                orgId: orgId, moduleId: moduleId, replaceSectionsRequest: request
            )
        },
        replaceQuiz: @escaping ReplaceQuiz = { orgId, moduleId, request in
            try await ModulesAPI.replaceModuleQuiz(
                orgId: orgId, moduleId: moduleId, replaceQuizRequest: request
            )
        },
        publish: @escaping Publish = { orgId, moduleId, request in
            try await ModulesAPI.publishModule(
                orgId: orgId, moduleId: moduleId, publishRequest: request
            )
        },
        assignTeams: @escaping AssignTeams = { orgId, moduleId, request in
            try await ModulesAPI.assignModuleToTeams(
                orgId: orgId, moduleId: moduleId, assignTeamsRequest: request
            )
        },
        archive: @escaping Archive = { orgId, moduleId in
            try await ModulesAPI.archiveModule(orgId: orgId, moduleId: moduleId)
        }
    ) {
        self.modulesCall = modules
        self.moduleCall = module
        self.createCall = create
        self.openDraftCall = openDraft
        self.replaceSectionsCall = replaceSections
        self.replaceQuizCall = replaceQuiz
        self.publishCall = publish
        self.assignTeamsCall = assignTeams
        self.archiveCall = archive
    }

    // MARK: Functions

    public func modules(orgId: UUID) async throws -> [ModuleSummaryResponse] {
        try await modulesCall(orgId)
    }

    public func module(orgId: UUID, moduleId: UUID) async throws -> AuthoredModuleResponse {
        try await moduleCall(orgId, moduleId)
    }

    public func create(
        orgId: UUID,
        title: String,
        summary: String?
    ) async throws -> AuthoredModuleResponse {
        let trimmed = summary?.trimmingCharacters(in: .whitespacesAndNewlines)
        return try await createCall(
            orgId,
            CreateModuleRequest(
                summary: trimmed?.isEmpty == false ? trimmed : nil,
                title: title.trimmingCharacters(in: .whitespaces)
            )
        )
    }

    public func openDraft(orgId: UUID, moduleId: UUID) async throws -> AuthoredModuleResponse {
        try await openDraftCall(orgId, moduleId)
    }

    public func replaceSections(
        orgId: UUID,
        moduleId: UUID,
        sections: [SectionInput]
    ) async throws -> AuthoredModuleResponse {
        try await replaceSectionsCall(
            orgId, moduleId, ReplaceSectionsRequest(sections: sections)
        )
    }

    public func replaceQuiz(
        orgId: UUID,
        moduleId: UUID,
        questions: [QuestionInput]
    ) async throws -> AuthoredModuleResponse {
        try await replaceQuizCall(orgId, moduleId, ReplaceQuizRequest(questions: questions))
    }

    public func publish(
        orgId: UUID,
        moduleId: UUID,
        supersedesCompletions: Bool
    ) async throws -> AuthoredModuleResponse {
        try await publishCall(
            orgId, moduleId, PublishRequest(supersedesCompletions: supersedesCompletions)
        )
    }

    public func assignTeams(
        orgId: UUID,
        moduleId: UUID,
        teamIds: [UUID]
    ) async throws -> AuthoredModuleResponse {
        // An empty array is meaningful: it unassigns the module from every team.
        try await assignTeamsCall(orgId, moduleId, AssignTeamsRequest(teamIds: teamIds))
    }

    public func archive(orgId: UUID, moduleId: UUID) async throws {
        try await archiveCall(orgId, moduleId)
    }
}
