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

/**
 * That a clinician finished a particular version. Never updated and never removed: a row here is a
 * true statement about content that really existed, which is what lets "completed version two in
 * June, version three outstanding since August" be expressible at all.
 */
@Entity
@Table(name = "user_module_completion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserModuleCompletion {

  @EmbeddedId private Key id;

  @CreationTimestamp
  @Column(name = "completed_at", nullable = false, updatable = false)
  private Instant completedAt;

  public UUID getUserId() {
    return id == null ? null : id.getUserId();
  }

  public UUID getVersionId() {
    return id == null ? null : id.getVersionId();
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

    @Column(name = "version_id", nullable = false)
    private UUID versionId;
  }

  public static UserModuleCompletion of(UUID userId, UUID versionId) {
    return UserModuleCompletion.builder().id(new Key(userId, versionId)).build();
  }
}
