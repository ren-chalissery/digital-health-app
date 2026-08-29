package io.simplicity.training.repository;

import io.simplicity.training.model.entity.Reflection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReflectionRepository extends JpaRepository<Reflection, UUID> {

  List<Reflection> findByUserIdOrderByCreatedAtDesc(UUID userId);

  /**
   * Scoped by author as well as id. Every read goes through this: a reflection belonging to
   * somebody else must be indistinguishable from one that does not exist.
   */
  Optional<Reflection> findByIdAndUserId(UUID id, UUID userId);

  /**
   * {@code plainto_tsquery} so a clinician types words rather than operators, ranked so the closest
   * match comes first. Native because the generated tsvector column is not mapped, and there is no
   * reason for Hibernate to know about it.
   */
  @Query(
      value =
          "select * from reflection r "
              + "where r.user_id = :userId and r.search @@ plainto_tsquery('english', :terms) "
              + "order by ts_rank(r.search, plainto_tsquery('english', :terms)) desc, "
              + "r.created_at desc",
      nativeQuery = true)
  List<Reflection> search(UUID userId, String terms);
}
