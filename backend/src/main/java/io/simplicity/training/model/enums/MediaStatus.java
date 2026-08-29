package io.simplicity.training.model.enums;

/**
 * Where a video is in its journey from an author's laptop to a clinician's browser. Shown in the
 * editor, because a file that does nothing for four minutes otherwise reads as broken.
 */
public enum MediaStatus {
  /** Registered, and the browser is putting bytes into the upload bucket. */
  UPLOADING,
  /** MediaConvert has the job. */
  PROCESSING,
  READY,
  FAILED
}
