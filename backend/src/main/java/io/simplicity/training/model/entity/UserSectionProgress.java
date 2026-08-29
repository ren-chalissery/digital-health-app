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

/** One section a clinician has worked through, so Learn can resume where they stopped. */
@Entity
@Table(name = "user_section_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSectionProgress {

  @EmbeddedId private Key id;

  @CreationTimestamp
  @Column(name = "completed_at", nullable = false, updatable = false)
  private Instant completedAt;

  public UUID getUserId() {
    return id == null ? null : id.getUserId();
  }

  public UUID getSectionId() {
    return id == null ? null : id.getSectionId();
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

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;
  }

  public static UserSectionProgress of(UUID userId, UUID sectionId) {
    return UserSectionProgress.builder().id(new Key(userId, sectionId)).build();
  }
}
