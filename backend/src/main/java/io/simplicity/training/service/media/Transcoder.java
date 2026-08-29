package io.simplicity.training.service.media;

import java.util.Optional;

/**
 * Turning an uploaded file into something a browser can play.
 *
 * <p>An interface because there is no local emulator for MediaConvert the way Floci gives us one
 * for SES, so the suite substitutes it. The shape is deliberately small: submit, then ask.
 */
public interface Transcoder {

  /** @return the job id to ask about later */
  String submit(String sourceKey, String outputKeyPrefix);

  Optional<TranscodeOutcome> check(String jobId);

  /**
   * @param durationSeconds null when the transcoder did not report one, which is not worth failing
   *     a video over
   */
  record TranscodeOutcome(boolean finished, boolean succeeded, String failureReason, Integer durationSeconds) {

    public static TranscodeOutcome stillGoing() {
      return new TranscodeOutcome(false, false, null, null);
    }

    public static TranscodeOutcome succeeded(Integer durationSeconds) {
      return new TranscodeOutcome(true, true, null, durationSeconds);
    }

    public static TranscodeOutcome failed(String reason) {
      return new TranscodeOutcome(true, false, reason, null);
    }
  }
}
