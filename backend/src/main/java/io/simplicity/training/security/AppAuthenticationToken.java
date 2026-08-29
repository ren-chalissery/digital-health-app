package io.simplicity.training.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Replaces the raw JWT authentication once the token has been resolved to an application user, so
 * that controllers and {@code @PreAuthorize} expressions work with domain roles rather than
 * claims.
 */
public class AppAuthenticationToken extends AbstractAuthenticationToken {

  private final transient AppPrincipal principal;
  private final transient Jwt token;

  public AppAuthenticationToken(AppPrincipal principal, Jwt token) {
    super(authorities(principal));
    this.principal = principal;
    this.token = token;
    setAuthenticated(true);
  }

  private static Collection<GrantedAuthority> authorities(AppPrincipal principal) {
    // Only the platform tier becomes a Spring authority. Organisation and team roles are
    // per-resource, so they are checked against the principal by the authorisation service
    // instead of being flattened into a global authority list.
    return principal.isSuperAdmin()
        ? List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        : List.of();
  }

  @Override
  public AppPrincipal getPrincipal() {
    return principal;
  }

  @Override
  public Jwt getCredentials() {
    return token;
  }

  @Override
  public String getName() {
    return principal.cognitoSub();
  }
}
