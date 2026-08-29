package io.simplicity.training.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One answer a clinician can choose.
 *
 * <p>{@code correct} must never reach a learner except in response to an attempt they submitted.
 */
@Entity
@Table(name = "quiz_option")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizOption {

  @Id @GeneratedValue private UUID id;

  @Column(name = "question_id", nullable = false)
  private UUID questionId;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  @Builder.Default
  private boolean correct = false;
}
