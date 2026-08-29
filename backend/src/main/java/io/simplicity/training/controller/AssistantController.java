package io.simplicity.training.controller;

import io.simplicity.training.model.response.AssistantResponses.AnswerResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.assistant.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Questions about the organisation's training content. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/assistant")
@RequiredArgsConstructor
@PreAuthorize("@authz.isOrgMember(#orgId)")
@Tag(name = "Assistant", description = "Questions answered from the training material")
public class AssistantController {

  private final AssistantService assistant;

  @PostMapping("/questions")
  @Operation(
      operationId = "askAssistant",
      summary = "Ask a question about the training",
      description =
          "Answers only from published modules in this organisation, with citations. When the "
              + "material does not cover the question it says so rather than guessing, and no "
              + "model is called. It never reads reflections and never gives clinical advice.")
  public AnswerResponse ask(@PathVariable UUID orgId, @Valid @RequestBody AskRequest request) {
    return assistant.ask(CurrentPrincipal.require(), orgId, request.question().trim());
  }

  public record AskRequest(@NotBlank @Size(max = 1000) String question) {}
}
