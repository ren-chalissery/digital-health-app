package io.simplicity.training.service;

import io.simplicity.training.model.entity.AuditEvent;
import io.simplicity.training.repository.AuditEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records membership and role changes.
 *
 * <p>Writes join the caller's transaction, so an audit entry only exists for a change that
 * actually committed.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditEventRepository auditEvents;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      UUID actorUserId, UUID orgId, String action, String targetType, Object targetId) {
    record(actorUserId, orgId, action, targetType, targetId, null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      UUID actorUserId,
      UUID orgId,
      String action,
      String targetType,
      Object targetId,
      String metadataJson) {
    auditEvents.save(
        AuditEvent.builder()
            .actorUserId(actorUserId)
            .orgId(orgId)
            .action(action)
            .targetType(targetType)
            .targetId(targetId == null ? null : targetId.toString())
            .metadata(metadataJson)
            .build());
  }
}
