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
  private final CognitoUserDirectory directory;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof JwtAuthenticationToken jwtAuthentication) {
      Jwt jwt = jwtAuthentication.getToken();
      String cognitoSub = jwt.getSubject();

      if (revocations.isRevoked(cognitoSub, jwt.getIssuedAt())) {
        // Cognito access tokens stay valid for up to fifteen minutes. Without this check, removing
        // somebody's access would not take effect until their current token expired.
        //
        // Only tokens issued before the revocation are refused. A token minted afterwards is
        // accepted, so somebody removed from one of their organisations is not locked out of the
        // others — or out of signing back in.
        log.info("Rejected a request from revoked subject {}", cognitoSub);
        SecurityContextHolder.clearContext();
        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Session has been revoked");
        return;
      }

      AppPrincipal principal =
          principalLookup.resolve(cognitoSub, () -> directory.verifiedEmail(jwt.getTokenValue()));
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
}
