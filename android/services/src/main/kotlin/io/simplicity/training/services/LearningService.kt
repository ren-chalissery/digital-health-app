package io.simplicity.training.services

import io.simplicity.training.api.apis.LearningApi
import io.simplicity.training.api.models.AnswerInput
import io.simplicity.training.api.models.AssignedModuleResponse
import io.simplicity.training.api.models.AttemptResultResponse
import io.simplicity.training.api.models.LearnerModuleResponse
import io.simplicity.training.api.models.PlaybackResponse
import io.simplicity.training.api.models.QuizResponse
import io.simplicity.training.api.models.SubmitAttemptRequest
import java.util.UUID

/**
 * Everything the Learn screens need from the API.
 *
 * Nothing here computes progress. Status, completion and marking are all the server's answers,
 * which is what keeps the app and the web agreeing about the same clinician.
 */
interface LearningService {
    suspend fun assignedModules(orgId: UUID): List<AssignedModuleResponse>
    suspend fun module(orgId: UUID, moduleId: UUID): LearnerModuleResponse
    suspend fun quiz(orgId: UUID, moduleId: UUID): QuizResponse
    suspend fun completeSection(orgId: UUID, sectionId: UUID): LearnerModuleResponse
    suspend fun submitAttempt(orgId: UUID, moduleId: UUID, answers: List<AnswerInput>): AttemptResultResponse
    suspend fun playback(orgId: UUID, assetId: UUID): PlaybackResponse
}

class LearningServiceImpl(private val api: LearningApi) : LearningService {

    override suspend fun assignedModules(orgId: UUID) = api.listAssignedModules(orgId).unwrap()

    override suspend fun module(orgId: UUID, moduleId: UUID) = api.readModule(orgId, moduleId).unwrap()

    override suspend fun quiz(orgId: UUID, moduleId: UUID) = api.getQuiz(orgId, moduleId).unwrap()

    override suspend fun completeSection(orgId: UUID, sectionId: UUID) =
        api.completeSection(orgId, sectionId).unwrap()

    override suspend fun submitAttempt(orgId: UUID, moduleId: UUID, answers: List<AnswerInput>) =
        api.submitQuizAttempt(orgId, moduleId, SubmitAttemptRequest(answers)).unwrap()

    override suspend fun playback(orgId: UUID, assetId: UUID) = api.getPlaybackUrl(orgId, assetId).unwrap()
}

/** Anything not finished, including a module that changed substantively and has come back around. */
val AssignedModuleResponse.isOutstanding: Boolean
    get() = status != AssignedModuleResponse.Status.COMPLETED
