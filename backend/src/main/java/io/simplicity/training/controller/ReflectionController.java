package io.simplicity.training.controller;

import io.simplicity.training.model.request.ReflectionRequests.WriteReflectionRequest;
import io.simplicity.training.model.response.ReflectionResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.ReflectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A clinician's own reflections.
 *
 * <p>Mounted under {@code /me} rather than under an organisation, because that is what these are:
 * one person's notes on their own practice. There is no administrative route to them anywhere in
 * the API, which is the point rather than an omission.
 */
@RestController
@RequestMapping("/api/v1/me/reflections")
@RequiredArgsConstructor
@Tag(name = "Reflections", description = "The signed-in clinician's private journal")
public class ReflectionController {

  private final ReflectionService reflections;

  @GetMapping
  @Operation(
      operationId = "listReflections",
      summary = "The caller's reflections, newest first, or those matching a search")
  public List<ReflectionResponse> list(@RequestParam(required = false) String q) {
    return reflections.list(CurrentPrincipal.require(), q);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "writeReflection", summary = "Write a reflection")
  public ReflectionResponse write(@Valid @RequestBody WriteReflectionRequest request) {
    return reflections.write(CurrentPrincipal.require(), request);
  }

  @GetMapping("/{reflectionId}")
  @Operation(
      operationId = "getReflection",
      summary = "Read one",
      description = "Somebody else's returns 404, because a 403 would confirm that it exists.")
  public ReflectionResponse get(@PathVariable UUID reflectionId) {
    return reflections.get(CurrentPrincipal.require(), reflectionId);
  }

  @PutMapping("/{reflectionId}")
  @Operation(operationId = "editReflection", summary = "Edit one")
  public ReflectionResponse edit(
      @PathVariable UUID reflectionId, @Valid @RequestBody WriteReflectionRequest request) {
    return reflections.edit(CurrentPrincipal.require(), reflectionId, request);
  }

  @DeleteMapping("/{reflectionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteReflection", summary = "Delete one")
  public void delete(@PathVariable UUID reflectionId) {
    reflections.delete(CurrentPrincipal.require(), reflectionId);
  }
}
