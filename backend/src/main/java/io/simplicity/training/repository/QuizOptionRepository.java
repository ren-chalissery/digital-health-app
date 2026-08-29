package io.simplicity.training.repository;

import io.simplicity.training.model.entity.QuizOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

  List<QuizOption> findByQuestionIdOrderByPositionAsc(UUID questionId);

  List<QuizOption> findByQuestionIdIn(List<UUID> questionIds);
}
