package io.simplicity.training.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A retrievable piece of a published module.
 *
 * <p>The embedding is written and searched through native queries: Hibernate has no notion of a
 * pgvector column, and there is nothing to gain from teaching it one.
 */
@Entity
@Table(name = "module_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleChunk {

  @Id @GeneratedValue private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "module_id", nullable = false)
  private UUID moduleId;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Column(name = "section_id")
  private UUID sectionId;

  @Column(name = "module_title", nullable = false)
  private String moduleTitle;

  @Column(name = "section_title")
  private String sectionTitle;

  @Column(nullable = false)
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
