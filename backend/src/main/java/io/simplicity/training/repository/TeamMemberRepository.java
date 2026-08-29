package io.simplicity.training.repository;

import io.simplicity.training.model.entity.TeamMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMember.Key> {

  @Query("select tm from TeamMember tm where tm.id.teamId = :teamId")
  List<TeamMember> findByTeamId(@Param("teamId") UUID teamId);

  @Query("select tm from TeamMember tm where tm.id.userId = :userId")
  List<TeamMember> findByUserId(@Param("userId") UUID userId);

  @Query("select tm from TeamMember tm where tm.id.teamId = :teamId and tm.id.userId = :userId")
  Optional<TeamMember> find(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

  /**
   * Team memberships for a user restricted to one organisation, joined through the team table so
   * the organisation boundary is enforced in SQL.
   */
  @Query(
      "select tm from TeamMember tm, Team t "
          + "where tm.id.teamId = t.id and tm.id.userId = :userId and t.orgId = :orgId")
  List<TeamMember> findByUserIdAndOrgId(@Param("userId") UUID userId, @Param("orgId") UUID orgId);

  @Modifying
  @Query("delete from TeamMember tm where tm.id.teamId = :teamId and tm.id.userId = :userId")
  void deleteMembership(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

  /** Used when a user leaves an organisation: every team membership inside it must go too. */
  @Modifying
  @Query(
      "delete from TeamMember tm where tm.id.userId = :userId "
          + "and tm.id.teamId in (select t.id from Team t where t.orgId = :orgId)")
  void deleteAllForUserInOrg(@Param("userId") UUID userId, @Param("orgId") UUID orgId);
}
