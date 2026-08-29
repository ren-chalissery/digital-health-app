package io.simplicity.training.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

/** Amazon Titan Text Embeddings v2, which returns 1024 dimensions by default. */
@RequiredArgsConstructor
public class BedrockEmbedder implements Embedder {

  static final String MODEL = "amazon.titan-embed-text-v2:0";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final BedrockRuntimeClient bedrock;

  @Override
  public float[] embed(String text) {
    ObjectNode request = JSON.createObjectNode();
    request.put("inputText", text);

    try {
      JsonNode response =
          JSON.readTree(
              bedrock
                  .invokeModel(
                      InvokeModelRequest.builder()
                          .modelId(MODEL)
                          .contentType("application/json")
                          .body(SdkBytes.fromUtf8String(JSON.writeValueAsString(request)))
                          .build())
                  .body()
                  .asUtf8String());

      JsonNode embedding = response.get("embedding");
      float[] vector = new float[embedding.size()];
      for (int i = 0; i < embedding.size(); i++) {
        vector[i] = (float) embedding.get(i).asDouble();
      }
      return vector;
    } catch (Exception e) {
      throw new IllegalStateException("Could not embed text for retrieval", e);
    }
  }
}
