package io.simplicity.training.exception;

/** The request is well formed but conflicts with the current state, such as a duplicate name. */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
