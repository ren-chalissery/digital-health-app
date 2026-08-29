package io.simplicity.training.model.enums;

/**
 * Where a clinician stands with a module. Derived on read rather than stored, because 2c replaces
 * self-reported completion with passing a quiz and a stored flag would then have to be unpicked.
 */
public enum LearningStatus {
  NOT_STARTED,
  IN_PROGRESS,
  COMPLETED,
  /** Completed, but a substantive revision has been published since. */
  NEEDS_REDOING
}
