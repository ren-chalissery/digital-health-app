package io.simplicity.training.model.response;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
    UUID id, UUID orgId, String name, String description, int memberCount, Instant createdAt) {}
