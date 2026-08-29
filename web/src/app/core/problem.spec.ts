import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { problemMessage } from './problem';

describe('problemMessage', () => {
  it('shows the server wording rather than inventing its own', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { title: 'Conflict', detail: 'That person is already a member of this organisation' },
    });

    expect(problemMessage(error)).toBe('That person is already a member of this organisation');
  });

  it('prefers field errors, which say which field is wrong', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        detail: 'One or more fields are invalid',
        errors: { email: 'must be a well-formed email address' },
      },
    });

    expect(problemMessage(error)).toBe('must be a well-formed email address');
  });

  it('joins several field errors so none is hidden', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { errors: { email: 'is required', orgRole: 'is required' } },
    });

    expect(problemMessage(error)).toBe('is required. is required');
  });

  it('explains a network failure instead of showing status 0', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toContain('Could not reach the server');
  });

  it('passes through an Amplify error, whose messages are already user-facing', () => {
    expect(problemMessage(new Error('Incorrect username or password.'))).toBe(
      'Incorrect username or password.',
    );
  });

  it('falls back to the caller wording for anything unrecognised', () => {
    expect(problemMessage({}, 'Could not send the invitation.')).toBe(
      'Could not send the invitation.',
    );
  });
});
