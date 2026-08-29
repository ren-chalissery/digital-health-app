package io.simplicity.training.repository;

import io.simplicity.training.model.Emails;
import io.simplicity.training.model.entity.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByCognitoSub(String cognitoSub);

  @Query("select u from AppUser u where u.email = :email")
  Optional<AppUser> findByExactEmail(@Param("email") String email);

  @Query("select count(u) > 0 from AppUser u where u.email = :email")
  boolean existsByExactEmail(@Param("email") String email);

  /**
   * The single choke point for email lookups. Callers cannot forget to normalise, which they
   * otherwise would — see {@link Emails} for why the citext column does not do this for us.
   */
  default Optional<AppUser> findByEmail(String email) {
    return findByExactEmail(Emails.normalise(email));
  }

  default boolean existsByEmail(String email) {
    return existsByExactEmail(Emails.normalise(email));
  }
}
