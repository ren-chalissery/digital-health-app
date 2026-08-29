package io.simplicity.training.model.entity;

import io.simplicity.training.model.enums.MediaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A video in an organisation's library, referenced by however many sections want it. */
@Entity
@Table(name = "media_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

  @Id @GeneratedValue private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(nullable = false)
  private String filename;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MediaStatus status;

  @Column(name = "failure_reason")
  private String failureReason;

  /** Where the browser put the original. Expired by a lifecycle rule a week later. */
  @Column(name = "upload_key", nullable = false)
  private String uploadKey;

  /** Where MediaConvert wrote the transcoded file. Null until it is ready. */
  @Column(name = "playback_key")
  private String playbackKey;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  @Column(name = "transcode_job_id")
  private String transcodeJobId;

  @Column(name = "created_by")
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public boolean isPlayable() {
    return status == MediaStatus.READY && playbackKey != null;
  }
}
