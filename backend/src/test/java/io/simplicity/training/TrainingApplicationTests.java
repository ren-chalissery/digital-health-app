package io.simplicity.training;

import io.simplicity.training.support.TestJwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
@SpringBootTest
// The application refuses to start without the clients whose tokens it should accept, so the
// context that proves it starts has to supply them.
@TestPropertySource(
    properties = {
      "app.cognito.issuer-uri=https://cognito-idp.test.amazonaws.com/test-pool",
      "app.cognito.client-ids=test-client"
    })
class TrainingApplicationTests {

  @Test
  void contextLoads() {}
}
