package io.simplicity.training.model;

import java.util.Locale;

/**
 * Email addresses are stored lower-cased.
 *
 * <p>The columns are {@code citext}, which makes the unique indexes case-insensitive — two accounts
 * differing only in case cannot both exist. That protection does not extend to lookups from JPA:
 * Hibernate binds string parameters as {@code varchar}, and PostgreSQL resolves
 * {@code citext = $1::varchar} by casting the column down to {@code text}, producing a
 * case-sensitive comparison. Normalising on the way in and on the way out closes that gap without
 * depending on operator resolution.
 */
public final class Emails {

  private Emails() {}

  public static String normalise(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
