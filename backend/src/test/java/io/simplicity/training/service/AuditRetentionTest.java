package io.simplicity.training.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.simplicity.training.model.entity.AuditEvent;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The audit trail keeps what it did; it stops keeping where it came from.
 *
 * <p>Filling {@code ip_address} added personal information to a table nothing ever purged. Deleting
 * whole entries would answer the privacy question by destroying the audit trail, which is the wrong
 * trade — an entry says who changed what, from user ids the system holds anyway. The address is the
 * personal part, so the address is the part that expires.
 */
class AuditRetentionTest extends AbstractIntegrationTest {

  @Autowired private AuditRetentionJob retention;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void forgetsWhereAnOldEntryCameFrom() {
    UUID id = anEntry(Instant.now().minus(200, ChronoUnit.DAYS), "203.0.113.5");

    retention.forgetOldAddresses();

    assertThat(auditEvents.findById(id))
        .get()
        .satisfies(
            event -> {
              assertThat(event.getIpAddress()).as("the address expires").isNull();
              assertThat(event.getAction()).as("the entry itself does not").isEqualTo("TESTED");
            });
  }

  @Test
  void keepsTheAddressOfARecentEntry() {
    UUID id = anEntry(Instant.now().minus(10, ChronoUnit.DAYS), "203.0.113.5");

    retention.forgetOldAddresses();

    assertThat(auditEvents.findById(id)).get().satisfies(
        event -> assertThat(event.getIpAddress()).isEqualTo("203.0.113.5"));
  }

  @Test
  void isHappyWhenThereIsNothingToForget() {
    anEntry(Instant.now().minus(200, ChronoUnit.DAYS), null);

    retention.forgetOldAddresses();

    assertThat(auditEvents.count()).isEqualTo(1);
  }

  private UUID anEntry(Instant createdAt, String ipAddress) {
    AuditEvent saved =
        auditEvents.saveAndFlush(
            AuditEvent.builder().action("TESTED").ipAddress(ipAddress).build());
    // @CreationTimestamp means Hibernate writes `now` whatever the builder said, so an old entry
    // has to be aged in SQL afterwards.
    jdbc.update(
        "UPDATE audit_event SET created_at = ? WHERE id = ?",
        java.sql.Timestamp.from(createdAt),
        saved.getId());
    return saved.getId();
  }
}
