package io.simplicity.training.service;

import io.simplicity.training.exception.ForbiddenException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.request.UpdateProfileRequest;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.security.AppPrincipal;
import io.simplicity.training.security.SessionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

  private final AppUserRepository users;
  private final SessionService sessions;

  /**
   * Completing the profile is what lets a clinician past the onboarding wizard. It is separate
   * from joining an organisation, because an invited user completes their profile without ever
   * creating one.
   */
  @Transactional
  public AppUser updateProfile(AppPrincipal principal, UpdateProfileRequest request) {
    AppUser user =
        users
            .findById(principal.userId())
            .orElseThrow(() -> NotFoundException.of("User", principal.userId()));

    user.setFullName(request.fullName().trim());
    user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
    user.setProfessionalRole(request.professionalRole().trim());
    user.setProfileCompleted(true);
    AppUser saved = users.save(user);

    // profileCompleted lives on the cached principal, so a stale entry would keep sending the
    // clinician back to the wizard.
    sessions.rolesChanged(saved.getId());
    return saved;
  }

  /**
   * Records which organisation the clinician is working in. Refused unless it is one of theirs —
   * and because archived organisations are absent from the principal, an archived one is refused
   * by the same check, indistinguishably from one they never belonged to.
   */
  @Transactional
  public void setActiveOrganisation(AppPrincipal principal, UUID orgId) {
    if (!principal.isMemberOf(orgId)) {
      throw new ForbiddenException("You are not a member of that organisation");
    }
    AppUser user =
        users
            .findById(principal.userId())
            .orElseThrow(() -> NotFoundException.of("User", principal.userId()));
    user.setActiveOrgId(orgId);
    users.save(user);
  }
}
