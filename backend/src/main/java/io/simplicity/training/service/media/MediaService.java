package io.simplicity.training.service.media;

import io.simplicity.training.config.AppProperties;
import io.simplicity.training.exception.BadRequestException;
import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.exception.ForbiddenException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.MediaAsset;
import io.simplicity.training.model.entity.ModuleSection;
import io.simplicity.training.model.enums.MediaStatus;
import io.simplicity.training.repository.MediaAssetRepository;
import io.simplicity.training.repository.ModuleSectionRepository;
import io.simplicity.training.security.AppPrincipal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Uploading, transcoding, and handing out playback URLs. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

  /** MP4 covers what an author will produce; the rest are what a browser reliably plays. */
  private static final Set<String> ACCEPTED = Set.of("video/mp4", "video/quicktime", "video/webm");

  private final MediaAssetRepository assets;
  private final ModuleSectionRepository sections;
  private final ObjectStore objects;
  private final Transcoder transcoder;
  private final AppProperties properties;

  /**
   * Registers the asset and hands back a URL the browser puts bytes to directly. Nothing passes
   * through this task on the way in: a 500MB upload proxied through a 1GB container is how a task
   * gets killed mid-upload.
   */
  @Transactional
  public Upload register(AppPrincipal actor, UUID orgId, String filename, String contentType, long sizeBytes) {
    requireConfigured();
    if (!ACCEPTED.contains(contentType)) {
      throw new BadRequestException(
          "Videos must be MP4, QuickTime, or WebM. Received " + contentType);
    }
    if (sizeBytes > properties.media().maxUploadBytes()) {
      throw new BadRequestException(
          "Videos must be under " + properties.media().maxUploadBytes() / (1024 * 1024) + "MB");
    }

    // A prefix of its own rather than one built from the asset id: assigning an id to a
    // @GeneratedValue entity makes Spring Data merge instead of persist, and the key only has to
    // be unique.
    String prefix = orgId + "/" + UUID.randomUUID();
    String uploadKey = prefix + "/source";

    MediaAsset asset =
        assets.save(
            MediaAsset.builder()
                .orgId(orgId)
                .filename(filename)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .status(MediaStatus.UPLOADING)
                .uploadKey(uploadKey)
                .createdBy(actor.userId())
                .build());

    return new Upload(
        asset.getId(),
        objects.presignPut(
            properties.media().uploadBucket(), uploadKey, contentType, Duration.ofMinutes(30)));
  }

  /** Called once the browser's PUT has finished. Hands the file to the transcoder. */
  @Transactional
  public MediaAsset uploaded(UUID orgId, UUID assetId) {
    MediaAsset asset = require(orgId, assetId);
    if (asset.getStatus() != MediaStatus.UPLOADING) {
      throw new ConflictException("That upload has already been submitted");
    }

    // MediaConvert writes alongside the source, appending the name modifier and extension to the
    // destination prefix it is given.
    String prefix = prefixOf(asset);
    asset.setTranscodeJobId(transcoder.submit(asset.getUploadKey(), prefix));
    asset.setStatus(MediaStatus.PROCESSING);
    asset.setPlaybackKey(prefix + "source-720p.mp4");
    return assets.save(asset);
  }

  @Transactional(readOnly = true)
  public List<MediaAsset> list(UUID orgId) {
    return assets.findByOrgIdOrderByCreatedAtDesc(orgId);
  }

  @Transactional
  public void delete(UUID orgId, UUID assetId) {
    MediaAsset asset = require(orgId, assetId);

    // Empty the sections pointing at it first, or the foreign key refuses the delete. They keep
    // their writing; they simply lose the video.
    for (ModuleSection section : sections.findByMediaAssetId(assetId)) {
      section.setMediaAssetId(null);
      sections.save(section);
    }
    sections.flush();

    objects.delete(properties.media().uploadBucket(), asset.getUploadKey());
    if (asset.getPlaybackKey() != null) {
      objects.delete(properties.media().assetBucket(), asset.getPlaybackKey());
    }
    if (asset.getCaptionKey() != null) {
      objects.delete(properties.media().assetBucket(), asset.getCaptionKey());
    }
    assets.delete(asset);
  }

  /**
   * A URL a browser can play, minted per request and short-lived.
   *
   * <p>Holding an asset id is not authorisation. The caller has to be entitled to a section that
   * uses it, which the caller-supplied predicate settles — the assignment rule lives in the
   * learning service and is not restated here.
   */
  @Transactional(readOnly = true)
  public String playbackUrl(UUID orgId, UUID assetId, java.util.function.Predicate<UUID> mayReachModule) {
    requireConfigured();
    MediaAsset asset = require(orgId, assetId);
    if (!asset.isPlayable()) {
      throw new ConflictException("That video is not ready yet");
    }

    boolean entitled =
        sections.findByMediaAssetId(assetId).stream()
            .map(ModuleSection::getVersionId)
            .anyMatch(mayReachModule::test);
    if (!entitled) {
      throw new ForbiddenException("That video is not part of a module assigned to you");
    }

    return objects.presignGet(
        properties.media().assetBucket(), asset.getPlaybackKey(), properties.media().playbackUrlTtl());
  }

  /**
   * Stores a caption track beside the video.
   *
   * <p>Validated only far enough to catch the mistake somebody will actually make, which is
   * uploading an SRT: a WebVTT file must begin with the word WEBVTT, and a browser silently ignores
   * a track that does not. Parsing cues properly is the browser's job, and rejecting a file it
   * would have accepted would be worse than letting it through.
   */
  @Transactional
  public MediaAsset setCaptions(UUID orgId, UUID assetId, String webvtt) {
    requireConfigured();
    MediaAsset asset = require(orgId, assetId);

    String trimmed = webvtt == null ? "" : webvtt.stripLeading();
    if (!trimmed.startsWith("WEBVTT")) {
      throw new BadRequestException(
          "Captions must be WebVTT, which begins with the line WEBVTT. An SRT file will not work.");
    }

    String key = prefixOf(asset) + "captions.vtt";
    objects.putText(properties.media().assetBucket(), key, "text/vtt", trimmed);
    asset.setCaptionKey(key);
    return assets.save(asset);
  }

  @Transactional
  public MediaAsset removeCaptions(UUID orgId, UUID assetId) {
    MediaAsset asset = require(orgId, assetId);
    if (asset.getCaptionKey() != null) {
      objects.delete(properties.media().assetBucket(), asset.getCaptionKey());
      asset.setCaptionKey(null);
      assets.save(asset);
    }
    return asset;
  }

  /** The caption track for a video the caller may watch, or empty when there is none. */
  @Transactional(readOnly = true)
  public java.util.Optional<String> captionUrl(UUID orgId, UUID assetId) {
    MediaAsset asset = require(orgId, assetId);
    if (asset.getCaptionKey() == null) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        objects.presignGet(
            properties.media().assetBucket(),
            asset.getCaptionKey(),
            properties.media().playbackUrlTtl()));
  }

  private String prefixOf(MediaAsset asset) {
    return asset.getUploadKey().substring(0, asset.getUploadKey().lastIndexOf('/') + 1);
  }

  private MediaAsset require(UUID orgId, UUID assetId) {
    return assets
        .findByIdAndOrgId(assetId, orgId)
        .orElseThrow(() -> NotFoundException.of("Media asset", assetId));
  }

  private void requireConfigured() {
    if (!properties.media().isConfigured()) {
      throw new ConflictException("Video is not configured in this environment");
    }
  }

  public record Upload(UUID assetId, String uploadUrl) {}
}
