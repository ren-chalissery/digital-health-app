package io.simplicity.training.exception;

/** Outgoing mail could not be handed to the provider, so the calling operation cannot stand. */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
