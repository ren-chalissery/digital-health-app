package io.simplicity.training.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.simplicity.training.config.AppProperties;
import io.simplicity.training.config.AwsConfig;
import io.simplicity.training.security.CognitoIdpUserDirectory;
import io.simplicity.training.security.SecurityConfig;
import io.simplicity.training.service.SesEmailSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExplicitAuthFlowsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifiedAttributeType;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Exercises the two AWS services the application actually calls, against a local emulator.
 *
 * <p>The rest of the suite substitutes both, which is right for speed but leaves the request shapes
 * unverified until deployment. These tests are the ones that fail when an SES field is wrong or
 * Cognito stops answering the way the provisioning path assumes.
 *
 * <p>What they cannot prove is fidelity. The emulator is more forgiving than Cognito in at least
 * one respect — it puts the address on the access token, which a real pool does not — so a claim
 * appearing here is no evidence it appears in production. Treat these as contract tests for our
 * side of the call, not as a substitute for deploying.
 */
@Testcontainers
class AwsContractTest {

  private static final String SENDER = "no-reply@simplicity.local";
  private static final String RECIPIENT = "clinician@example.org";
  private static final String PASSWORD = "Sup3rSecretPass";

  @Container
  static final GenericContainer<?> FLOCI =
      new GenericContainer<>(DockerImageName.parse("floci/floci:1.7.0"))
          .withExposedPorts(4566)
          .waitingFor(Wait.forHttp("/health").forPort(4566));

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AwsConfig AWS = new AwsConfig();

  @BeforeAll
  static void useThrowawayCredentials() {
    // The emulator accepts anything. Setting them explicitly keeps the default credential chain
    // from wandering off to a real profile on a developer's machine.
    System.setProperty("aws.accessKeyId", "test");
    System.setProperty("aws.secretAccessKey", "test");
    System.setProperty("aws.region", "us-east-1");
  }

  private static String endpoint() {
    return "http://" + FLOCI.getHost() + ":" + FLOCI.getMappedPort(4566);
  }

  private static AppProperties propertiesFor(String issuerUri) {
    return new AppProperties(
        new AppProperties.Aws(endpoint()),
        new AppProperties.Cognito(issuerUri, null, null, "us-east-1"),
        new AppProperties.Auth(Duration.ofMinutes(15), Duration.ofMinutes(5)),
        new AppProperties.Invitations(Duration.ofDays(7), 50),
        new AppProperties.Mail(SENDER, true),
        new AppProperties.Media("", "", "", "", Duration.ofMinutes(15), 1L),
        new AppProperties.Web("http://localhost:4200", java.util.List.of()));
  }

  @Test
  void sendsAnInvitationThatSesActuallyAccepts() throws Exception {
    AppProperties properties = propertiesFor(null);
    SesV2Client ses = AWS.sesClient(properties);
    ses.createEmailIdentity(request -> request.emailIdentity(SENDER));

    new SesEmailSender(ses, properties)
        .send(RECIPIENT, "You have been invited", "<p>Join us</p>", "Join us");

    JsonNode sent = sentMail().get("messages").get(0);
    assertThat(sent.get("Source").asText()).isEqualTo(SENDER);
    assertThat(sent.get("Destination").get("ToAddresses").get(0).asText()).isEqualTo(RECIPIENT);
    assertThat(sent.get("Subject").asText()).isEqualTo("You have been invited");
    assertThat(sent.get("Body").get("html_part").asText()).isEqualTo("<p>Join us</p>");
    assertThat(sent.get("Body").get("text_part").asText()).isEqualTo("Join us");
  }

  @Test
  void readsTheAddressCognitoHoldsForWhoeverPresentedTheToken() {
    CognitoIdentityProviderClient cognito = AWS.cognitoClient(propertiesFor(null));
    String accessToken = aSignedInUser(cognito);

    assertThat(new CognitoIdpUserDirectory(cognito).verifiedEmail(accessToken))
        .as("the address has to come from Cognito, because the access token does not carry it")
        .contains(RECIPIENT);
  }

  @Test
  void validatesAPoolIssuedTokenWithTheDecoderTheApplicationUses() {
    CognitoIdentityProviderClient cognito = AWS.cognitoClient(propertiesFor(null));
    String poolId = aPool(cognito);
    String accessToken = aSignedInUser(cognito, poolId, aClient(cognito, poolId));

    JwtDecoder decoder =
        new SecurityConfig().jwtDecoder(propertiesFor(endpoint() + "/" + poolId));
    Jwt decoded = decoder.decode(accessToken);

    assertThat(decoded.getSubject()).isNotBlank();
    assertThat(decoded.getClaimAsString("token_use")).isEqualTo("access");
  }

  private String aSignedInUser(CognitoIdentityProviderClient cognito) {
    String poolId = aPool(cognito);
    return aSignedInUser(cognito, poolId, aClient(cognito, poolId));
  }

  private String aPool(CognitoIdentityProviderClient cognito) {
    // Mirrors infra/auth.yaml. Identifying users by email is what makes the username claim a UUID.
    return cognito
        .createUserPool(
            request ->
                request
                    .poolName("contract-" + System.nanoTime())
                    .usernameAttributes(UsernameAttributeType.EMAIL)
                    .autoVerifiedAttributes(VerifiedAttributeType.EMAIL))
        .userPool()
        .id();
  }

  private String aClient(CognitoIdentityProviderClient cognito, String poolId) {
    return cognito
        .createUserPoolClient(
            request ->
                request
                    .userPoolId(poolId)
                    .clientName("web")
                    .generateSecret(false)
                    .explicitAuthFlows(
                        ExplicitAuthFlowsType.ALLOW_USER_PASSWORD_AUTH,
                        ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH))
        .userPoolClient()
        .clientId();
  }

  private String aSignedInUser(
      CognitoIdentityProviderClient cognito, String poolId, String clientId) {
    cognito.signUp(
        request ->
            request
                .clientId(clientId)
                .username(RECIPIENT)
                .password(PASSWORD)
                .userAttributes(AttributeType.builder().name("email").value(RECIPIENT).build()));
    cognito.adminConfirmSignUp(request -> request.userPoolId(poolId).username(RECIPIENT));
    // Confirming an auto-verified pool is enough for Cognito to set this, but the emulator leaves
    // the attribute off entirely, so set it by hand rather than relax the check being tested.
    cognito.adminUpdateUserAttributes(
        request ->
            request
                .userPoolId(poolId)
                .username(RECIPIENT)
                .userAttributes(
                    AttributeType.builder().name("email_verified").value("true").build()));

    return cognito
        .initiateAuth(
            request ->
                request
                    .clientId(clientId)
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .authParameters(java.util.Map.of("USERNAME", RECIPIENT, "PASSWORD", PASSWORD)))
        .authenticationResult()
        .accessToken();
  }

  private JsonNode sentMail() throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(endpoint() + "/_aws/ses")).build(),
                HttpResponse.BodyHandlers.ofString());
    return JSON.readTree(response.body());
  }
}
