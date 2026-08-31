package io.simplicity.training.reflect

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.ReflectionResponse
import io.simplicity.training.services.ReflectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ReflectState(
    val entries: List<ReflectionResponse> = emptyList(),
    val query: String = "",
    val title: String = "",
    val body: String = "",
    val editingId: UUID? = null,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
) {
    /** Warnings are computed as they type, from what is on screen. Nothing is sent to compute them. */
    val warnings: List<IdentifierWarning> get() = Identifiers.find("$title $body")

    val canSave: Boolean get() = body.isNotBlank() && !isBusy

    val isEditing: Boolean get() = editingId != null
}

/**
 * The journal.
 *
 * Nothing is written to disk before it is posted and no draft is kept, which is the same position
 * iOS takes: a half-written reflection sitting in local storage is a copy of clinical thinking
 * nobody asked to keep.
 */
class ReflectViewModel(private val reflections: ReflectionService) : ViewModel() {

    private val _state = MutableStateFlow(ReflectState())
    val state: StateFlow<ReflectState> = _state.asStateFlow()

    fun queryChanged(value: String) = _state.update { it.copy(query = value) }
    fun titleChanged(value: String) = _state.update { it.copy(title = value) }
    fun bodyChanged(value: String) = _state.update { it.copy(body = value) }

    suspend fun load() = fetch(_state.value.query)

    suspend fun search() = fetch(_state.value.query)

    private suspend fun fetch(query: String) {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val entries = reflections.list(query)
            _state.update { it.copy(entries = entries, isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    fun edit(entry: ReflectionResponse) = _state.update {
        it.copy(editingId = entry.id, title = entry.title.orEmpty(), body = entry.body.orEmpty())
    }

    fun cancelEditing() = _state.update { it.copy(editingId = null, title = "", body = "") }

    suspend fun save() {
        val current = _state.value
        if (!current.canSave) return

        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val editing = current.editingId
            if (editing == null) {
                reflections.write(current.title.ifBlank { null }, current.body)
            } else {
                reflections.edit(editing, current.title.ifBlank { null }, current.body)
            }
            _state.update { it.copy(isBusy = false, editingId = null, title = "", body = "") }
            fetch(_state.value.query)
        } catch (e: Exception) {
            // The writing stays on screen. Losing a reflection to a failed save would be the worst
            // thing this screen could do.
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    suspend fun delete(id: UUID) {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            reflections.delete(id)
            _state.update { it.copy(isBusy = false) }
            fetch(_state.value.query)
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }
}
