package io.simplicity.training.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exchanges a validated Cognito token for an application principal.
 *
 * <p>Runs after the resource server has authenticated the bearer token and before authorisation is
 * evaluated, so {@code @PreAuthorize} expressions see domain roles rather than JWT claims.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrincipalResolutionFilter extends OncePerRequestFilter {

  private final PrincipalLookup principalLookup;
  private final TokenRevocationService revocations;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof JwtAuthenticationToken jwtAuthentication) {
      Jwt jwt = jwtAuthentication.getToken();
      String cognitoSub = jwt.getSubject();

      if (revocations.isRevoked(cognitoSub)) {
        // Cognito access tokens stay valid for up to fifteen minutes. Without this check, removing
        // somebody's access would not take effect until their current token expired.
        log.info("Rejected a request from revoked subject {}", cognitoSub);
        SecurityContextHolder.clearContext();
        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Session has been revoked");
        return;
      }

      AppPrincipal principal = principalLookup.resolve(cognitoSub, emailClaim(jwt));
      if (!principal.isActive()) {
        log.info("Rejected a request from deactivated user {}", principal.userId());
        SecurityContextHolder.clearContext();
        response.sendError(HttpStatus.FORBIDDEN.value(), "Account is not active");
        return;
      }

      SecurityContextHolder.getContext()
          .setAuthentication(new AppAuthenticationToken(principal, jwt));
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Cognito puts the address on the id token; an access token carries it only when the pool is
   * configured to. Both claim names are checked so provisioning works either way.
   */
  private String emailClaim(Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    return email != null ? email : jwt.getClaimAsString("username");
  }
}
