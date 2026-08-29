package io.simplicity.training.model.enums;

/**
 * Role within a single team. A team administrator manages that team's membership only; authoring
 * training content is deliberately an organisation-level capability, so a team lead cannot delete
 * modules other teams depend on.
 */
public enum TeamRole {
  TEAM_ADMIN,
  TEAM_MEMBER
}
