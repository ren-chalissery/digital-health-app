package io.simplicity.training.repository;

import io.simplicity.training.model.entity.QuizQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

  List<QuizQuestion> findByVersionIdOrderByPositionAsc(UUID versionId);

  long countByVersionId(UUID versionId);

  @Modifying
  @Query("delete from QuizQuestion q where q.versionId = :versionId")
  void deleteByVersionId(UUID versionId);
}
