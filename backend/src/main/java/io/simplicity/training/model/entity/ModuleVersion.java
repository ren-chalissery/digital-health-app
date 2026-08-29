package io.simplicity.training.model.entity;

import io.simplicity.training.model.enums.ModuleStatus;
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

/** One published or in-progress revision of a module, and the thing a completion points at. */
@Entity
@Table(name = "module_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "module_id", nullable = false)
  private UUID moduleId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModuleStatus status;

  /**
   * Whether publishing this version should make the module outstanding again for people who
   * completed an earlier one. Decided by whoever publishes, because only they know whether they
   * corrected a typo or rewrote the protocol.
   */
  @Column(name = "supersedes_completions", nullable = false)
  @Builder.Default
  private boolean supersedesCompletions = false;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "published_by")
  private UUID publishedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
