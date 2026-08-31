package io.simplicity.training.service;

import io.simplicity.training.config.AppProperties;
import io.simplicity.training.repository.AuditEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops the audit trail remembering where a change came from, once that stops being useful.
 *
 * <p>Recording {@code ip_address} put personal information into a table nothing ever purged.
 * Principle 9 of the Privacy Act 2020 says not to keep personal information longer than it is
 * needed for, and "forever" is not a retention period.
 *
 * <p>The entry survives; only the address expires. Deleting whole entries would answer the privacy
 * question by destroying the audit trail, which is the wrong trade: an entry records who changed
 * what, from user ids the system holds anyway, and that history is why the table exists. The
 * address is the part that is personal information, so it is the part with an expiry.
 *
 * <p>Six months by default, which is long enough to investigate something noticed late — breaches
 * are routinely found months after the fact — and short enough to be a real limit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionJob {

  private final AuditEventRepository auditEvents;
  private final AppProperties properties;

  /**
   * Daily. The window is measured in months, so there is nothing to gain from running it often, and
   * a single statement over a small table costs little when it does run.
   *
   * <p>With more than one task this runs more than once a day, which is harmless: the statement
   * only ever clears addresses already past the window, so a second run finds nothing.
   */
  @Scheduled(cron = "0 15 3 * * *")
  @Transactional
  public void forgetOldAddresses() {
    Instant before = Instant.now().minus(properties.audit().ipRetention());
    int forgotten = auditEvents.forgetAddressesRecordedBefore(before);
    if (forgotten > 0) {
      log.info("Cleared the source address of {} audit entries older than {}", forgotten, before);
    }
  }
}
