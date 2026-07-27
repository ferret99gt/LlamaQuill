package com.llamaquill.serviceClients;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OllamaClient
{
    public static final String DEFAULT_HOST = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "hf.co/LatitudeGames/Muse-12B-GGUF:BF16";
    private static final int MAX_ERROR_BODY_BYTES = 4096;
    private static final int MAX_ERROR_MESSAGE_CHARS = 1000;

    private final HttpClient client;
    private String host;
    private String model;
    private volatile int lastPromptEvalCount = -1;

    public OllamaClient()
    {
        this(DEFAULT_HOST, DEFAULT_MODEL);
    }

    public OllamaClient(String host, String model)
    {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.host = host;
        this.model = model;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public String getModel()
    {
        return model;
    }

    public int getLastPromptEvalCount()
    {
        return lastPromptEvalCount;
    }

    public String getHost()
    {
        return host;
    }

    public List<String> listModels() throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(host + "/api/tags")).timeout(Duration.ofSeconds(10)).GET()
                .build();
        HttpResponse<String> response = sendString(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw httpError("/api/tags", response.statusCode(), response.body());
        }
        return parseModelList(response.body());
    }

    public String chat(List<ChatMessage> messages, GenerationSettings settings) throws IOException, InterruptedException
    {
        return executeStreaming("/api/chat", buildChatPayload(messages, settings));
    }

    private String executeStreaming(String path, String payload) throws IOException, InterruptedException
    {
        lastPromptEvalCount = -1;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(host + path)).timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();

        HttpResponse<InputStream> response = sendStream(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            try (InputStream body = response.body())
            {
                throw httpError(path, response.statusCode(), readErrorBody(body));
            }
        }
        if (response.body() == null)
        {
            throw new IOException("Ollama returned an empty response body for " + path);
        }

        StringBuilder sb = new StringBuilder();
        JSONObject terminalEvent = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
        {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;
                if (line.isBlank())
                {
                    continue;
                }

                ChatStreamEvent event = parseChatStreamEvent(line, lineNumber);
                sb.append(event.content());
                if (event.done())
                {
                    terminalEvent = event.json();
                    break;
                }
            }
        }

        if (terminalEvent == null)
        {
            throw new IOException("Ollama stream for " + path + " ended before a terminal done response");
        }

        Object promptEvalCount = terminalEvent.opt("prompt_eval_count");
        if (promptEvalCount instanceof Number number && number.intValue() > 0)
        {
            lastPromptEvalCount = number.intValue();
        }
        System.out.println("Ollama final response:");
        System.out.println(terminalEvent);
        return sb.toString();
    }

    String buildChatPayload(List<ChatMessage> messages, GenerationSettings settings)
    {
        JSONObject payload = new JSONObject();
        payload.put("model", model == null ? "" : model);

        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : normalizeChatMessages(messages))
        {
            JSONObject serialized = new JSONObject();
            serialized.put("role", message.role());
            serialized.put("content", message.content());
            messageArray.put(serialized);
        }
        payload.put("messages", messageArray);
        payload.put("think", false);
        payload.put("stream", true);
        payload.put("options", buildOptions(settings));
        return payload.toString();
    }

    private static List<ChatMessage> normalizeChatMessages(List<ChatMessage> messages)
    {
        List<ChatMessage> normalized = new ArrayList<>();
        for (ChatMessage message : messages == null ? List.<ChatMessage>of() : messages)
        {
            if (message == null || message.role().isBlank() || message.content().isBlank())
            {
                continue;
            }

            // llama.cpp's assistant-prefill path rejects multiple trailing assistant
            // messages before the model template has an opportunity to render them.
            if ("assistant".equals(message.role()) && !normalized.isEmpty()
                    && "assistant".equals(normalized.getLast().role()))
            {
                ChatMessage previous = normalized.getLast();
                normalized.set(normalized.size() - 1,
                        new ChatMessage(previous.role(), previous.content() + message.content()));
                continue;
            }
            normalized.add(message);
        }
        return normalized;
    }

    private JSONObject buildOptions(GenerationSettings settings)
    {
        JSONObject options = new JSONObject();
        options.put("num_ctx", settings.contextLimit());
        if (settings.temperatureEnabled())
        {
            options.put("temperature", settings.temperature());
        }
        if (settings.topKEnabled())
        {
            options.put("top_k", settings.topK());
        }
        if (settings.topPEnabled())
        {
            options.put("top_p", settings.topP());
        }
        if (settings.minPEnabled())
        {
            options.put("min_p", settings.minP());
        }
        if (settings.presencePenaltyEnabled())
        {
            options.put("presence_penalty", settings.presencePenalty());
        }
        if (settings.frequencyPenaltyEnabled())
        {
            options.put("frequency_penalty", settings.frequencyPenalty());
        }
        if (settings.repetitionPenaltyEnabled())
        {
            options.put("repeat_penalty", settings.repetitionPenalty());
        }
        if (settings.responseLengthEnabled())
        {
            options.put("num_predict", settings.responseLength());
        }
        return options;
    }

    HttpResponse<String> sendString(HttpRequest request) throws IOException, InterruptedException
    {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    HttpResponse<InputStream> sendStream(HttpRequest request) throws IOException, InterruptedException
    {
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private static List<String> parseModelList(String body) throws IOException
    {
        JSONObject root = parseObject(body, "Ollama /api/tags response");
        JSONArray models = root.optJSONArray("models");
        if (models == null)
        {
            throw new IOException("Invalid Ollama /api/tags response: missing models array");
        }

        List<String> names = new ArrayList<>(models.length());
        for (int i = 0; i < models.length(); i++)
        {
            JSONObject entry = models.optJSONObject(i);
            if (entry == null)
            {
                throw new IOException("Invalid Ollama /api/tags response: models[" + i + "] is not an object");
            }
            Object nameValue = entry.opt("name");
            if (!(nameValue instanceof String name) || name.isBlank())
            {
                throw new IOException("Invalid Ollama /api/tags response: models[" + i + "] has no name");
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    private static ChatStreamEvent parseChatStreamEvent(String line, int lineNumber) throws IOException
    {
        JSONObject event = parseObject(line, "Ollama stream line " + lineNumber);
        String error = extractErrorMessage(event);
        if (!error.isBlank())
        {
            throw new IOException("Ollama stream error on line " + lineNumber + ": " + error);
        }

        Object doneValue = event.opt("done");
        if (!(doneValue instanceof Boolean done))
        {
            throw new IOException("Invalid Ollama stream line " + lineNumber + ": missing boolean done field");
        }

        String content = "";
        JSONObject message = event.optJSONObject("message");
        if (message == null)
        {
            if (!done)
            {
                throw new IOException("Invalid Ollama stream line " + lineNumber + ": missing message object");
            }
        }
        else
        {
            Object contentValue = message.opt("content");
            if (contentValue == null || contentValue == JSONObject.NULL)
            {
                if (!done)
                {
                    throw new IOException("Invalid Ollama stream line " + lineNumber + ": missing message content");
                }
            }
            else if (contentValue instanceof String text)
            {
                content = text;
            }
            else
            {
                throw new IOException("Invalid Ollama stream line " + lineNumber + ": message content is not text");
            }
        }
        return new ChatStreamEvent(content, done, event);
    }

    private static JSONObject parseObject(String body, String context) throws IOException
    {
        try
        {
            return new JSONObject(body == null ? "" : body);
        }
        catch (JSONException e)
        {
            throw new IOException("Invalid " + context + " JSON: " + e.getMessage(), e);
        }
    }

    private static IOException httpError(String path, int statusCode, String body)
    {
        String detail = extractErrorMessage(body);
        String suffix = detail.isBlank() ? "" : ": " + truncate(detail, MAX_ERROR_MESSAGE_CHARS);
        return new IOException("Ollama returned status " + statusCode + " for " + path + suffix);
    }

    private static String extractErrorMessage(String body)
    {
        if (body == null || body.isBlank())
        {
            return "";
        }
        try
        {
            return extractErrorMessage(new JSONObject(body));
        }
        catch (JSONException ignored)
        {
            return truncate(body.trim(), MAX_ERROR_MESSAGE_CHARS);
        }
    }

    private static String extractErrorMessage(JSONObject json)
    {
        Object error = json.opt("error");
        if (error instanceof String text)
        {
            return text.trim();
        }
        if (error instanceof JSONObject detail)
        {
            String message = detail.optString("message", "").trim();
            return message.isBlank() ? detail.toString() : message;
        }
        Object message = json.opt("message");
        return message instanceof String text ? text.trim() : "";
    }

    private static String readErrorBody(InputStream body) throws IOException
    {
        if (body == null)
        {
            return "";
        }
        byte[] bytes = body.readNBytes(MAX_ERROR_BODY_BYTES + 1);
        boolean truncated = bytes.length > MAX_ERROR_BODY_BYTES;
        int length = truncated ? MAX_ERROR_BODY_BYTES : bytes.length;
        String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
        return truncated ? text + "..." : text;
    }

    private static String truncate(String value, int maxChars)
    {
        if (value == null || value.length() <= maxChars)
        {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private record ChatStreamEvent(String content, boolean done, JSONObject json)
    {
    }
}
