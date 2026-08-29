package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.PlatformRole;
import io.simplicity.training.model.enums.UserStatus;
import java.util.List;
import java.util.UUID;

/**
 * The first call every client makes after signing in.
 *
 * <p>{@code profileCompleted} and an empty {@code organisations} list are what drive onboarding:
 * clients show the profile wizard while the former is false, and the create-organisation screen
 * while the latter is empty. Keeping both decisions server-side means three independently written
 * clients cannot disagree about when onboarding is finished.
 *
 * <p>{@code activeOrganisationId} is which of the memberships the clinician is currently working
 * in. It is always one of {@code organisations}, or null when there are none, so a client can
 * follow it without checking.
 */
public record CurrentUserResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String professionalRole,
    boolean profileCompleted,
    UserStatus status,
    PlatformRole platformRole,
    List<OrganisationMembershipResponse> organisations,
    UUID activeOrganisationId) {}
