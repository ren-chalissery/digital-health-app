package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.TeamRole;
import java.time.Instant;
import java.util.UUID;

public record TeamMemberDetailResponse(
    UUID userId,
    String email,
    String fullName,
    String professionalRole,
    TeamRole teamRole,
    Instant joinedAt) {}
