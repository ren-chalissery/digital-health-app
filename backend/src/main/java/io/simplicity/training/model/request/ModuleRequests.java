package io.simplicity.training.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** What the authoring screen sends. */
public final class ModuleRequests {

  private ModuleRequests() {}

  public record CreateModuleRequest(
      @NotBlank @Size(max = 200) String title, @Size(max = 1000) String summary) {}

  public record UpdateModuleRequest(
      @NotBlank @Size(max = 200) String title, @Size(max = 1000) String summary) {}

  /** Markdown. Rendered through a sanitiser by every client, never stored as HTML. */
  public record SectionInput(
      @NotBlank @Size(max = 200) String title,
      @Size(max = 50_000) String body,
      /** Optional video from the organisation's library. At most one per section. */
      UUID mediaAssetId) {}

  /**
   * The draft's sections in full. Replacing wholesale rather than patching one at a time: editing,
   * reordering, and deleting all arrive together from one screen, and a per-section API would mean
   * reconciling positions across several round trips for nothing.
   */
  public record ReplaceSectionsRequest(
      @NotNull @Size(max = 200) List<@Valid SectionInput> sections) {}

  public record PublishRequest(
      /**
       * Whether this revision should send people who completed an earlier version back through it.
       * Defaults to false, so a corrected typo costs nobody anything.
       */
      boolean supersedesCompletions) {}

  public record AssignTeamsRequest(@NotNull List<UUID> teamIds) {}
}
