package io.simplicity.training.service.media;

import io.simplicity.training.model.entity.MediaAsset;
import io.simplicity.training.model.enums.MediaStatus;
import io.simplicity.training.repository.MediaAssetRepository;
import io.simplicity.training.service.media.Transcoder.TranscodeOutcome;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks the transcoder whether anything finished.
 *
 * <p>Polling rather than events, which is what tinderbox2_server does with MediaConvert. It runs in
 * the API task because it is a handful of API calls a minute, not the transcode itself. With more
 * than one task several pollers would ask the same question and write the same answer, which is
 * wasteful rather than wrong; an advisory lock is the fix if the service is ever scaled out.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaConvertPoller {

  private final MediaAssetRepository assets;
  private final Transcoder transcoder;

  @Scheduled(fixedDelayString = "PT30S")
  @Transactional
  public void poll() {
    for (MediaAsset asset : assets.findByStatus(MediaStatus.PROCESSING)) {
      if (asset.getTranscodeJobId() == null) {
        continue;
      }
      Optional<TranscodeOutcome> outcome = transcoder.check(asset.getTranscodeJobId());
      if (outcome.isEmpty() || !outcome.get().finished()) {
        continue;
      }

      TranscodeOutcome finished = outcome.get();
      if (finished.succeeded()) {
        asset.setStatus(MediaStatus.READY);
        asset.setDurationSeconds(finished.durationSeconds());
        log.info("Video {} is ready", asset.getId());
      } else {
        asset.setStatus(MediaStatus.FAILED);
        asset.setFailureReason(finished.failureReason());
        asset.setPlaybackKey(null);
        log.warn("Video {} failed to transcode: {}", asset.getId(), finished.failureReason());
      }
      assets.save(asset);
    }
  }
}
