package io.simplicity.training.service.assistant;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * Claude Haiku, summarising passages it was handed.
 *
 * <p>Haiku rather than a larger model because the work is restating supplied text, not reasoning
 * from scratch, which is what small models do well and cheaply.
 */
@RequiredArgsConstructor
public class BedrockAnswerGenerator implements AnswerGenerator {

  static final String MODEL = "anthropic.claude-haiku-4-5-20251001-v1:0";

  /**
   * The passages are the only permitted source. This is a request rather than a guarantee — the
   * guarantee is the similarity threshold that decides whether this class is called at all — but it
   * matters for the case where retrieval succeeds and the question is still slightly beyond what
   * was retrieved.
   */
  private static final String SYSTEM =
      """
      You answer questions about a training package for mental health professionals, using only \
      the passages supplied with the question.

      Rules, in order of importance:
      1. Use only the supplied passages. Never draw on anything else you know, even if you are \
         confident and even if the passages are nearly relevant.
      2. If the passages do not answer the question, say that the training material does not \
         cover it and suggest raising it in supervision. Do not guess and do not partially answer.
      3. Never give clinical advice about a specific person, a risk situation, or a treatment \
         decision. If asked, say that this is a training resource and direct them to their \
         supervisor or their organisation's crisis procedure.
      4. Be brief. Two or three sentences is usually enough.
      5. Write plainly, in British English, addressing the clinician directly.
      """;

  private final BedrockRuntimeClient bedrock;

  @Override
  public String answer(String question, List<Passage> passages) {
    String context =
        passages.stream()
            .map(
                passage ->
                    "## "
                        + passage.moduleTitle()
                        + (passage.sectionTitle() == null ? "" : " — " + passage.sectionTitle())
                        + "\n"
                        + passage.content())
            .collect(Collectors.joining("\n\n"));

    ConverseResponse response =
        bedrock.converse(
            ConverseRequest.builder()
                .modelId(MODEL)
                .system(SystemContentBlock.fromText(SYSTEM))
                .messages(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(
                            ContentBlock.fromText(
                                "Passages:\n\n" + context + "\n\nQuestion: " + question))
                        .build())
                .inferenceConfig(
                    InferenceConfiguration.builder().maxTokens(400).temperature(0.2F).build())
                .build());

    return response.output().message().content().stream()
        .map(ContentBlock::text)
        .filter(text -> text != null && !text.isBlank())
        .collect(Collectors.joining("\n"))
        .trim();
  }
}
