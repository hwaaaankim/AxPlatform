package com.axplatform.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.axplatform.common.JsonCodec;
import com.axplatform.common.Problem;
import com.axplatform.security.AccountSessionService;
import com.axplatform.security.SessionSubject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;

class AiDiagnosticsAccessTest {
    private final AccountSessionService sessions = mock(AccountSessionService.class);
    private final HttpClient client = mock(HttpClient.class);
    private final AiSettings settings =
            new AiSettings(
                    new MockEnvironment()
                            .withProperty(
                                    "ax.integrations.openai.api-key", "TEST_ONLY_NOT_AN_API_KEY"));
    private final OpenAiResponses provider = new OpenAiResponses(settings, new JsonCodec(), client);
    private final AiService service =
            new AiService(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new JsonCodec(),
                    Clock.systemUTC(),
                    settings,
                    provider,
                    sessions,
                    null);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void successfulReply() {
        HttpResponse<byte[]> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{\"input_tokens\":5}".getBytes(StandardCharsets.UTF_8));
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(reply));
    }

    private SessionSubject subject(boolean admin) {
        return new SessionSubject() {
            public long accountId() {
                return 1L;
            }

            public boolean administrator() {
                return admin;
            }
        };
    }

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void expiredAndMemberSessionsCannotUseDiagnostic() {
        var admin = subject(true);
        var member = subject(false);
        when(sessions.current(member)).thenReturn(true);
        assertThatThrownBy(() -> service.diagnose(admin))
                .isInstanceOfSatisfying(Problem.class, e -> assertThat(e.status()).isEqualTo(401));
        assertThatThrownBy(() -> service.diagnose(member))
                .isInstanceOfSatisfying(Problem.class, e -> assertThat(e.status()).isEqualTo(403));
        verifyNoInteractions(client);
    }

    @Test
    void administratorCanCheckOnceThenMustWait() {
        var admin = subject(true);
        when(sessions.current(admin)).thenReturn(true);
        successfulReply();
        assertThat(service.diagnose(admin)).containsEntry("ok", true);
        assertThatThrownBy(() -> service.diagnose(admin))
                .isInstanceOfSatisfying(
                        Problem.class, e -> assertThat(e.code()).isEqualTo("AI_DIAGNOSTIC_WAIT"));
        verify(client, times(1))
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void sessionRevokedDuringCheckCannotReceiveResult() {
        var admin = subject(true);
        when(sessions.current(admin)).thenReturn(true, false);
        successfulReply();
        assertThatThrownBy(() -> service.diagnose(admin))
                .isInstanceOfSatisfying(Problem.class, e -> assertThat(e.status()).isEqualTo(401));
        verify(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
