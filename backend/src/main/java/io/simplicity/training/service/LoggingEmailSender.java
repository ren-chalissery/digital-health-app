package io.simplicity.training.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Used when mail is switched off, which is the default locally and in tests.
 *
 * <p>The body is logged so a developer can copy an invitation link out of the console, but this
 * must never be the active sender in a deployed environment: {@code app.mail.enabled} is true
 * there, which selects the SES implementation instead.
 */
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingEmailSender implements EmailSender {

  @Override
  public void send(String to, String subject, String htmlBody, String textBody) {
    log.info("Mail disabled. Would have sent to {} with subject '{}':\n{}", to, subject, textBody);
  }
}
