package com.axplatform.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.axplatform.common.JsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** Output limits must pause a chunk without applying partial proposals or paid automatic retries. */
class AiResponseLimitsTest {
    private final JsonCodec json = new JsonCodec();
    private final HttpClient client = mock(HttpClient.class);
    private final List<Map<?, ?>> measured = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();

    private OpenAiResponses adapter() {
        return new OpenAiResponses(new AiSettings(new MockEnvironment()
                .withProperty("ax.integrations.openai.api-key", "TEST_ONLY_NOT_AN_API_KEY")
                .withProperty("ax.integrations.openai.max-tool-rounds", "1")), json, client);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> response(int status, Object body) {
        var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(json.write(body).getBytes(StandardCharsets.UTF_8));
        return response;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void reply(int status, Object body) {
        HttpResponse<byte[]> prepared = response(status, body);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(prepared));
    }

    private OpenAiResponses.Context context() {
        return new OpenAiResponses.Context() {
            public void checkpoint() {}
            public Object read(String name) { throw new AssertionError("partial result must not read tools"); }
            public void usage(String id, Map<?, ?> usage, long elapsed) { measured.add(usage); }
            public void failed(String id, String code, long elapsed) { failed.add(code); }
        };
    }

    private OpenAiResponses.Failure run(OpenAiResponses.Context context) {
        return catchThrowableOfType(() -> adapter().run("EXCEL", "fixture", null, List.of(),
                Instant.now().plusSeconds(30), context), OpenAiResponses.Failure.class);
    }

    private void oneRequest() {
        verify(client, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void tokenTruncationMetersUsageAndRejectsPartialProposalWithoutRetry() {
        reply(200, Map.of("status", "incomplete", "incomplete_details", Map.of("reason", "max_output_tokens"),
                "usage", Map.of("input_tokens", 17, "output_tokens", 2000),
                "output", List.of(Map.of("type", "function_call", "name", "propose_workflow",
                        "call_id", "partial", "arguments", "{\"sheet\":"))));
        var error = run(context());
        assertThat(error.code()).isEqualTo("AI_OUTPUT_LIMIT");
        assertThat(error.diagnostic()).containsEntry("incompleteReason", "max_output_tokens")
                .containsEntry("generationRequests", 1);
        assertThat(error.getMessage()).contains("같은 구간", "반영하지 않았습니다");
        assertThat(measured).hasSize(1);
        assertThat(measured.get(0).get("output_tokens")).isEqualTo(2000);
        assertThat(failed).isEmpty(); // Known provider usage is settled, not marked unknown.
        oneRequest();
    }

    @Test
    void policyAndUnknownIncompleteReasonsAreNotMisclassifiedAsSizeLimits() {
        for (String reason : List.of("content_filter", "PRIVATE_PROVIDER_TEXT")) {
            reset(client);
            reply(200, Map.of("status", "incomplete", "incomplete_details", Map.of("reason", reason),
                    "output", List.of(), "usage", Map.of()));
            var error = run(context());
            assertThat(error.code()).isEqualTo(reason.equals("content_filter") ? "AI_PROVIDER_POLICY" : "AI_INCOMPLETE");
            assertThat(error.getMessage() + json.write(error.diagnostic())).doesNotContain("PRIVATE_PROVIDER_TEXT");
            oneRequest();
        }
    }

    @Test
    void failedStatusDoesNotPretendToBeTokenTruncation() {
        reply(200, Map.of("status", "failed", "incomplete_details", Map.of("reason", "max_output_tokens"),
                "error", Map.of("message", "PRIVATE_PROVIDER_TEXT")));
        assertThat(run(context()).code()).isEqualTo("AI_INCOMPLETE");
        oneRequest();
    }

    @Test
    void classifiesOnlyExplicitInputLimitsAndPreservesBillingPolicy() {
        Object[][] samples = {
                {400, "context_length_exceeded", "AI_CONTEXT_LIMIT"},
                {400, "max_context_length_exceeded", "AI_CONTEXT_LIMIT"},
                {400, "input_too_long", "AI_CONTEXT_LIMIT"},
                {413, "", "AI_CONTEXT_LIMIT"},
                {400, "invalid_parameter", "AI_PROVIDER_REQUEST"},
                {429, "insufficient_quota", "AI_PROVIDER_BILLING"},
                {429, "rate_limit_exceeded", "AI_PROVIDER_RATE_LIMIT"},
                {400, "image_too_large", "AI_IMAGE_DIMENSIONS"}
        };
        for (Object[] sample : samples) {
            reset(client);
            reply((int) sample[0], Map.of("error", Map.of("code", sample[1], "message", "PRIVATE_PROVIDER_TEXT")));
            var error = run(context());
            assertThat(error.code()).as("HTTP %s %s", sample[0], sample[1]).isEqualTo(sample[2]);
            assertThat(error.diagnostic()).containsEntry("generationRequests", 1);
            assertThat(error.getMessage() + json.write(error.diagnostic())).doesNotContain("PRIVATE_PROVIDER_TEXT");
            oneRequest();
        }
    }

    @Test
    void inputCountLimitStopsBeforeAnyPaidRequest() {
        reply(413, Map.of("error", Map.of("code", "request_too_large")));
        var error = run(new OpenAiResponses.Context() {
            public void checkpoint() {}
            public Object read(String name) { throw new AssertionError("must not read"); }
            public boolean metered() { return true; }
            public String beforeCall(int round, String model, long input, int max) {
                throw new AssertionError("must not reserve generation");
            }
        });
        assertThat(error.code()).isEqualTo("AI_CONTEXT_LIMIT");
        assertThat(error.diagnostic()).containsEntry("generationRequests", 0).containsEntry("stage", "INPUT_TOKENS");
        oneRequest();
    }

    @Test
    void oversizedOutputItemsAndArgumentsHaveExplicitSizeCodes() {
        List<List<?>> outputs = List.of(Collections.nCopies(41, Map.of("type", "reasoning")),
                List.of(Map.of("type", "function_call", "name", "propose_workflow", "call_id", "large",
                        "arguments", "x".repeat(1_000_001))));
        for (List<?> output : outputs) {
            reset(client);
            reply(200, Map.of("status", "completed", "output", output));
            var error = run(context());
            assertThat(error.code()).isEqualTo("AI_RESPONSE_LIMIT");
            assertThat(error.diagnostic()).containsEntry("generationRequests", 1);
            oneRequest();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void boundedHttpResponseCancelsSubscriptionAndClassifiesResponseLimit() {
        AtomicBoolean cancelled = new AtomicBoolean();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    var subscriber = handler.apply(mock(HttpResponse.ResponseInfo.class));
                    subscriber.onSubscribe(new Flow.Subscription() {
                        public void request(long count) {}
                        public void cancel() { cancelled.set(true); }
                    });
                    subscriber.onNext(List.of(ByteBuffer.allocate(2 * 1024 * 1024 + 1)));
                    subscriber.onComplete();
                    return subscriber.getBody().thenApply(bytes -> response(200, Map.of())).toCompletableFuture();
                });
        var error = run(context());
        assertThat(error.code()).isEqualTo("AI_RESPONSE_LIMIT");
        assertThat(error.diagnostic()).containsEntry("generationRequests", 1);
        assertThat(cancelled).isTrue();
        assertThat(failed).containsExactly("AI_RESPONSE_LIMIT");
        assertThat(measured).isEmpty();
        oneRequest();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void asynchronousHttpTimeoutRetainsDistinctCodeAndUnknownUsage() {
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(new HttpTimeoutException("PRIVATE_PROVIDER_TEXT"))));
        var error = run(context());
        assertThat(error.code()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(error.getMessage()).contains("사용량", "자동 재시도하지 않았습니다").doesNotContain("PRIVATE_PROVIDER_TEXT");
        assertThat(error.diagnostic()).containsEntry("generationRequests", 1);
        assertThat(failed).containsExactly("AI_PROVIDER_TIMEOUT");
        assertThat(measured).isEmpty();
        oneRequest();
    }

    @Test
    void roundLimitRetainsAttemptCountAndDoesNotAutomaticallyStartAnotherChunk() {
        reply(200, Map.of("status", "completed", "output", List.of(Map.of("type", "function_call",
                "name", "read_workflow_guide", "call_id", "guide", "arguments", "{}"))));
        var error = run(new OpenAiResponses.Context() {
            public void checkpoint() {}
            public Object read(String name) { return Map.of("guide", "fixture"); }
        });
        assertThat(error.code()).isEqualTo("AI_ROUND_LIMIT");
        assertThat(error.diagnostic()).containsEntry("generationRequests", 1);
        assertThat(error.getMessage()).contains("같은 구간");
        oneRequest();
    }
}
