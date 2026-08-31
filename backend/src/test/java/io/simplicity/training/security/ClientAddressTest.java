package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Which entry of {@code X-Forwarded-For} is worth recording.
 *
 * <p>The header is a chain and the load balancer appends to it, so everything before the last entry
 * came from the caller and can say anything. Taking the first is the obvious reading and the wrong
 * one, and an audit trail that faithfully records a forgery is worse than one with a blank column.
 */
class ClientAddressTest {

  @Test
  void takesTheEntryTheProxyAppendedRatherThanTheOneTheCallerClaimed() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.5");

    assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.5");
  }

  @Test
  void usesTheOnlyEntryWhenThereIsJustOne() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.5");

    assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.5");
  }

  @Test
  void fallsBackToThePeerWhenThereIsNoHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.7");

    assertThat(ClientAddress.of(request)).isEqualTo("198.51.100.7");
  }

  @Test
  void ignoresABlankHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "   ");
    request.setRemoteAddr("198.51.100.7");

    assertThat(ClientAddress.of(request)).isEqualTo("198.51.100.7");
  }

  /** The header is caller-supplied, so its length is too. */
  @Test
  void refusesToRecordMoreThanAnAddressWorthOfIt() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "x".repeat(5000));

    assertThat(ClientAddress.of(request)).hasSize(45);
  }

  @Test
  void isNullWhenThereIsNoRequestAtAll() {
    // Scheduled work — the transcode poller records audit entries with no request behind them.
    assertThat(ClientAddress.current()).isNull();
  }
}
