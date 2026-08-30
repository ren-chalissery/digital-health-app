package io.simplicity.training.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The address a request actually came from.
 *
 * <p>Not {@code getRemoteAddr()}, which behind the load balancer is the load balancer.
 */
public final class ClientAddress {

  /** Long enough for a full IPv6 address with an embedded IPv4 suffix, and no longer. */
  private static final int MAX_LENGTH = 45;

  private ClientAddress() {}

  /**
   * The client's address for the request in flight, or null when there is no request.
   *
   * <p>Null is normal rather than exceptional: scheduled work such as the transcode poller records
   * audit entries with no request behind them.
   */
  public static String current() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      return of(servlet.getRequest());
    }
    return null;
  }

  /**
   * The <em>last</em> entry in {@code X-Forwarded-For}, not the first.
   *
   * <p>The header is a chain, and the load balancer appends the address it received the connection
   * from. Everything before that was supplied by the caller and can say anything at all, so taking
   * the first entry — which is the obvious reading, and what most examples do — records whatever an
   * attacker chose to claim. Only the entry the trusted proxy appended is worth keeping.
   */
  static String of(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return truncate(request.getRemoteAddr());
    }

    String[] hops = forwarded.split(",");
    return truncate(hops[hops.length - 1].trim());
  }

  private static String truncate(String address) {
    if (address == null || address.isBlank()) {
      return null;
    }
    return address.length() > MAX_LENGTH ? address.substring(0, MAX_LENGTH) : address;
  }
}
