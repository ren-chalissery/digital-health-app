package io.simplicity.training.admin

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.AuthoredModuleResponse
import io.simplicity.training.api.models.SectionInput
import io.simplicity.training.services.AuthoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ModuleEditorState(
    val module: AuthoredModuleResponse? = null,
    val sections: List<SectionInput> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * A published version is immutable. Editing works on a draft, and there may not be one yet.
     *
     * Surfacing this rather than hiding it is deliberate: an administrator who believes they are
     * editing what learners see would be surprised twice — once when nothing changes, and again
     * when publishing changes everything at once.
     */
    val hasDraft: Boolean get() = module?.draft != null

    val isPublished: Boolean get() = module?.published != null

    val canPublish: Boolean get() = hasDraft && sections.isNotEmpty() && !isBusy
}

class ModuleEditorViewModel(
    private val authoring: AuthoringService,
    private val orgId: UUID,
    private val moduleId: UUID,
) : ViewModel() {

    private val _state = MutableStateFlow(ModuleEditorState())
    val state: StateFlow<ModuleEditorState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val module = authoring.module(orgId, moduleId)
            _state.update {
                it.copy(
                    module = module,
                    sections = module.draft?.sections?.map(::toInput).orEmpty(),
                    hasUnsavedChanges = false,
                    isBusy = false,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    /** Opening a draft copies the published version; it does not modify it. */
    suspend fun openDraft() {
        act { authoring.openDraft(orgId, moduleId) }
    }

    fun addSection(title: String, body: String) = _state.update {
        it.copy(
            // No position field: order is the list's order, which the server honours.
            sections = it.sections + SectionInput(title = title, body = body),
            hasUnsavedChanges = true,
        )
    }

    fun removeSection(index: Int) = _state.update {
        it.copy(sections = it.sections.filterIndexed { i, _ -> i != index }, hasUnsavedChanges = true)
    }

    suspend fun saveSections() {
        act { authoring.replaceSections(orgId, moduleId, _state.value.sections) }
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    /**
     * @param supersedeCompletions when the change is substantive enough that a clinician who
     *   already finished should do it again. That is a judgement only the author can make.
     */
    suspend fun publish(supersedeCompletions: Boolean) {
        if (!_state.value.canPublish) return
        act { authoring.publish(orgId, moduleId, supersedeCompletions) }
    }

    private suspend fun act(block: suspend () -> AuthoredModuleResponse) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val module = block()
            _state.update {
                it.copy(
                    module = module,
                    sections = module.draft?.sections?.map(::toInput) ?: it.sections,
                    isBusy = false,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    private fun toInput(section: io.simplicity.training.api.models.SectionResponse) = SectionInput(
        title = section.title.orEmpty(),
        body = section.body,
        mediaAssetId = section.mediaAssetId,
    )
}
