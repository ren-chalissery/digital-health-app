package io.simplicity.training.model.entity;

import io.simplicity.training.model.enums.TeamRole;
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

@Entity
@Table(name = "team_member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMember {

  @EmbeddedId private Key id;

  @Enumerated(EnumType.STRING)
  @Column(name = "team_role", nullable = false)
  private TeamRole teamRole;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  public UUID getTeamId() {
    return id == null ? null : id.getTeamId();
  }

  public UUID getUserId() {
    return id == null ? null : id.getUserId();
  }

  @Embeddable
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
  }

  public static TeamMember of(UUID teamId, UUID userId, TeamRole role) {
    return TeamMember.builder().id(new Key(teamId, userId)).teamRole(role).build();
  }
}
