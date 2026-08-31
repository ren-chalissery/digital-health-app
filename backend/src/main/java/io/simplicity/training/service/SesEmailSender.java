package io.simplicity.training.service;

import io.simplicity.training.config.AppProperties;
import io.simplicity.training.exception.EmailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SesEmailSender implements EmailSender {

  private final SesV2Client ses;
  private final AppProperties properties;

  @Override
  public void send(String to, String subject, String htmlBody, String textBody) {
    SendEmailRequest.Builder request =
        SendEmailRequest.builder()
            .fromEmailAddress(properties.mail().from())
            .destination(Destination.builder().toAddresses(to).build())
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(utf8(subject))
                            .body(Body.builder().html(utf8(htmlBody)).text(utf8(textBody)).build())
                            .build())
                    .build());

    // Sending outside the configuration set still delivers, so this is left optional rather than
    // required — but then nothing reports the bounce, which is the whole point of having one.
    if (properties.mail().hasConfigurationSet()) {
      request.configurationSetName(properties.mail().configurationSet());
    }

    try {
      ses.sendEmail(request.build());
    } catch (SesV2Exception e) {
      log.error("SES rejected the message to {}", to, e);
      throw new EmailDeliveryException("Could not send the invitation email", e);
    }
  }

  private Content utf8(String data) {
    return Content.builder().charset("UTF-8").data(data).build();
  }
}
