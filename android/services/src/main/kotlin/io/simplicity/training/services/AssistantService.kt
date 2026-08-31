package io.simplicity.training.services

import io.simplicity.training.api.apis.AssistantApi
import io.simplicity.training.api.models.AnswerResponse
import io.simplicity.training.api.models.AskRequest
import java.util.UUID

/**
 * Answers questions the training covers, and refuses everything else.
 *
 * The refusal is a normal response rather than an error: `answered = false` with no citations. A
 * clinical question the training does not address must be sent to supervision, not guessed at.
 */
interface AssistantService {
    suspend fun ask(orgId: UUID, question: String): AnswerResponse
}

class AssistantServiceImpl(private val api: AssistantApi) : AssistantService {
    override suspend fun ask(orgId: UUID, question: String) =
        api.askAssistant(orgId, AskRequest(question)).unwrap()
}
