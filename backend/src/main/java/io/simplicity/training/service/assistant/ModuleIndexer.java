package io.simplicity.training.service.assistant;

import io.simplicity.training.model.entity.ModuleSection;
import io.simplicity.training.model.entity.ModuleVersion;
import io.simplicity.training.model.entity.TrainingModule;
import io.simplicity.training.model.enums.ModuleStatus;
import io.simplicity.training.repository.ModuleChunkRepository;
import io.simplicity.training.repository.ModuleRepository;
import io.simplicity.training.repository.ModuleSectionRepository;
import io.simplicity.training.repository.ModuleVersionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Embeds published modules so the assistant can retrieve them.
 *
 * <p>Runs after publishing rather than during it. Doing this inside the publish request would be
 * simpler and wrong: an administrator could not publish while Bedrock was unavailable. Publishing
 * is the product working; the assistant knowing about it a minute later is not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModuleIndexer {

  /** Sections are already the unit an author wrote, so they are the unit retrieved. */
  private static final int MAX_CHUNK_CHARACTERS = 4000;

  private final ModuleVersionRepository versions;
  private final ModuleSectionRepository sections;
  private final ModuleRepository modules;
  private final ModuleChunkRepository chunks;
  private final Embedder embedder;
  private final EntityManager entityManager;

  @Scheduled(fixedDelayString = "PT60S")
  @Transactional
  public void indexPublished() {
    for (ModuleVersion version : versions.findByStatusAndIndexedAtIsNull(ModuleStatus.PUBLISHED)) {
      try {
        index(version);
      } catch (RuntimeException e) {
        // Left unindexed so the next run tries again. A module that cannot be embedded is still a
        // module people can read; only the assistant is behind.
        log.warn("Could not index module version {}", version.getId(), e);
      }
    }
  }

  @Transactional
  public void index(ModuleVersion version) {
    TrainingModule module = modules.findById(version.getModuleId()).orElse(null);
    if (module == null || module.isArchived()) {
      // Nothing to retrieve from, but mark it so the poller stops picking it up.
      version.setIndexedAt(Instant.now());
      versions.save(version);
      return;
    }

    chunks.deleteByVersionId(version.getId());
    chunks.flush();

    List<ModuleSection> body = sections.findByVersionIdOrderByPositionAsc(version.getId());
    for (ModuleSection section : body) {
      String content = section.getBody() == null ? "" : section.getBody().trim();
      if (content.isEmpty()) {
        continue;
      }
      for (String piece : split(content)) {
        insert(module, version, section, piece, embedder.embed(section.getTitle() + "\n" + piece));
      }
    }

    version.setIndexedAt(Instant.now());
    versions.save(version);
    log.info("Indexed {} sections of module {}", body.size(), module.getTitle());
  }

  /**
   * Written natively because the embedding column is pgvector, which Hibernate does not model, and
   * a vector literal is simply its numbers in brackets.
   */
  private void insert(
      TrainingModule module, ModuleVersion version, ModuleSection section, String content, float[] embedding) {
    entityManager
        .createNativeQuery(
            """
            insert into module_chunk
              (id, org_id, module_id, version_id, section_id, module_title, section_title,
               content, embedding)
            values (gen_random_uuid(), :orgId, :moduleId, :versionId, :sectionId, :moduleTitle,
                    :sectionTitle, :content, cast(:embedding as vector))
            """)
        .setParameter("orgId", module.getOrgId())
        .setParameter("moduleId", module.getId())
        .setParameter("versionId", version.getId())
        .setParameter("sectionId", section.getId())
        .setParameter("moduleTitle", module.getTitle())
        .setParameter("sectionTitle", section.getTitle())
        .setParameter("content", content)
        .setParameter("embedding", Vectors.toLiteral(embedding))
        .executeUpdate();
  }

  /** Splits only what is too long to embed in one piece, on paragraph boundaries where it can. */
  private List<String> split(String content) {
    if (content.length() <= MAX_CHUNK_CHARACTERS) {
      return List.of(content);
    }
    List<String> pieces = new java.util.ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String paragraph : content.split("\n\n")) {
      if (current.length() + paragraph.length() > MAX_CHUNK_CHARACTERS && !current.isEmpty()) {
        pieces.add(current.toString().trim());
        current.setLength(0);
      }
      current.append(paragraph).append("\n\n");
    }
    if (!current.isEmpty()) {
      pieces.add(current.toString().trim());
    }
    return pieces;
  }
}
