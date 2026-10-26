package com.axplatform.ai;

import com.axplatform.common.JsonCodec;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Scripted local provider responses. No request connects to a provider or uses a real key. */
public final class ContinuationHttpFixture {
    public final List<Long> photoCounts = new CopyOnWriteArrayList<>();
    public final List<Map<String,Object>> sheetReads = new CopyOnWriteArrayList<>();
    public final AtomicInteger requests = new AtomicInteger();
    private final Queue<Map<String,Object>> proposals = new ConcurrentLinkedQueue<>();
    private volatile boolean failNext;

    public ContinuationHttpFixture add(Map<String,Object> proposal) { proposals.add(proposal); return this; }
    public void failNextIncomplete() { failNext = true; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public OpenAiResponses provider(AiSettings settings, JsonCodec json) {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest request = inv.getArgument(0);
            Map<String,Object> payload = json.object(body(request));
            Map<String,Object> result;
            if (request.uri().getPath().endsWith("/input_tokens")) result = Map.of("input_tokens", 100);
            else {
                requests.incrementAndGet();
                List<?> input = (List<?>) payload.get("input");
                long photos = input.stream().filter(v -> v instanceof Map<?,?> m && m.get("content") instanceof List<?>)
                        .flatMap(v -> ((List<?>)((Map<?,?>)v).get("content")).stream())
                        .filter(v -> v instanceof Map<?,?> m && "input_image".equals(m.get("type"))).count();
                photoCounts.add(photos);
                Optional<?> read = input.stream().filter(v -> v instanceof Map<?,?> m && "function_call_output".equals(m.get("type")) && "sheet".equals(m.get("call_id"))).findFirst();
                if (failNext) {
                    failNext = false;
                    result = Map.of("status", "incomplete", "incomplete_details", Map.of("reason", "max_output_tokens"), "output", List.of(), "usage", Map.of("input_tokens", 100, "output_tokens", 20));
                } else if (read.isEmpty()) {
                    result = Map.of("status", "completed", "output", List.of(function("read_workflow_guide", "{}", "guide"), function("read_current_draft", "{}", "draft"), function("read_current_sheet", "{}", "sheet")), "usage", Map.of("input_tokens", 100, "output_tokens", 20));
                } else {
                    sheetReads.add(json.object((String)((Map<?,?>)read.get()).get("output")));
                    Map<String,Object> proposal = proposals.poll();
                    if (proposal == null) throw new AssertionError("Unexpected additional generation request");
                    result = Map.of("status", "completed", "output", List.of(function("propose_workflow", json.write(proposal), "proposal")), "usage", Map.of("input_tokens", 100, "output_tokens", 20));
                }
            }
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(json.write(result).getBytes(StandardCharsets.UTF_8));
            return CompletableFuture.completedFuture(response);
        });
        return new OpenAiResponses(settings, json, client);
    }
    private static Map<String,Object> function(String name, String args, String id) { return Map.of("type", "function_call", "name", name, "arguments", args, "call_id", id); }
    private static String body(HttpRequest request) {
        var out = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) { byte[] data = new byte[b.remaining()]; b.get(data); out.writeBytes(data); }
            public void onComplete() { }
            public void onError(Throwable t) { throw new AssertionError(t); }
        });
        return out.toString(StandardCharsets.UTF_8);
    }
}
