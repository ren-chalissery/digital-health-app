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

/** One question, belonging to a module version so that publishing freezes it. */
@Entity
@Table(name = "quiz_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private String prompt;

  /** Shown after an attempt. Optional: a question can be plain enough not to need one. */
  private String explanation;
}
