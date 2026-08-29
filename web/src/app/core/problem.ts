import { HttpErrorResponse } from '@angular/common/http';

interface ProblemDetail {
  detail?: string;
  title?: string;
  errors?: Record<string, string>;
}

/**
 * Pulls the message out of an RFC 9457 problem response.
 *
 * <p>The server already writes these for a clinician to read, so the client shows them verbatim
 * rather than inventing its own wording and going stale.
 */
export function problemMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as ProblemDetail | null;
    const fieldErrors = problem?.errors ? Object.values(problem.errors) : [];
    if (fieldErrors.length > 0) {
      return fieldErrors.join('. ');
    }
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.status === 0) {
      return 'Could not reach the server. Check your connection and try again.';
    }
  }

  // Amplify throws plain Errors whose messages are already user-facing.
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}
