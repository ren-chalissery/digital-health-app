package io.simplicity.training.controller;

import io.simplicity.training.model.entity.MediaAsset;
import io.simplicity.training.model.response.MediaResponses.MediaAssetResponse;
import io.simplicity.training.model.response.MediaResponses.UploadTargetResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.media.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The organisation's video library. Administrators only. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/media")
@RequiredArgsConstructor
@PreAuthorize("@authz.isOrgAdmin(#orgId)")
@Tag(name = "Media", description = "Video for training modules")
public class MediaController {

  private final MediaService media;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "registerUpload",
      summary = "Register a video and get somewhere to put it",
      description =
          "Returns a presigned URL the browser PUTs to directly. Video never passes through the "
              + "API on the way in.")
  public UploadTargetResponse register(
      @PathVariable UUID orgId, @Valid @RequestBody RegisterUploadRequest request) {
    MediaService.Upload upload =
        media.register(
            CurrentPrincipal.require(),
            orgId,
            request.filename(),
            request.contentType(),
            request.sizeBytes());
    return new UploadTargetResponse(upload.assetId(), upload.uploadUrl());
  }

  @PostMapping("/{assetId}/uploaded")
  @Operation(
      operationId = "completeUpload",
      summary = "Report that the upload finished",
      description = "Hands the file to the transcoder; the asset becomes PROCESSING.")
  public MediaAssetResponse uploaded(@PathVariable UUID orgId, @PathVariable UUID assetId) {
    return describe(media.uploaded(orgId, assetId));
  }

  @GetMapping
  @Operation(operationId = "listMedia", summary = "The organisation's video library")
  public List<MediaAssetResponse> list(@PathVariable UUID orgId) {
    return media.list(orgId).stream().map(this::describe).toList();
  }

  @DeleteMapping("/{assetId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "deleteMedia",
      summary = "Delete a video",
      description = "Any section using it keeps its writing and loses the video.")
  public void delete(@PathVariable UUID orgId, @PathVariable UUID assetId) {
    media.delete(orgId, assetId);
  }

  @PutMapping(value = "/{assetId}/captions", consumes = "text/vtt")
  @Operation(
      operationId = "setCaptions",
      summary = "Attach a WebVTT caption track",
      description =
          "Sent as the request body rather than presigned, because a caption file is kilobytes "
              + "where a video is hundreds of megabytes.")
  public MediaAssetResponse setCaptions(
      @PathVariable UUID orgId, @PathVariable UUID assetId, @RequestBody String webvtt) {
    return describe(media.setCaptions(orgId, assetId, webvtt));
  }

  @DeleteMapping("/{assetId}/captions")
  @Operation(operationId = "removeCaptions", summary = "Remove the caption track")
  public MediaAssetResponse removeCaptions(@PathVariable UUID orgId, @PathVariable UUID assetId) {
    return describe(media.removeCaptions(orgId, assetId));
  }

  private MediaAssetResponse describe(MediaAsset asset) {
    return new MediaAssetResponse(
        asset.getId(),
        asset.getFilename(),
        asset.getStatus().name(),
        asset.getFailureReason(),
        asset.getDurationSeconds(),
        asset.getSizeBytes(),
        asset.getCaptionKey() != null,
        asset.getCreatedAt());
  }

  public record RegisterUploadRequest(
      @NotBlank String filename, @NotBlank String contentType, @Positive long sizeBytes) {}
}
