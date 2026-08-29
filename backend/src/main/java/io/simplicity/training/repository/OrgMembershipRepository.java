package io.simplicity.training.repository;

import io.simplicity.training.model.entity.OrgMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgMembershipRepository
    extends JpaRepository<OrgMembership, OrgMembership.Key> {

  @Query("select m from OrgMembership m where m.id.userId = :userId")
  List<OrgMembership> findByUserId(@Param("userId") UUID userId);

  @Query("select m from OrgMembership m where m.id.orgId = :orgId")
  List<OrgMembership> findByOrgId(@Param("orgId") UUID orgId);

  @Query("select m from OrgMembership m where m.id.userId = :userId and m.id.orgId = :orgId")
  Optional<OrgMembership> find(@Param("userId") UUID userId, @Param("orgId") UUID orgId);

  @Query(
      "select count(m) from OrgMembership m "
          + "where m.id.orgId = :orgId and m.orgRole = io.simplicity.training.model.enums.OrgRole.ORG_ADMIN "
          + "and m.status = io.simplicity.training.model.enums.MembershipStatus.ACTIVE")
  long countActiveAdmins(@Param("orgId") UUID orgId);

  @Query("delete from OrgMembership m where m.id.userId = :userId and m.id.orgId = :orgId")
  @org.springframework.data.jpa.repository.Modifying
  void deleteMembership(@Param("userId") UUID userId, @Param("orgId") UUID orgId);
}
