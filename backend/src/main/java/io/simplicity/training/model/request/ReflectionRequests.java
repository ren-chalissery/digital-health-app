package io.simplicity.training.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ReflectionRequests {

  private ReflectionRequests() {}

  public record WriteReflectionRequest(
      @Size(max = 200) String title, @NotBlank @Size(max = 20_000) String body) {}
}
