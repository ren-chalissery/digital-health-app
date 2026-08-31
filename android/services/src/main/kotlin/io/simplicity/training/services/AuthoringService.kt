package io.simplicity.training.services

import io.simplicity.training.api.apis.ModulesApi
import io.simplicity.training.api.models.AssignTeamsRequest
import io.simplicity.training.api.models.AuthoredModuleResponse
import io.simplicity.training.api.models.CreateModuleRequest
import io.simplicity.training.api.models.ModuleSummaryResponse
import io.simplicity.training.api.models.PublishRequest
import io.simplicity.training.api.models.ReplaceQuizRequest
import io.simplicity.training.api.models.ReplaceSectionsRequest
import io.simplicity.training.api.models.SectionInput
import java.util.UUID

/**
 * Authoring a module.
 *
 * A published version is immutable: editing opens a draft, and publishing replaces what learners
 * see. That is the server's model and the client does not soften it — an administrator who thinks
 * they are editing the live version would be surprised twice, once when nothing changed and again
 * when it all did.
 */
interface AuthoringService {
    suspend fun modules(orgId: UUID): List<ModuleSummaryResponse>
    suspend fun module(orgId: UUID, moduleId: UUID): AuthoredModuleResponse
    suspend fun create(orgId: UUID, title: String, summary: String?): AuthoredModuleResponse
    suspend fun openDraft(orgId: UUID, moduleId: UUID): AuthoredModuleResponse
    suspend fun replaceSections(orgId: UUID, moduleId: UUID, sections: List<SectionInput>): AuthoredModuleResponse
    suspend fun replaceQuiz(orgId: UUID, moduleId: UUID, request: ReplaceQuizRequest): AuthoredModuleResponse
    suspend fun publish(orgId: UUID, moduleId: UUID, supersedeCompletions: Boolean): AuthoredModuleResponse
    suspend fun assignTeams(orgId: UUID, moduleId: UUID, teamIds: List<UUID>): AuthoredModuleResponse
}

class AuthoringServiceImpl(private val api: ModulesApi) : AuthoringService {

    override suspend fun modules(orgId: UUID) = api.listModules(orgId).unwrap()

    override suspend fun module(orgId: UUID, moduleId: UUID) = api.getModule(orgId, moduleId).unwrap()

    override suspend fun create(orgId: UUID, title: String, summary: String?) =
        api.createModule(orgId, CreateModuleRequest(title = title, summary = summary)).unwrap()

    override suspend fun openDraft(orgId: UUID, moduleId: UUID) =
        api.openModuleDraft(orgId, moduleId).unwrap()

    override suspend fun replaceSections(orgId: UUID, moduleId: UUID, sections: List<SectionInput>) =
        api.replaceModuleSections(orgId, moduleId, ReplaceSectionsRequest(sections)).unwrap()

    override suspend fun replaceQuiz(orgId: UUID, moduleId: UUID, request: ReplaceQuizRequest) =
        api.replaceModuleQuiz(orgId, moduleId, request).unwrap()

    override suspend fun publish(orgId: UUID, moduleId: UUID, supersedeCompletions: Boolean) =
        api.publishModule(orgId, moduleId, PublishRequest(supersedeCompletions)).unwrap()

    override suspend fun assignTeams(orgId: UUID, moduleId: UUID, teamIds: List<UUID>) =
        api.assignModuleToTeams(orgId, moduleId, AssignTeamsRequest(teamIds)).unwrap()
}
