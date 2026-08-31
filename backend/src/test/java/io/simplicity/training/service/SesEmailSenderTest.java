package io.simplicity.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.simplicity.training.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Whether outgoing mail names the configuration set.
 *
 * <p>It is the only part of the request nothing else can see: the emulator the contract test runs
 * against accepts the field and then omits it from what it reports, so a send that works there is
 * no evidence the name was ever on the request. Without it SES still delivers, but reports the
 * bounce nowhere, and the infrastructure's event destination stays silent however much mail fails.
 */
class SesEmailSenderTest {

  @Test
  void namesTheConfigurationSetSoBouncesAreReported() {
    SesV2Client ses = mock(SesV2Client.class);

    new SesEmailSender(ses, propertiesWithConfigurationSet("digital-health-prod"))
        .send("clinician@example.org", "You have been invited", "<p>Join</p>", "Join");

    assertThat(sentRequest(ses).configurationSetName()).isEqualTo("digital-health-prod");
  }

  @Test
  void leavesItOffEntirelyWhenNoneIsConfigured() {
    SesV2Client ses = mock(SesV2Client.class);

    new SesEmailSender(ses, propertiesWithConfigurationSet(""))
        .send("clinician@example.org", "You have been invited", "<p>Join</p>", "Join");

    // An empty name is not the same as no name: SES rejects the blank outright, which would take
    // invitations down in an environment that simply has no configuration set.
    assertThat(sentRequest(ses).configurationSetName()).isNull();
  }

  private SendEmailRequest sentRequest(SesV2Client ses) {
    ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(ses).sendEmail(request.capture());
    return request.getValue();
  }

  private AppProperties propertiesWithConfigurationSet(String configurationSet) {
    return new AppProperties(
        new AppProperties.Aws(null),
        new AppProperties.Cognito(null, null, List.of(), "ap-southeast-2"),
        new AppProperties.Auth(Duration.ofMinutes(15), Duration.ofMinutes(5)),
        new AppProperties.Invitations(Duration.ofDays(7), 50),
        new AppProperties.Mail("no-reply@example.org", true, configurationSet),
        new AppProperties.Media("", "", "", "", Duration.ofMinutes(15), 1L),
        new AppProperties.Web("http://localhost:4200", List.of()),
        new AppProperties.Audit(Duration.ofDays(180)));
  }
}
