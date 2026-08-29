package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.InvitationStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.TeamRole;
import java.time.Instant;
import java.util.UUID;

/** An invitation as an administrator sees it. Never carries the token. */
public record InvitationResponse(
    UUID id,
    String email,
    OrgRole orgRole,
    UUID teamId,
    String teamName,
    TeamRole teamRole,
    InvitationStatus status,
    Instant expiresAt,
    Instant createdAt) {}
