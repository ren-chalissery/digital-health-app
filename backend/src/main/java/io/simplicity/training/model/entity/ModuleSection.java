package io.simplicity.training.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One step of a module, belonging to a version rather than to the module itself. */
@Entity
@Table(name = "module_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleSection {

  @Id @GeneratedValue private UUID id;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private String title;

  /** Markdown, rendered through a sanitiser by every client. Never HTML. */
  @Column(nullable = false)
  @Builder.Default
  private String body = "";

  /** At most one video. An author who wants two makes two sections. */
  @Column(name = "media_asset_id")
  private UUID mediaAssetId;
}
