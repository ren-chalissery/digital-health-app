package io.simplicity.training.assistant

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.AnswerResponse
import io.simplicity.training.api.models.CitationResponse
import io.simplicity.training.services.AssistantService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class AskState(
    val question: String = "",
    val answer: AnswerResponse? = null,
    val isAsking: Boolean = false,
    val failed: Boolean = false,
) {
    val canAsk: Boolean get() = question.isNotBlank() && !isAsking

    val citations: List<CitationResponse> get() = answer?.citations.orEmpty()

    /**
     * A refusal is a normal answer, not a failure. The training does not cover everything, and a
     * clinical question it does not address belongs with a supervisor rather than with a guess.
     */
    val wasRefused: Boolean get() = answer != null && answer.answered != true
}

/**
 * Asking about the training.
 *
 * This module must never depend on `:reflect`. The assistant reads training content and a
 * clinician's journal is private to them — the boundary is a privacy property, and there is a test
 * asserting the dependency does not exist.
 */
class AskViewModel(
    private val assistant: AssistantService,
    private val orgId: UUID,
) : ViewModel() {

    private val _state = MutableStateFlow(AskState())
    val state: StateFlow<AskState> = _state.asStateFlow()

    fun questionChanged(value: String) = _state.update { it.copy(question = value) }

    suspend fun ask() {
        val current = _state.value
        if (!current.canAsk) return

        _state.update { it.copy(isAsking = true, failed = false, answer = null) }
        try {
            val answer = assistant.ask(orgId, current.question)
            _state.update { it.copy(answer = answer, isAsking = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isAsking = false, failed = true) }
        }
    }

    fun reset() = _state.update { AskState() }
}
