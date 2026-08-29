package io.simplicity.training.service;

import io.simplicity.training.model.Emails;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.enums.UserStatus;
import io.simplicity.training.repository.AppUserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the application's record of a user the first time they present a valid Cognito token.
 *
 * <p>Provisioning on first request rather than through a Cognito post-confirmation trigger keeps
 * the two systems loosely coupled: no Lambda to deploy, and a user who somehow authenticates
 * without having been provisioned simply gets a row rather than an error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningService {

  private final AppUserRepository users;

  /**
   * @param cognitoSub the {@code sub} claim, stable for the life of the Cognito account
   * @param email the verified address from the token, or null if the token carries no email claim
   */
  @Transactional
  public AppUser findOrCreate(String cognitoSub, String email) {
    Optional<AppUser> bySub = users.findByCognitoSub(cognitoSub);
    if (bySub.isPresent()) {
      return bySub.get();
    }

    // An administrator may have invited this address before the person created their Cognito
    // account. That row already carries their organisation membership, so claim it rather than
    // creating a second account for the same person.
    if (email != null) {
      Optional<AppUser> byEmail = users.findByEmail(email);
      if (byEmail.isPresent()) {
        AppUser existing = byEmail.get();
        existing.setCognitoSub(cognitoSub);
        if (existing.getStatus() == UserStatus.INVITED) {
          existing.setStatus(UserStatus.ACTIVE);
        }
        log.info("Linked Cognito subject to invited user {}", existing.getId());
        return users.save(existing);
      }
    }

    if (email == null) {
      throw new IllegalStateException(
          "Cannot provision a user for Cognito subject "
              + cognitoSub
              + " because the token carries no email claim");
    }

    AppUser created =
        AppUser.builder()
            .cognitoSub(cognitoSub)
            .email(Emails.normalise(email))
            .status(UserStatus.ACTIVE)
            .profileCompleted(false)
            .build();
    try {
      AppUser saved = users.save(created);
      log.info("Provisioned user {} on first authenticated request", saved.getId());
      return saved;
    } catch (DataIntegrityViolationException e) {
      // Two concurrent first requests from the same new user race here. Whichever insert lost can
      // simply read the winner's row.
      return users
          .findByCognitoSub(cognitoSub)
          .or(() -> users.findByEmail(email))
          .orElseThrow(() -> e);
    }
  }
}
