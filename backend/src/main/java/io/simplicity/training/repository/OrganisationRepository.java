package io.simplicity.training.repository;

import io.simplicity.training.model.entity.Organisation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

  Optional<Organisation> findBySlug(String slug);

  boolean existsBySlug(String slug);

  /**
   * Read once while building a principal and checked against that clinician's memberships, which
   * is cheaper than a query per membership and returns few rows: archiving is rare and the table
   * is small.
   */
  @Query("select o.id from Organisation o where o.archivedAt is not null")
  Set<UUID> findArchivedIds();

  Optional<Organisation> findByIdAndArchivedAtIsNull(UUID id);
}
