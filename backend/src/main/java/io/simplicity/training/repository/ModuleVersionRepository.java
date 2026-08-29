package io.simplicity.training.repository;

import io.simplicity.training.model.entity.ModuleVersion;
import io.simplicity.training.model.enums.ModuleStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleVersionRepository extends JpaRepository<ModuleVersion, UUID> {

  Optional<ModuleVersion> findByModuleIdAndStatus(UUID moduleId, ModuleStatus status);

  /** The version learners are currently given, which is simply the newest published one. */
  Optional<ModuleVersion> findFirstByModuleIdAndStatusOrderByVersionNumberDesc(
      UUID moduleId, ModuleStatus status);

  List<ModuleVersion> findByModuleIdOrderByVersionNumberDesc(UUID moduleId);

  List<ModuleVersion> findByModuleIdIn(List<UUID> moduleIds);
}
