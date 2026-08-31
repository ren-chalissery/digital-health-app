package io.simplicity.training.repository;

import io.simplicity.training.model.entity.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  List<AuditEvent> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

  /**
   * Clears the source address of entries older than the retention window, leaving the entries
   * themselves untouched.
   *
   * <p>Restricted to rows that still have an address, so a second run over the same entries is a
   * no-op rather than a rewrite of everything old.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE AuditEvent e SET e.ipAddress = NULL "
          + "WHERE e.createdAt < :before AND e.ipAddress IS NOT NULL")
  int forgetAddressesRecordedBefore(@Param("before") Instant before);
}
