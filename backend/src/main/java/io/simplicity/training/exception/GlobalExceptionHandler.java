package io.simplicity.training.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns exceptions into RFC 9457 {@code application/problem+json}.
 *
 * <p>Messages are written for a clinician to read, and never disclose whether a resource in
 * another organisation exists.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String PROBLEM_BASE = "https://digitalhealth.app/problems/";

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException e, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage(), request);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException e, HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", e.getMessage(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
    // Deliberately identical whether the organisation exists or the caller merely lacks the role,
    // so the response cannot be used to discover which organisation ids are real.
    return problem(
        HttpStatus.FORBIDDEN,
        "forbidden",
        "Forbidden",
        "You do not have permission to perform this action",
        request);
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException e, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "conflict", "Conflict", e.getMessage(), request);
  }

  @ExceptionHandler(BadRequestException.class)
  public ProblemDetail handleBadRequest(BadRequestException e, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", e.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "validation-failed",
            "Validation failed",
            "One or more fields are invalid",
            request);
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ProblemDetail handleHandlerValidation(
      HandlerMethodValidationException e, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "validation-failed",
        "Validation failed",
        "One or more parameters are invalid",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleIntegrity(
      DataIntegrityViolationException e, HttpServletRequest request) {
    // The underlying message names columns and constraints, so it is logged rather than returned.
    log.warn("Database constraint violated on {}", request.getRequestURI(), e);
    return problem(
        HttpStatus.CONFLICT,
        "conflict",
        "Conflict",
        "That change conflicts with existing data",
        request);
  }

  @ExceptionHandler(EmailDeliveryException.class)
  public ProblemDetail handleEmailDelivery(EmailDeliveryException e, HttpServletRequest request) {
    // The provider's own wording names addresses and identities, so it is logged rather than
    // returned. Saying nothing was changed is what tells the caller a plain retry is safe.
    log.error("Outgoing mail failed on {}", request.getRequestURI(), e);
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "email-delivery-failed",
        "Email could not be sent",
        "The email could not be sent, so nothing was changed. Please try again shortly.",
        request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled exception on {}", request.getRequestURI(), e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Something went wrong",
        "The request could not be completed. Please try again.",
        request);
  }

  private ProblemDetail problem(
      HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(PROBLEM_BASE + type));
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("timestamp", Instant.now().toString());
    return problem;
  }
}
