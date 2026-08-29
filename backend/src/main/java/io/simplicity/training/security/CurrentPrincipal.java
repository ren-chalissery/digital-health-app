package io.simplicity.training.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the resolved principal out of the security context. */
public final class CurrentPrincipal {

  private CurrentPrincipal() {}

  public static AppPrincipal require() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AppAuthenticationToken token) {
      return token.getPrincipal();
    }
    throw new IllegalStateException(
        "No application principal on the security context. Either the endpoint is public or "
            + "PrincipalResolutionFilter did not run.");
  }
}
