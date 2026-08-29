package io.simplicity.training.model.entity;

import io.simplicity.training.model.enums.MembershipStatus;
import io.simplicity.training.model.enums.OrgRole;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Ties a user to an organisation with a role. Foreign keys are plain UUID columns rather than JPA
 * associations, which keeps org-scoped queries explicit and avoids lazy-loading surprises in the
 * authorisation path.
 */
@Entity
@Table(name = "org_membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgMembership {

  @EmbeddedId private Key id;

  @Enumerated(EnumType.STRING)
  @Column(name = "org_role", nullable = false)
  private OrgRole orgRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private MembershipStatus status = MembershipStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  public UUID getUserId() {
    return id == null ? null : id.getUserId();
  }

  public UUID getOrgId() {
    return id == null ? null : id.getOrgId();
  }

  @Embeddable
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;
  }

  public static OrgMembership of(UUID userId, UUID orgId, OrgRole role) {
    return OrgMembership.builder().id(new Key(userId, orgId)).orgRole(role).build();
  }
}
