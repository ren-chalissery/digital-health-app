package io.simplicity.training.service.media;

import io.simplicity.training.config.AppProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.mediaconvert.model.AacSettings;
import software.amazon.awssdk.services.mediaconvert.model.AudioCodec;
import software.amazon.awssdk.services.mediaconvert.model.AudioDefaultSelection;
import software.amazon.awssdk.services.mediaconvert.model.AudioCodecSettings;
import software.amazon.awssdk.services.mediaconvert.model.AudioDescription;
import software.amazon.awssdk.services.mediaconvert.model.AudioSelector;
import software.amazon.awssdk.services.mediaconvert.model.ContainerSettings;
import software.amazon.awssdk.services.mediaconvert.model.ContainerType;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.FileGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.GetJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.H264Settings;
import software.amazon.awssdk.services.mediaconvert.model.Input;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.JobStatus;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;
import software.amazon.awssdk.services.mediaconvert.model.Output;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroup;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupType;
import software.amazon.awssdk.services.mediaconvert.model.VideoCodec;
import software.amazon.awssdk.services.mediaconvert.model.VideoCodecSettings;
import software.amazon.awssdk.services.mediaconvert.model.VideoDescription;

/**
 * One 720p H.264 rendition.
 *
 * <p>Multi-bitrate ladders exist to serve viewers on unpredictable connections; these are
 * clinicians on hospital or home broadband watching short videos, and a single MP4 plays everywhere
 * without a player library.
 */
@Slf4j
public class MediaConvertTranscoder implements Transcoder {

  private static final String AUDIO_SELECTOR = "Audio Selector 1";
  private static final int HEIGHT = 720;
  private static final int BITRATE = 2_500_000;

  private final MediaConvertClient mediaConvert;
  private final AppProperties properties;

  public MediaConvertTranscoder(MediaConvertClient mediaConvert, AppProperties properties) {
    this.mediaConvert = mediaConvert;
    this.properties = properties;
  }

  @Override
  public String submit(String sourceKey, String outputKeyPrefix) {
    AppProperties.Media media = properties.media();

    Job job =
        mediaConvert
            .createJob(
                CreateJobRequest.builder()
                    .queue(media.transcodeQueueArn())
                    .role(media.transcodeRoleArn())
                    .settings(
                        JobSettings.builder()
                            .inputs(
                                Input.builder()
                                    .fileInput("s3://" + media.uploadBucket() + "/" + sourceKey)
                                    // An audio description has to name a selector defined here.
                                    // Without one MediaConvert rejects the job outright.
                                    .audioSelectors(
                                        Map.of(
                                            AUDIO_SELECTOR,
                                            AudioSelector.builder()
                                                .defaultSelection(AudioDefaultSelection.DEFAULT)
                                                .build()))
                                    .build())
                            .outputGroups(outputGroup(media.assetBucket(), outputKeyPrefix))
                            .build())
                    .build())
            .job();

    log.info("Submitted transcode {} for {}", job.id(), sourceKey);
    return job.id();
  }

  @Override
  public Optional<TranscodeOutcome> check(String jobId) {
    Job job;
    try {
      job = mediaConvert.getJob(GetJobRequest.builder().id(jobId).build()).job();
    } catch (RuntimeException e) {
      // A job that cannot be read is not a job that failed; leave it alone and ask again.
      log.warn("Could not read transcode job {}", jobId, e);
      return Optional.empty();
    }

    JobStatus status = job.status();
    if (status == JobStatus.COMPLETE) {
      return Optional.of(TranscodeOutcome.succeeded(durationOf(job)));
    }
    if (status == JobStatus.ERROR || status == JobStatus.CANCELED) {
      String reason =
          job.errorMessage() == null ? "Transcoding " + status.toString().toLowerCase() : job.errorMessage();
      return Optional.of(TranscodeOutcome.failed(reason));
    }
    return Optional.of(TranscodeOutcome.stillGoing());
  }

  private Integer durationOf(Job job) {
    if (job.outputGroupDetails() == null) {
      return null;
    }
    return job.outputGroupDetails().stream()
        .flatMap(group -> group.outputDetails().stream())
        .map(detail -> detail.durationInMs())
        .filter(ms -> ms != null && ms > 0)
        .findFirst()
        .map(ms -> (int) Math.round(ms / 1000.0))
        .orElse(null);
  }

  private OutputGroup outputGroup(String bucket, String keyPrefix) {
    return OutputGroup.builder()
        .outputGroupSettings(
            OutputGroupSettings.builder()
                .type(OutputGroupType.FILE_GROUP_SETTINGS)
                .fileGroupSettings(
                    FileGroupSettings.builder()
                        .destination("s3://" + bucket + "/" + keyPrefix)
                        .build())
                .build())
        .outputs(
            List.of(
                Output.builder()
                    .containerSettings(
                        ContainerSettings.builder().container(ContainerType.MP4).build())
                    .videoDescription(
                        VideoDescription.builder()
                            .height(HEIGHT)
                            .codecSettings(
                                VideoCodecSettings.builder()
                                    .codec(VideoCodec.H_264)
                                    .h264Settings(H264Settings.builder().bitrate(BITRATE).build())
                                    .build())
                            .build())
                    .audioDescriptions(
                        AudioDescription.builder()
                            .audioSourceName(AUDIO_SELECTOR)
                            .codecSettings(
                                AudioCodecSettings.builder()
                                    .codec(AudioCodec.AAC)
                                    .aacSettings(
                                        AacSettings.builder()
                                            .bitrate(96_000)
                                            .codingMode("CODING_MODE_2_0")
                                            .sampleRate(48_000)
                                            .build())
                                    .build())
                            .build())
                    .nameModifier("-720p")
                    .build()))
        .build();
  }
}
