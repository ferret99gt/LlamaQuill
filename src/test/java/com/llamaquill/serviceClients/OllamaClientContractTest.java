package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
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
        assertEquals("stop", result.doneReason());
        assertEquals(2_500_000_000L, result.totalDurationNanos());
        assertEquals(500_000_000L, result.loadDurationNanos());
        assertEquals(750_000_000L, result.promptEvalDurationNanos());
        assertEquals(1_250_000_000L, result.evalDurationNanos());
        assertEquals(0, result.strippedAssistantPrefixCharacters());
        assertEquals("POST", client.lastStreamRequest.method());
        assertEquals("/api/chat", client.lastStreamRequest.uri().getPath());
        assertEquals("application/json", client.lastStreamRequest.headers().firstValue("Content-Type").orElseThrow());
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

        OllamaChatResult result = client.chat(
                List.of(
                        new ChatMessage("assistant", firstBlock),
                        new ChatMessage("assistant", secondBlock)),
                GenerationSettings.defaults());

        assertEquals(" sword.", result.content());
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

        OllamaChatResult result = client.chat(
                List.of(new ChatMessage("assistant", "You lift the")),
                GenerationSettings.defaults());

        assertEquals(" sword.", result.content());
        assertEquals(0, result.strippedAssistantPrefixCharacters());
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

        IOException error = assertThrows(IOException.class, () -> client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults()));

        assertEquals("Ollama stream error on line 2: runner crashed", error.getMessage());
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
