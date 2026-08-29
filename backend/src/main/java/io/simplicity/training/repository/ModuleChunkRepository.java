package io.simplicity.training.repository;

import io.simplicity.training.model.entity.ModuleChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ModuleChunkRepository extends JpaRepository<ModuleChunk, UUID> {

  @Modifying
  @Query("delete from ModuleChunk c where c.versionId = :versionId")
  void deleteByVersionId(UUID versionId);

  /**
   * Nearest neighbours within one organisation.
   *
   * <p>Native because the embedding column is pgvector, which Hibernate does not model. The joins
   * are the boundary that matters: only the current published version of a module that is not
   * archived. A draft is not content and an archive is meant to be unreachable, so neither may be
   * retrievable however good the similarity is.
   *
   * @return rows of chunk id, module id, module title, section title, content, and cosine distance
   */
  @Query(
      value =
          """
          select c.id, c.module_id, c.module_title, c.section_title, c.content,
                 c.embedding <=> cast(:embedding as vector) as distance
          from module_chunk c
          join module m on m.id = c.module_id
          where c.org_id = :orgId
            and m.archived_at is null
            and c.version_id = (
              select v.id from module_version v
              where v.module_id = c.module_id and v.status = 'PUBLISHED'
              order by v.version_number desc limit 1
            )
          order by distance
          limit :limit
          """,
      nativeQuery = true)
  List<Object[]> findNearest(UUID orgId, String embedding, int limit);
}
