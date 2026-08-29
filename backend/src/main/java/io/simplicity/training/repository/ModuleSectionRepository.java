package io.simplicity.training.repository;

import io.simplicity.training.model.entity.ModuleSection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ModuleSectionRepository extends JpaRepository<ModuleSection, UUID> {

  List<ModuleSection> findByVersionIdOrderByPositionAsc(UUID versionId);

  List<ModuleSection> findByVersionIdIn(List<UUID> versionIds);

  @Modifying
  @Query("delete from ModuleSection s where s.versionId = :versionId")
  void deleteByVersionId(UUID versionId);

  long countByVersionId(UUID versionId);

  /** Every section using a given video, across every version. Used when deleting one. */
  List<ModuleSection> findByMediaAssetId(UUID mediaAssetId);
}
