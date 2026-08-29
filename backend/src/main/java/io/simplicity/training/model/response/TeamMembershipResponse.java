package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.TeamRole;
import java.util.UUID;

public record TeamMembershipResponse(UUID teamId, String name, TeamRole teamRole) {}
