package io.simplicity.training.support;

import io.simplicity.training.service.EmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Captures outgoing mail so tests can read the invitation token the way a recipient would: out of
 * the link in the message, never out of the database. That keeps the tests honest about the token
 * only existing in the email and as a digest in Postgres.
 */
public class RecordingEmailSender implements EmailSender {

  private static final Pattern LINK = Pattern.compile("/invitations/([A-Za-z0-9_-]+)");

  private final List<Sent> sent = new ArrayList<>();

  @Override
  public synchronized void send(String to, String subject, String htmlBody, String textBody) {
    sent.add(new Sent(to, subject, htmlBody, textBody));
  }

  public synchronized void clear() {
    sent.clear();
  }

  public synchronized List<Sent> all() {
    return List.copyOf(sent);
  }

  public synchronized Sent last() {
    if (sent.isEmpty()) {
      throw new AssertionError("No email was sent");
    }
    return sent.get(sent.size() - 1);
  }

  /** The token from the most recent invitation link. */
  public String lastToken() {
    Matcher matcher = LINK.matcher(last().textBody());
    if (!matcher.find()) {
      throw new AssertionError("No invitation link in:\n" + last().textBody());
    }
    return matcher.group(1);
  }

  public record Sent(String to, String subject, String htmlBody, String textBody) {}

  @TestConfiguration
  public static class Config {

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
      return new RecordingEmailSender();
    }
  }
}
