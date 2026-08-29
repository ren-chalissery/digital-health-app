package io.simplicity.training.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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

/** How a module reaches learners: assignment is by team, never to an individual. */
@Entity
@Table(name = "team_module_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamModuleAssignment {

  @EmbeddedId private Key id;

  @Column(name = "assigned_by")
  private UUID assignedBy;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public UUID getTeamId() {
    return id == null ? null : id.getTeamId();
  }

  public UUID getModuleId() {
    return id == null ? null : id.getModuleId();
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

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;
  }

  public static TeamModuleAssignment of(UUID teamId, UUID moduleId, UUID assignedBy) {
    return TeamModuleAssignment.builder().id(new Key(teamId, moduleId)).assignedBy(assignedBy).build();
  }
}
