package io.simplicity.training.admin

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.OrgMemberResponse
import io.simplicity.training.services.OrganisationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class SettingsState(
    val isOrgAdmin: Boolean = false,
    /**
     * True when this person is the only administrator, in which case leaving **archives the
     * organisation**. The server does that deliberately rather than leaving it unadministered, so
     * the confirmation has to say so before they commit, not afterwards.
     *
     * False when it cannot be determined. A wrong claim that nothing will happen is milder than a
     * wrong claim that everything will.
     */
    val willArchiveOnLeave: Boolean = false,
    val didLeave: Boolean = false,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
)

class SettingsViewModel(
    private val organisations: OrganisationService,
    private val orgId: UUID,
    private val currentUserId: UUID?,
    private val isOrgAdmin: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(isOrgAdmin = isOrgAdmin))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    suspend fun load() {
        determineWhetherLeavingArchives()
    }

    /**
     * Only an administrator can be the last one, and only an administrator may read the member
     * list, so an ordinary member never provokes the request.
     */
    private suspend fun determineWhetherLeavingArchives() {
        if (!isOrgAdmin || currentUserId == null) {
            _state.update { it.copy(willArchiveOnLeave = false) }
            return
        }
        try {
            val administrators = organisations.members(orgId)
                .filter { it.orgRole == OrgMemberResponse.OrgRole.ORG_ADMIN }
            _state.update {
                it.copy(
                    willArchiveOnLeave = administrators.size == 1 &&
                        administrators.first().userId == currentUserId,
                )
            }
        } catch (e: Exception) {
            // Silent: this is a detail of a warning, not something they asked for, and an error
            // banner on opening Settings would be baffling.
            _state.update { it.copy(willArchiveOnLeave = false) }
        }
    }

    suspend fun leave() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            organisations.leave(orgId)
            _state.update { it.copy(isBusy = false, didLeave = true) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }
}
