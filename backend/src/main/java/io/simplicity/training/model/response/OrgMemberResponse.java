package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.MembershipStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record OrgMemberResponse(
    UUID userId,
    String email,
    String fullName,
    String professionalRole,
    OrgRole orgRole,
    MembershipStatus membershipStatus,
    UserStatus userStatus,
    Instant joinedAt) {}
