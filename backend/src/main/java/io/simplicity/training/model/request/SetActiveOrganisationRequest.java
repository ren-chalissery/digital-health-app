package io.simplicity.training.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Which of the caller's organisations they are switching to. */
public record SetActiveOrganisationRequest(@NotNull UUID organisationId) {}
