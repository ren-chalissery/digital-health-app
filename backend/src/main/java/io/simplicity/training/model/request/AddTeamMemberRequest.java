package io.simplicity.training.model.request;

import io.simplicity.training.model.enums.TeamRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddTeamMemberRequest(@NotNull UUID userId, @NotNull TeamRole teamRole) {}
