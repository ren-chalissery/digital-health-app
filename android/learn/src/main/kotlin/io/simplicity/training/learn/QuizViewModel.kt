package io.simplicity.training.learn

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.AnswerInput
import io.simplicity.training.api.models.AttemptResultResponse
import io.simplicity.training.api.models.LearnerModuleResponse
import io.simplicity.training.api.models.MarkedQuestion
import io.simplicity.training.api.models.QuizQuestionResponse
import io.simplicity.training.api.models.QuizResponse
import io.simplicity.training.services.LearningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class QuizState(
    val quiz: QuizResponse? = null,
    val chosen: Map<UUID, UUID> = emptyMap(),
    val result: AttemptResultResponse? = null,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
) {
    val questions: List<QuizQuestionResponse> get() = quiz?.questions.orEmpty()

    /**
     * False for a quiz with no questions, for the same reason the reader guards an empty module:
     * `all {}` is vacuously true, and submitting an empty attempt is not a pass.
     */
    val allAnswered: Boolean
        get() = questions.isNotEmpty() && questions.all { it.questionId in chosen }

    fun feedback(questionId: UUID): MarkedQuestion? =
        result?.questions?.firstOrNull { it.questionId == questionId }
}

class QuizViewModel(
    private val learning: LearningService,
    private val orgId: UUID,
    private val moduleId: UUID,
    /** Passing recomputes the module's status, and the reader needs to know. */
    private val onModuleChanged: (LearnerModuleResponse) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val quiz = learning.quiz(orgId, moduleId)
            _state.update { it.copy(quiz = quiz, isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    fun choose(questionId: UUID, optionId: UUID) {
        // Changing an answer before submitting is ordinary, so the previous result is cleared —
        // leaving old feedback beside a new choice would be actively misleading.
        _state.update { it.copy(chosen = it.chosen + (questionId to optionId), result = null) }
    }

    suspend fun submit() {
        val current = _state.value
        if (!current.allAnswered || current.isBusy) return

        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val answers = current.chosen.map { (question, option) ->
                AnswerInput(optionId = option, questionId = question)
            }
            val result = learning.submitAttempt(orgId, moduleId, answers)
            _state.update { it.copy(result = result, isBusy = false) }

            // Whether this counts as completion is the server's answer, never a local tally.
            if (result.passed == true) {
                onModuleChanged(learning.module(orgId, moduleId))
            }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }
}
