package io.simplicity.training;

import io.simplicity.training.support.TestJwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
@SpringBootTest
class TrainingApplicationTests {

  @Test
  void contextLoads() {}
}
