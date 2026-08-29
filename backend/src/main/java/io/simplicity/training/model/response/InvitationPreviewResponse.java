package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.OrgRole;

/**
 * What somebody holding an invitation link sees before they have an account.
 *
 * <p>This endpoint is public, so it carries only what the recipient needs in order to decide
 * whether to accept: which organisation, in what role. No member list, no ids, no inviter's
 * address.
 */
public record InvitationPreviewResponse(
    String organisationName, String teamName, OrgRole orgRole, String email, boolean valid) {}
