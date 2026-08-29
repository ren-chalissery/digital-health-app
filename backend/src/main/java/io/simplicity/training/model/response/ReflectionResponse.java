package io.simplicity.training.model.response;

import java.time.Instant;
import java.util.UUID;

/** One journal entry. Only ever returned to the person who wrote it. */
public record ReflectionResponse(
    UUID id, String title, String body, Instant createdAt, Instant updatedAt) {}
