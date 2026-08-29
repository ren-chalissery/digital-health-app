package io.simplicity.training.model.response;

import java.time.Instant;
import java.util.UUID;

public final class MediaResponses {

  private MediaResponses() {}

  /** Where to PUT the bytes. Valid for half an hour, which is generous for 500MB. */
  public record UploadTargetResponse(UUID assetId, String uploadUrl) {}

  public record MediaAssetResponse(
      UUID assetId,
      String filename,
      String status,
      String failureReason,
      Integer durationSeconds,
      Long sizeBytes,
      boolean hasCaptions,
      Instant createdAt) {}

  /** Short-lived, minted per request. {@code captionUrl} is null when nobody has written any. */
  public record PlaybackResponse(String url, String captionUrl, int expiresInSeconds) {}
}
