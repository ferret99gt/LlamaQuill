package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class OllamaClientContractTest
{
    @Test
    void readsModelNamesOnlyFromTheModelsArray() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.stringBody = """
                {
                  "name": "root-decoy",
                  "models": [
                    {"name": "alpha:latest", "details": {"name": "detail-decoy"}},
                    {"name": "\\u03b2eta:Q6_K"}
                  ]
                }
                """;

        assertEquals(List.of("alpha:latest", "\u03b2eta:Q6_K"), client.listModels());
        assertEquals("GET", client.lastStringRequest.method());
        assertEquals("/api/tags", client.lastStringRequest.uri().getPath());
    }

    @Test
    void rejectsMalformedModelListJson()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.stringBody = "{\"models\":[{\"size\":123}]}";

        IOException error = assertThrows(IOException.class, client::listModels);

        assertTrue(error.getMessage().contains("models[0] has no name"));
    }

    @Test
    void includesStructuredErrorDetailsFromFailedModelListRequest()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.stringStatus = 503;
        client.stringBody = """
                {"error":{"code":503,"message":"model registry unavailable","type":"server_error"}}
                """;

        IOException error = assertThrows(IOException.class, client::listModels);

        assertEquals("Ollama returned status 503 for /api/tags: model registry unavailable", error.getMessage());
    }

    @Test
    void aggregatesStructuredChatEventsAndCapturesPromptTokens() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"Hello \\"world\\""},"done":false}
                {"message":{"role":"assistant","content":"\\n\\u2603"},"done":false}
                {"model":"test-model","message":{"role":"assistant","content":"!"},"done":true,"done_reason":"stop","prompt_eval_count":42,"eval_count":7,"total_duration":2500000000,"load_duration":500000000,"prompt_eval_duration":750000000,"eval_duration":1250000000}
                """;

        OllamaChatResult result = client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults());

        assertEquals("Hello \"world\"\n\u2603!", result.content());
        assertEquals("test-model", result.model());
        assertEquals(42, result.promptEvalCount());
        assertEquals(7, result.evalCount());
        assertEquals("42 tokens processed", result.promptTokensProcessedLabel());
        assertEquals("1.25 s generation", result.generationDurationLabel());
        assertEquals("stop", result.doneReason());
        assertEquals(2_500_000_000L, result.totalDurationNanos());
        assertEquals(500_000_000L, result.loadDurationNanos());
        assertEquals(750_000_000L, result.promptEvalDurationNanos());
        assertEquals(1_250_000_000L, result.evalDurationNanos());
        assertTrue(result.clientRequestDurationNanos() >= 0);
        assertTrue(result.clientRequestDurationLabel().endsWith(" request"));
        assertEquals(0, result.strippedAssistantPrefixCharacters());
        assertEquals("POST", client.lastStreamRequest.method());
        assertEquals("/api/chat", client.lastStreamRequest.uri().getPath());
        assertEquals("application/json", client.lastStreamRequest.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void nonStreamingChatUsesTheChatContractAndReturnsTypedMetadata() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.stringBody = """
                {
                  "model":"equinox:Q6_K",
                  "message":{"role":"assistant","content":"Generated card text."},
                  "done":true,
                  "done_reason":"stop",
                  "prompt_eval_count":123,
                  "eval_count":19,
                  "total_duration":9000
                }
                """;

        OllamaChatResult result = client.chatNonStreaming(
                List.of(new ChatMessage("user", "Generate a card.")),
                GenerationSettings.defaults());
        JSONObject payload = new JSONObject(client.buildChatPayload(
                List.of(new ChatMessage("user", "Generate a card.")),
                GenerationSettings.defaults(),
                false));

        assertEquals("Generated card text.", result.content());
        assertEquals("equinox:Q6_K", result.model());
        assertEquals(123, result.promptEvalCount());
        assertEquals(19, result.evalCount());
        assertEquals("stop", result.doneReason());
        assertEquals(9000L, result.totalDurationNanos());
        assertFalse(payload.getBoolean("stream"));
        assertFalse(payload.getBoolean("think"));
        assertEquals("5m", payload.getString("keep_alive"));
        assertEquals("POST", client.lastStringRequest.method());
        assertEquals("/api/chat", client.lastStringRequest.uri().getPath());
    }

    @Test
    void capturesTheNormalizedOutboundMessageListBeforeTransportFailure()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.stringStatus = 503;
        client.stringBody = "{\"error\":\"offline\"}";
        AtomicReference<OllamaChatRequestSnapshot> captured = new AtomicReference<>();
        client.setChatRequestObserver(captured::set);

        assertThrows(IOException.class, () -> client.chatNonStreaming(
                List.of(
                        new ChatMessage("system", "Write prose."),
                        new ChatMessage("assistant", "First"),
                        new ChatMessage("assistant", " continuation.")),
                GenerationSettings.defaults()));

        OllamaChatRequestSnapshot snapshot = captured.get();
        assertEquals("http://localhost:11434/api/chat", snapshot.endpoint());
        assertEquals(GenerationSettings.DEFAULT_MODEL, snapshot.model());
        assertFalse(snapshot.streaming());
        assertEquals(List.of(
                new ChatMessage("system", "Write prose."),
                new ChatMessage("assistant", "First continuation.")),
                snapshot.messages());
    }

    @Test
    void nonStreamingChatRejectsErrorsAndIncompleteResponses()
    {
        StubOllamaClient failed = new StubOllamaClient();
        failed.stringStatus = 400;
        failed.stringBody = """
                {"error":{"code":400,"message":"invalid sequence","type":"invalid_request_error"}}
                """;
        IOException statusError = assertThrows(IOException.class, () -> failed.chatNonStreaming(
                List.of(new ChatMessage("user", "Generate.")), GenerationSettings.defaults()));
        assertEquals("Ollama returned status 400 for /api/chat: invalid sequence", statusError.getMessage());

        StubOllamaClient incomplete = new StubOllamaClient();
        incomplete.stringBody = """
                {"message":{"role":"assistant","content":"partial"},"done":false}
                """;
        IOException terminalError = assertThrows(IOException.class, () -> incomplete.chatNonStreaming(
                List.of(new ChatMessage("user", "Generate.")), GenerationSettings.defaults()));
        assertEquals("Ollama non-streaming chat response was not terminal", terminalError.getMessage());

        StubOllamaClient missingContent = new StubOllamaClient();
        missingContent.stringBody = """
                {"message":{"role":"assistant"},"done":true}
                """;
        IOException contentError = assertThrows(IOException.class, () -> missingContent.chatNonStreaming(
                List.of(new ChatMessage("user", "Generate.")), GenerationSettings.defaults()));
        assertEquals("Ollama non-streaming chat response has no message.content", contentError.getMessage());
    }

    @Test
    void removesAnExactReturnedAssistantPrefillAndPreservesOnlyTheGeneratedSuffix() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        String firstBlock = "Author's Note: Keep the tense consistent.\n\nYou lift";
        String secondBlock = " the";
        String combinedPrefill = firstBlock + secondBlock;
        client.streamBody = """
                {"message":{"role":"assistant","content":"Author's Note: Keep the tense consistent.\\n\\nYou lift"},"done":false}
                {"message":{"role":"assistant","content":" the sword."},"done":true,"prompt_eval_count":100,"eval_count":3}
                """;
        List<String> streamed = new ArrayList<>();

        OllamaChatResult result = client.chat(
                List.of(
                        new ChatMessage("assistant", firstBlock),
                        new ChatMessage("assistant", secondBlock)),
                GenerationSettings.defaults(),
                streamed::add);

        assertEquals(" sword.", result.content());
        assertEquals(List.of(" sword."), streamed);
        assertEquals(combinedPrefill.length(), result.strippedAssistantPrefixCharacters());
        assertTrue(result.diagnosticSummary().contains("Assistant prefill removed"));
    }

    @Test
    void leavesSuffixOnlyAssistantResponsesUntouched() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":" sword."},"done":true}
                """;
        List<String> streamed = new ArrayList<>();

        OllamaChatResult result = client.chat(
                List.of(new ChatMessage("assistant", "You lift the")),
                GenerationSettings.defaults(),
                streamed::add);

        assertEquals(" sword.", result.content());
        assertEquals(List.of(" sword."), streamed);
        assertEquals(0, result.strippedAssistantPrefixCharacters());
    }

    @Test
    void releasesUserEndedResponseChunksWithoutWaitingForTheTerminalEvent() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"One"},"done":false}
                {"message":{"role":"assistant","content":" two"},"done":false}
                {"message":{"role":"assistant","content":" three."},"done":true}
                """;
        List<String> streamed = new ArrayList<>();

        OllamaChatResult result = client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults(),
                streamed::add);

        assertEquals("One two three.", result.content());
        assertEquals(List.of("One", " two", " three."), streamed);
    }

    @Test
    void recognizesTemplateTrimmedAssistantPrefill()
            throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"You wait. Then the bell rings."},"done":true}
                """;

        OllamaChatResult result = client.chat(
                List.of(new ChatMessage("assistant", "\r\nYou wait.\r\n")),
                GenerationSettings.defaults());

        assertEquals(" Then the bell rings.", result.content());
        assertEquals("You wait.".length(), result.strippedAssistantPrefixCharacters());
    }

    @Test
    void neverStripsContentBasedOnAUserMessage()
            throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"Repeat this exactly, then answer."},"done":true}
                """;

        OllamaChatResult result = client.chat(
                List.of(new ChatMessage("user", "Repeat this exactly, then answer.")),
                GenerationSettings.defaults());

        assertEquals("Repeat this exactly, then answer.", result.content());
        assertEquals(0, result.strippedAssistantPrefixCharacters());
    }

    @Test
    void rejectsMidStreamErrorInsteadOfReturningPartialText()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"partial"},"done":false}
                {"error":{"message":"runner crashed"}}
                """;
        List<String> streamed = new ArrayList<>();

        IOException error = assertThrows(IOException.class, () -> client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults(),
                streamed::add));

        assertEquals("Ollama stream error on line 2: runner crashed", error.getMessage());
        assertEquals(List.of("partial"), streamed);
    }

    @Test
    void readsModelCapabilitiesAndArchitectureContextFromShow() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        client.stringBody = """
                {
                  "capabilities": ["completion", "tools"],
                  "details": {
                    "family": "gemma3",
                    "parameter_size": "31B",
                    "quantization_level": "Q6_K"
                  },
                  "model_info": {
                    "general.architecture": "gemma3",
                    "gemma3.context_length": 131072,
                    "clip.vision.context_length": 4096
                  }
                }
                """;

        OllamaModelDetails details = client.showModel("equinox:Q6_K");

        assertEquals("equinox:Q6_K", details.model());
        assertEquals(131072, details.maxContextLength());
        assertEquals(List.of("completion", "tools"), details.capabilities());
        assertEquals("gemma3", details.family());
        assertEquals("31B", details.parameterSize());
        assertEquals("Q6_K", details.quantization());
        assertEquals("POST", client.lastStringRequest.method());
        assertEquals("/api/show", client.lastStringRequest.uri().getPath());
    }

    @Test
    void rejectsMalformedStreamJson()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"partial"},"done":false}
                definitely-not-json
                """;

        IOException error = assertThrows(IOException.class, () -> client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults()));

        assertTrue(error.getMessage().contains("Invalid Ollama stream line 2 JSON"));
    }

    @Test
    void rejectsStreamThatEndsWithoutTerminalEvent()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamBody = """
                {"message":{"role":"assistant","content":"partial"},"done":false}
                """;

        IOException error = assertThrows(IOException.class, () -> client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults()));

        assertEquals("Ollama stream for /api/chat ended before a terminal done response", error.getMessage());
    }

    @Test
    void includesStructuredErrorDetailsFromFailedChatRequest()
    {
        StubOllamaClient client = new StubOllamaClient();
        client.streamStatus = 400;
        client.streamBody = """
                {"error":{"code":400,"message":"invalid chat sequence","type":"invalid_request_error"}}
                """;

        IOException error = assertThrows(IOException.class, () -> client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults()));

        assertEquals("Ollama returned status 400 for /api/chat: invalid chat sequence", error.getMessage());
    }

    @Test
    void interruptionClosesAnActiveResponseStream() throws Exception
    {
        StubOllamaClient client = new StubOllamaClient();
        BlockingInputStream stream = new BlockingInputStream();
        client.streamInput = stream;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread generation = Thread.ofPlatform().start(() ->
        {
            try
            {
                client.chat(List.of(new ChatMessage("user", "Continue.")), GenerationSettings.defaults());
            }
            catch (Throwable error)
            {
                failure.set(error);
            }
        });

        assertTrue(stream.readEntered.await(2, TimeUnit.SECONDS), "Stream read did not begin.");
        generation.interrupt();
        generation.join(2000);

        assertFalse(generation.isAlive(), "Interrupted generation thread did not finish.");
        assertTrue(stream.closed, "Response stream was not closed.");
        assertTrue(failure.get() instanceof InterruptedException,
                () -> "Expected InterruptedException but got " + failure.get());
    }

    private static final class StubOllamaClient extends OllamaClient
    {
        private int stringStatus = 200;
        private String stringBody = "{\"models\":[]}";
        private int streamStatus = 200;
        private String streamBody = """
                {"message":{"role":"assistant","content":""},"done":true}
                """;
        private InputStream streamInput;
        private HttpRequest lastStringRequest;
        private HttpRequest lastStreamRequest;

        private StubOllamaClient()
        {
            super("http://ollama.test", "test-model");
        }

        @Override
        HttpResponse<String> sendString(HttpRequest request)
        {
            lastStringRequest = request;
            return response(request, stringStatus, stringBody);
        }

        @Override
        HttpResponse<InputStream> sendStream(HttpRequest request)
        {
            lastStreamRequest = request;
            InputStream body = streamInput == null
                    ? new ByteArrayInputStream(streamBody.getBytes(StandardCharsets.UTF_8))
                    : streamInput;
            return response(request, streamStatus, body);
        }
    }

    private static final class BlockingInputStream extends InputStream
    {
        private final CountDownLatch readEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean closed;

        @Override
        public int read() throws IOException
        {
            readEntered.countDown();
            try
            {
                release.await();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IOException("Blocking test stream was interrupted.", e);
            }
            return -1;
        }

        @Override
        public void close()
        {
            closed = true;
            release.countDown();
        }
    }

    private static <T> HttpResponse<T> response(HttpRequest request, int status, T body)
    {
        return new HttpResponse<>()
        {
            @Override
            public int statusCode()
            {
                return status;
            }

            @Override
            public HttpRequest request()
            {
                return request;
            }

            @Override
            public Optional<HttpResponse<T>> previousResponse()
            {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers()
            {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }

            @Override
            public T body()
            {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession()
            {
                return Optional.empty();
            }

            @Override
            public URI uri()
            {
                return request.uri();
            }

            @Override
            public HttpClient.Version version()
            {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
