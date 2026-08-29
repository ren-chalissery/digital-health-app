package io.simplicity.training.service.media;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@RequiredArgsConstructor
@Slf4j
public class S3ObjectStore implements ObjectStore {

  private final S3Client s3;
  private final S3Presigner presigner;

  @Override
  public String presignPut(String bucket, String key, String contentType, Duration validFor) {
    // Content type is part of the signature, so a browser cannot upload something else to a URL
    // issued for a video.
    return presigner
        .presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .putObjectRequest(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build())
                .build())
        .url()
        .toString();
  }

  @Override
  public String presignGet(String bucket, String key, Duration validFor) {
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build())
        .url()
        .toString();
  }

  @Override
  public void delete(String bucket, String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (RuntimeException e) {
      // The database record going is what matters; a leftover object costs pennies and is caught
      // by the lifecycle rule on the upload bucket.
      log.warn("Could not delete s3://{}/{}", bucket, key, e);
    }
  }
}
