package io.simplicity.training.service;

import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.Reflection;
import io.simplicity.training.model.request.ReflectionRequests.WriteReflectionRequest;
import io.simplicity.training.model.response.ReflectionResponse;
import io.simplicity.training.repository.ReflectionRepository;
import io.simplicity.training.security.AppPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A clinician's private journal.
 *
 * <p>Every method takes the principal and filters on their user id. There is no administrative
 * path in here on purpose: the absence is the feature.
 */
@Service
@RequiredArgsConstructor
public class ReflectionService {

  private final ReflectionRepository reflections;

  @Transactional(readOnly = true)
  public List<ReflectionResponse> list(AppPrincipal principal, String terms) {
    List<Reflection> found =
        terms == null || terms.isBlank()
            ? reflections.findByUserIdOrderByCreatedAtDesc(principal.userId())
            : reflections.search(principal.userId(), terms.trim());
    return found.stream().map(this::describe).toList();
  }

  @Transactional(readOnly = true)
  public ReflectionResponse get(AppPrincipal principal, UUID id) {
    return describe(require(principal, id));
  }

  @Transactional
  public ReflectionResponse write(AppPrincipal principal, WriteReflectionRequest request) {
    return describe(
        reflections.save(
            Reflection.builder()
                .userId(principal.userId())
                .title(blankToNull(request.title()))
                .body(request.body().trim())
                .build()));
  }

  @Transactional
  public ReflectionResponse edit(AppPrincipal principal, UUID id, WriteReflectionRequest request) {
    Reflection reflection = require(principal, id);
    reflection.setTitle(blankToNull(request.title()));
    reflection.setBody(request.body().trim());
    return describe(reflections.save(reflection));
  }

  @Transactional
  public void delete(AppPrincipal principal, UUID id) {
    reflections.delete(require(principal, id));
  }

  /**
   * Not found rather than forbidden when it belongs to somebody else. A 403 would confirm the id
   * names something real, which is itself a disclosure about a private journal.
   */
  private Reflection require(AppPrincipal principal, UUID id) {
    return reflections
        .findByIdAndUserId(id, principal.userId())
        .orElseThrow(() -> NotFoundException.of("Reflection", id));
  }

  private ReflectionResponse describe(Reflection reflection) {
    return new ReflectionResponse(
        reflection.getId(),
        reflection.getTitle(),
        reflection.getBody(),
        reflection.getCreatedAt(),
        reflection.getUpdatedAt());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
