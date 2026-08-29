package io.simplicity.training.repository;

import io.simplicity.training.model.entity.TrainingModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository extends JpaRepository<TrainingModule, UUID> {

  List<TrainingModule> findByOrgIdAndArchivedAtIsNullOrderByTitleAsc(UUID orgId);

  /**
   * Scoped by organisation as well as id. The second layer of the org filtering Phase 1
   * established: an id from another organisation must miss even if authorisation were wrong.
   */
  Optional<TrainingModule> findByIdAndOrgIdAndArchivedAtIsNull(UUID id, UUID orgId);
}
