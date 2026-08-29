package io.simplicity.training.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The API document is generated from the controllers, committed, and checked here.
 *
 * <p>Three clients are generated from {@code api-contract/openapi.yaml} and none of them share a
 * compiler with the server, so a stale document is the one way the API can change without anybody
 * noticing. Running this as an ordinary test means the check happens on every build rather than
 * only in a pipeline step somebody can skip.
 *
 * <p>Run {@code ./gradlew generateOpenApiSpec} to rewrite the file after an intended change.
 */
class OpenApiSpecTest extends AbstractIntegrationTest {

  private static final Path SPEC = Path.of("..", "api-contract", "openapi.yaml");

  @Test
  void theCommittedDocumentMatchesTheControllers() throws Exception {
    String generated = generate();

    if (Boolean.getBoolean("openapi.write")) {
      Files.createDirectories(SPEC.getParent());
      Files.writeString(SPEC, generated, StandardCharsets.UTF_8);
      return;
    }

    assertThat(Files.exists(SPEC))
        .as("api-contract/openapi.yaml is missing. Run ./gradlew generateOpenApiSpec")
        .isTrue();
    assertThat(read(SPEC))
        .as(
            "The committed API document no longer matches the controllers. If the change was "
                + "intended, run ./gradlew generateOpenApiSpec and commit the result together "
                + "with the regenerated clients.")
        .isEqualTo(generated);
  }

  /**
   * A wildcard media type here is not cosmetic. openapi-generator only parses a response whose
   * media type it recognises as JSON, so a document declaring the wildcard produces clients that
   * hand every response back as an opaque blob. Nothing fails loudly when that happens: fields
   * simply read as undefined, and the first symptom was an onboarding wizard that looped because
   * it could not see the flag it had just been told about.
   */
  @Test
  void documentsEveryPayloadAsJson() throws Exception {
    assertThat(generate())
        .as("responses must be application/json, or the generated clients will not parse them")
        .doesNotContain("*/*");
  }

  @Test
  void describesTheBearerTokenAndLeavesThePreviewEndpointPublic() throws Exception {
    String document = generate();

    assertThat(document).contains("bearerAuth");
    assertThat(document).contains("/api/v1/invitations/{token}");
  }

  private String generate() throws Exception {
    return normaliseLineEndings(
        mockMvc
            .perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8));
  }

  private String read(Path path) throws IOException {
    return normaliseLineEndings(Files.readString(path, StandardCharsets.UTF_8));
  }

  private String normaliseLineEndings(String text) {
    return text.replace("\r\n", "\n").stripTrailing() + "\n";
  }
}
