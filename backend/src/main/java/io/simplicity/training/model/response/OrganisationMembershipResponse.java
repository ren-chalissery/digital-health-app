package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import java.util.List;
import java.util.UUID;

/** One organisation the caller belongs to, with their role and the teams they are in. */
public record OrganisationMembershipResponse(
    UUID orgId,
    String name,
    String slug,
    OrganisationType organisationType,
    OrgRole orgRole,
    List<TeamMembershipResponse> teams) {}
