package io.simplicity.training.service;

/** Sends transactional mail. Implemented by SES in the deployed environment and by a logger locally. */
public interface EmailSender {

  void send(String to, String subject, String htmlBody, String textBody);
}
