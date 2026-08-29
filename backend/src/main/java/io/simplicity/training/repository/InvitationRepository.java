package io.simplicity.training.repository;

import io.simplicity.training.model.entity.Invitation;
import io.simplicity.training.model.enums.InvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  Optional<Invitation> findByTokenHash(String tokenHash);

  Optional<Invitation> findByOrgIdAndEmailAndStatus(
      UUID orgId, String email, InvitationStatus status);

  List<Invitation> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

  Optional<Invitation> findByIdAndOrgId(UUID id, UUID orgId);

  long countByOrgIdAndCreatedAtAfter(UUID orgId, Instant after);
}
