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

/**
 * One submission. Kept whether it passed or not: retakes are unlimited, so the interesting fact is
 * often how many it took, and that cannot be recovered once discarded.
 */
@Entity
@Table(name = "quiz_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Column(name = "correct_count", nullable = false)
  private int correctCount;

  @Column(name = "question_count", nullable = false)
  private int questionCount;

  @Column(nullable = false)
  private boolean passed;

  @CreationTimestamp
  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;
}
