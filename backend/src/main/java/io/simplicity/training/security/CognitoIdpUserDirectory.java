package io.simplicity.training.security;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;

/**
 * Resolves the caller's address with Cognito's {@code GetUser}, which authorises against the
 * caller's own access token and so needs no administrative credentials.
 */
@Component
@ConditionalOnMissingBean(CognitoUserDirectory.class)
@RequiredArgsConstructor
@Slf4j
public class CognitoIdpUserDirectory implements CognitoUserDirectory {

  private final CognitoIdentityProviderClient cognito;

  @Override
  public Optional<String> verifiedEmail(String accessToken) {
    Map<String, String> attributes;
    try {
      GetUserResponse user = cognito.getUser(request -> request.accessToken(accessToken));
      attributes =
          user.userAttributes().stream()
              .collect(Collectors.toMap(AttributeType::name, AttributeType::value, (a, b) -> a));
    } catch (CognitoIdentityProviderException e) {
      log.warn("Cognito refused to describe the caller: {}", e.awsErrorDetails().errorMessage());
      return Optional.empty();
    }

    // An unverified address must never be trusted here. Claiming an invitation matches on address,
    // so accepting one would let somebody sign up as a colleague and inherit their membership.
    if (!Boolean.parseBoolean(attributes.get("email_verified"))) {
      log.warn("Refusing to provision a user whose address Cognito has not verified");
      return Optional.empty();
    }
    return Optional.ofNullable(attributes.get("email"));
  }
}
