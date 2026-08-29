package io.simplicity.training.repository;

import io.simplicity.training.model.entity.MediaAsset;
import io.simplicity.training.model.enums.MediaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

  List<MediaAsset> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

  /** Scoped by organisation as well as id, so an id from elsewhere misses rather than leaks. */
  Optional<MediaAsset> findByIdAndOrgId(UUID id, UUID orgId);

  List<MediaAsset> findByStatus(MediaStatus status);
}
