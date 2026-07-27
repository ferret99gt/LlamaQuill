package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                {"message":{"role":"assistant","content":"!"},"done":true,"prompt_eval_count":42}
                """;

        String result = client.chat(
                List.of(new ChatMessage("user", "Continue.")),
                GenerationSettings.defaults());

        assertEquals("Hello \"world\"\n\u2603!", result);
        assertEquals(42, client.getLastPromptEvalCount());
        assertEquals("POST", client.lastStreamRequest.method());
        assertEquals("/api/chat", client.lastStreamRequest.uri().getPath());
        assertEquals("application/json", client.lastStreamRequest.headers().firstValue("Content-Type").orElseThrow());
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
        assertEquals(-1, client.getLastPromptEvalCount());
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

    private static final class StubOllamaClient extends OllamaClient
    {
        private int stringStatus = 200;
        private String stringBody = "{\"models\":[]}";
        private int streamStatus = 200;
        private String streamBody = """
                {"message":{"role":"assistant","content":""},"done":true}
                """;
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
            InputStream body = new ByteArrayInputStream(streamBody.getBytes(StandardCharsets.UTF_8));
            return response(request, streamStatus, body);
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
