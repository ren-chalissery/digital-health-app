package io.simplicity.training.repository;

import io.simplicity.training.model.entity.UserModuleCompletion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserModuleCompletionRepository
    extends JpaRepository<UserModuleCompletion, UserModuleCompletion.Key> {

  @Query(
      "select c.id.versionId from UserModuleCompletion c "
          + "where c.id.userId = :userId and c.id.versionId in :versionIds")
  List<UUID> findCompletedVersionIds(UUID userId, List<UUID> versionIds);
}
