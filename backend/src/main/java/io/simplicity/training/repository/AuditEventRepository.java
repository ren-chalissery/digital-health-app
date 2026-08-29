package io.simplicity.training.repository;

import io.simplicity.training.model.entity.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  List<AuditEvent> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
