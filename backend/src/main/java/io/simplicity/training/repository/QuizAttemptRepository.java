package io.simplicity.training.repository;

import io.simplicity.training.model.entity.QuizAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

  List<QuizAttempt> findByUserIdAndVersionIdOrderByAttemptNumberAsc(UUID userId, UUID versionId);

  boolean existsByUserIdAndVersionIdAndPassedIsTrue(UUID userId, UUID versionId);

  @Query(
      "select coalesce(max(a.attemptNumber), 0) from QuizAttempt a "
          + "where a.userId = :userId and a.versionId = :versionId")
  int highestAttemptNumber(UUID userId, UUID versionId);
}
