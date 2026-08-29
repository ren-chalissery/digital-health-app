package io.simplicity.training.repository;

import io.simplicity.training.model.entity.UserSectionProgress;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserSectionProgressRepository
    extends JpaRepository<UserSectionProgress, UserSectionProgress.Key> {

  @Query(
      "select p.id.sectionId from UserSectionProgress p "
          + "where p.id.userId = :userId and p.id.sectionId in :sectionIds")
  List<UUID> findCompletedSectionIds(UUID userId, List<UUID> sectionIds);
}
