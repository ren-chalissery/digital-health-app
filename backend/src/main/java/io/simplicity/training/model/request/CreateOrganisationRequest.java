package io.simplicity.training.model.request;

import io.simplicity.training.model.enums.OrganisationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganisationRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull OrganisationType organisationType,
    @Size(max = 80) String country) {}
