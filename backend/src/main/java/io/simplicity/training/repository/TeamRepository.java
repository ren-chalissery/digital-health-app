package io.simplicity.training.repository;

import io.simplicity.training.model.entity.Team;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

  List<Team> findByOrgIdOrderByNameAsc(UUID orgId);

  /**
   * Always look a team up by id <em>and</em> organisation. Filtering on org_id here means a bug in
   * the authorisation layer yields an empty result rather than another tenant's data.
   */
  Optional<Team> findByIdAndOrgId(UUID id, UUID orgId);

  boolean existsByOrgIdAndNameIgnoreCase(UUID orgId, String name);
}
