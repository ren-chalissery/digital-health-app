package io.simplicity.training.model.entity;

import io.simplicity.training.model.enums.InvitationStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.TeamRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "invitation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

  @Id @GeneratedValue private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "team_id")
  private UUID teamId;

  @Column(nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "org_role", nullable = false)
  private OrgRole orgRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "team_role")
  private TeamRole teamRole;

  /** SHA-256 of the emailed token. The token itself is never stored. */
  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private InvitationStatus status = InvitationStatus.PENDING;

  @Column(name = "invited_by")
  private UUID invitedBy;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public boolean isRedeemable(Instant now) {
    return status == InvitationStatus.PENDING && !isExpired(now);
  }
}
