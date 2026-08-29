package io.simplicity.training.model.request;

import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.TeamRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateInvitationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotNull OrgRole orgRole,
    /** Optional. When set, accepting the invitation also joins this team. */
    UUID teamId,
    TeamRole teamRole) {}
