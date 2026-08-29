package io.simplicity.training.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
    @NotBlank @Size(max = 150) String name, @Size(max = 1000) String description) {}
