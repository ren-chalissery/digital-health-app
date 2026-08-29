package io.simplicity.training;

import org.springframework.boot.SpringApplication;

/** Runs the application locally with Testcontainers-backed Postgres and Redis. */
public class TestTrainingApplication {

  public static void main(String[] args) {
    SpringApplication.from(TrainingApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
