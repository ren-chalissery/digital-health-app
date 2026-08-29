package io.simplicity.training.repository;

import io.simplicity.training.model.entity.TeamModuleAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamModuleAssignmentRepository
    extends JpaRepository<TeamModuleAssignment, TeamModuleAssignment.Key> {

  @Query("select a from TeamModuleAssignment a where a.id.moduleId = :moduleId")
  List<TeamModuleAssignment> findByModuleId(UUID moduleId);

  @Query("select distinct a.id.moduleId from TeamModuleAssignment a where a.id.teamId in :teamIds")
  List<UUID> findModuleIdsForTeams(List<UUID> teamIds);

  @Query("select a from TeamModuleAssignment a where a.id.moduleId in :moduleIds")
  List<TeamModuleAssignment> findByModuleIdIn(List<UUID> moduleIds);
}
