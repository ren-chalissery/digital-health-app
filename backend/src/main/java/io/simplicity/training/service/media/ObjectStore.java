package io.simplicity.training.service.media;

import java.time.Duration;

/**
 * S3, behind an interface so the suite can watch what would have been stored without a bucket.
 *
 * <p>Presigning is deliberately the only way bytes move: the browser puts straight to S3 and plays
 * straight from S3, and this application never carries a video.
 */
public interface ObjectStore {

  String presignPut(String bucket, String key, String contentType, Duration validFor);

  String presignGet(String bucket, String key, Duration validFor);

  void delete(String bucket, String key);
}
