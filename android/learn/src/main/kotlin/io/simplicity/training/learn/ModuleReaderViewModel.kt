package io.simplicity.training.learn

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.LearnerModuleResponse
import io.simplicity.training.api.models.SectionResponse
import io.simplicity.training.services.LearningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ModuleReaderState(
    val module: LearnerModuleResponse? = null,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
) {
    val sections: List<SectionResponse> get() = module?.sections.orEmpty()

    private val readIds: Set<UUID> get() = module?.completedSectionIds.orEmpty().toSet()

    fun isRead(section: SectionResponse): Boolean = section.sectionId in readIds

    /**
     * False for a module with no sections.
     *
     * `all {}` is vacuously true on an empty list, which would unlock the quiz on a module with
     * nothing in it. The emptiness check is the whole point of this property.
     */
    val allSectionsRead: Boolean
        get() = sections.isNotEmpty() && sections.all(::isRead)

    /** Where to resume. A module runs to several sections and nobody finishes one in a sitting. */
    val firstUnreadSectionId: UUID?
        get() = sections.firstOrNull { !isRead(it) }?.sectionId

    /** The server's rule, mirrored so the button is not offered and then refused. */
    val canTakeQuiz: Boolean
        get() = module?.hasQuiz == true && allSectionsRead
}

class ModuleReaderViewModel(
    private val learning: LearningService,
    private val orgId: UUID,
    private val moduleId: UUID,
) : ViewModel() {

    private val _state = MutableStateFlow(ModuleReaderState())
    val state: StateFlow<ModuleReaderState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val module = learning.module(orgId, moduleId)
            _state.update { it.copy(module = module, isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    /**
     * Marking is the server's business: it answers with the module, and that answer replaces what
     * is held. Nothing is recomputed locally, so the phone and the web cannot disagree about how
     * far somebody has got.
     */
    suspend fun markRead(sectionId: UUID) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        try {
            val updated = learning.completeSection(orgId, sectionId)
            _state.update { it.copy(module = updated, isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }
}
