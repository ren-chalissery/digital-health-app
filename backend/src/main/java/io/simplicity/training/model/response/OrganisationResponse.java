package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.OrganisationType;
import java.time.Instant;
import java.util.UUID;

public record OrganisationResponse(
    UUID id,
    String name,
    String slug,
    OrganisationType organisationType,
    String country,
    Instant createdAt) {}
