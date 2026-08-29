package io.simplicity.training.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A unit of training. Holds identity and ownership only: the content lives on {@link
 * ModuleVersion}, so that publishing produces something immutable for a completion to point at.
 *
 * <p>Named {@code TrainingModule} rather than {@code Module}, which is {@link java.lang.Module} and
 * would shadow a JDK class imported into every file by default.
 */
@Entity
@Table(name = "module")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingModule {

  @Id @GeneratedValue private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(nullable = false)
  private String title;

  private String summary;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public boolean isArchived() {
    return archivedAt != null;
  }
}
