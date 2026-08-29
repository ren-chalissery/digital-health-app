package io.simplicity.training.model.request;

import io.simplicity.training.model.enums.OrgRole;
import jakarta.validation.constraints.NotNull;

public record ChangeOrgRoleRequest(@NotNull OrgRole orgRole) {}
