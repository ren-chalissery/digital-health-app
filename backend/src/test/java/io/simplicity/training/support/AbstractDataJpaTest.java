package io.simplicity.training.support;

import io.simplicity.training.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Persistence tests run against a real PostgreSQL container. The embedded-database replacement is
 * disabled so the Flyway migrations, the citext extension, and the partial unique indexes are all
 * genuinely exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractDataJpaTest {}
