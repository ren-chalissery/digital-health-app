package io.simplicity.training.support;

import io.simplicity.training.service.media.ObjectStore;
import io.simplicity.training.service.media.Transcoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for S3 and MediaConvert.
 *
 * <p>Unlike SES and Cognito, which Floci emulates well enough to run contract tests against, there
 * is no local MediaConvert. These fakes record what would have happened so a test can assert on it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMediaConfiguration {

  @Bean
  @Primary
  public RecordingObjectStore recordingObjectStore() {
    return new RecordingObjectStore();
  }

  @Bean
  @Primary
  public ScriptedTranscoder scriptedTranscoder() {
    return new ScriptedTranscoder();
  }

  /** Hands back URLs shaped like presigned ones and remembers everything it was asked to do. */
  public static class RecordingObjectStore implements ObjectStore {

    public final List<String> presignedPuts = new ArrayList<>();
    public final List<String> deleted = new ArrayList<>();
    /** What was written, so a test can assert on the caption body that reached storage. */
    public final Map<String, String> stored = new ConcurrentHashMap<>();

    @Override
    public String presignPut(String bucket, String key, String contentType, Duration validFor) {
      presignedPuts.add(key);
      return "https://" + bucket + ".s3.test/" + key + "?upload=1";
    }

    @Override
    public String presignGet(String bucket, String key, Duration validFor) {
      return "https://" + bucket + ".s3.test/" + key + "?expires=" + validFor.toSeconds();
    }

    @Override
    public void putText(String bucket, String key, String contentType, String body) {
      stored.put(key, body);
    }

    @Override
    public void delete(String bucket, String key) {
      deleted.add(key);
      stored.remove(key);
    }

    public void reset() {
      presignedPuts.clear();
      deleted.clear();
      stored.clear();
    }
  }

  /** A transcoder whose outcome each test decides. */
  public static class ScriptedTranscoder implements Transcoder {

    private final Map<String, TranscodeOutcome> outcomes = new ConcurrentHashMap<>();
    public final List<String> submitted = new ArrayList<>();

    @Override
    public String submit(String sourceKey, String outputKeyPrefix) {
      String jobId = "job-" + submitted.size();
      submitted.add(sourceKey);
      outcomes.put(jobId, TranscodeOutcome.stillGoing());
      return jobId;
    }

    @Override
    public Optional<TranscodeOutcome> check(String jobId) {
      return Optional.ofNullable(outcomes.get(jobId));
    }

    public void finish(String jobId, int durationSeconds) {
      outcomes.put(jobId, TranscodeOutcome.succeeded(durationSeconds));
    }

    public void fail(String jobId, String reason) {
      outcomes.put(jobId, TranscodeOutcome.failed(reason));
    }

    public void reset() {
      outcomes.clear();
      submitted.clear();
    }
  }
}
