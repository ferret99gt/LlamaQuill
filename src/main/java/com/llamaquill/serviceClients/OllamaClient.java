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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaClient implements AutoCloseable
{
    public static final String DEFAULT_HOST = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "hf.co/LatitudeGames/Muse-12B-GGUF:BF16";
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CHAT_RESPONSE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_ERROR_BODY_BYTES = 4096;
    private static final int MAX_ERROR_MESSAGE_CHARS = 1000;
    private static final Pattern CONTEXT_ERROR_TYPE_PATTERN = Pattern.compile(
            "\\\"type\\\"\\s*:\\s*\\\"exceed_context_size_error\\\"");
    private static final Pattern PROMPT_TOKEN_COUNT_PATTERN = Pattern.compile(
            "\\\"n_prompt_tokens\\\"\\s*:\\s*(\\d+)");
    private static final Pattern CONTEXT_TOKEN_COUNT_PATTERN = Pattern.compile(
            "\\\"n_ctx\\\"\\s*:\\s*(\\d+)");

    private final HttpClient client;
    private volatile String host;
    private volatile String model;
    private volatile Consumer<OllamaChatRequestSnapshot> chatRequestObserver = ignored -> { };

    public OllamaClient()
    {
        this(DEFAULT_HOST, DEFAULT_MODEL);
    }

    public OllamaClient(String host, String model)
    {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        setHost(host);
        setModel(model);
    }

    public void setHost(String host)
    {
        this.host = OllamaEndpoint.normalize(host);
    }

    public void setModel(String model)
    {
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    public String getModel()
    {
        return model;
    }

    public String getHost()
    {
        return host;
    }

    public void setChatRequestObserver(Consumer<OllamaChatRequestSnapshot> observer)
    {
        chatRequestObserver = observer == null ? ignored -> { } : observer;
    }

    @Override
    public void close()
    {
        client.shutdownNow();
        try
        {
            client.awaitTermination(Duration.ofSeconds(2));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    public List<String> listModels() throws IOException, InterruptedException
    {
        return listModels(host);
    }

    public List<String> listModels(String baseHost) throws IOException, InterruptedException
    {
        String endpoint = OllamaEndpoint.resolve(baseHost, "/api/tags").toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .timeout(METADATA_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = sendString(request);
        requireSuccess("/api/tags", endpoint, response.statusCode(), response.body());
        return parseModelList(response.body());
    }

    public OllamaModelDetails showModel(String modelName) throws IOException, InterruptedException
    {
        return showModel(host, modelName);
    }

    public OllamaModelDetails showModel(String baseHost, String modelName) throws IOException, InterruptedException
    {
        String requestedModel = requireModel(modelName);
        String endpoint = OllamaEndpoint.resolve(baseHost, "/api/show").toString();
        JSONObject body = new JSONObject().put("model", requestedModel);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .timeout(METADATA_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = sendString(request);
        requireSuccess("/api/show", endpoint, response.statusCode(), response.body());
        return parseModelDetails(requestedModel, response.body());
    }

    public OllamaChatResult chat(List<ChatMessage> messages, GenerationSettings settings)
            throws IOException, InterruptedException
    {
        return chat(messages, settings, ignored -> { });
    }

    public OllamaChatResult chat(List<ChatMessage> messages, GenerationSettings settings,
            Consumer<String> generatedChunkConsumer)
            throws IOException, InterruptedException
    {
        if (settings == null)
        {
            throw new IllegalArgumentException("Generation settings are required.");
        }
        String requestedModel = requireModel(settings.modelName());
        List<ChatMessage> normalizedMessages = normalizeChatMessages(messages);
        AssistantPrefillFilter prefillFilter = new AssistantPrefillFilter(normalizedMessages,
                generatedChunkConsumer == null ? ignored -> { } : generatedChunkConsumer);
        String payload = buildChatPayloadFromNormalized(normalizedMessages, settings);
        String endpoint = OllamaEndpoint.resolve(settings.ollamaHost(), "/api/chat").toString();
        notifyChatRequest(new OllamaChatRequestSnapshot(
                endpoint, requestedModel, true, normalizedMessages));
        return executeStreaming(settings.ollamaHost(), "/api/chat", requestedModel,
                payload, prefillFilter);
    }

    public OllamaChatResult chatNonStreaming(List<ChatMessage> messages, GenerationSettings settings)
            throws IOException, InterruptedException
    {
        if (settings == null)
        {
            throw new IllegalArgumentException("Generation settings are required.");
        }
        String requestedModel = requireModel(settings.modelName());
        List<ChatMessage> normalizedMessages = normalizeChatMessages(messages);
        String endpoint = OllamaEndpoint.resolve(settings.ollamaHost(), "/api/chat").toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .timeout(CHAT_RESPONSE_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildChatPayloadFromNormalized(normalizedMessages, settings, false),
                        StandardCharsets.UTF_8))
                .build();
        notifyChatRequest(new OllamaChatRequestSnapshot(
                endpoint, requestedModel, false, normalizedMessages));
        long requestStartedNanos = System.nanoTime();
        HttpResponse<String> response = sendString(request);
        long clientRequestDurationNanos = elapsedNanos(requestStartedNanos);
        requireSuccess("/api/chat", endpoint, response.statusCode(), response.body());

        JSONObject root = parseObject(response.body(), "Ollama /api/chat response");
        String error = extractErrorMessage(root);
        if (!error.isBlank())
        {
            throw new OllamaException("Ollama non-streaming chat error: " + error,
                    endpoint, response.statusCode(), error);
        }
        if (!root.optBoolean("done", false))
        {
            throw new OllamaException("Ollama non-streaming chat response was not terminal",
                    endpoint, response.statusCode(), "");
        }
        JSONObject message = root.optJSONObject("message");
        if (message == null || !(message.opt("content") instanceof String content))
        {
            throw new OllamaException("Ollama non-streaming chat response has no message.content",
                    endpoint, response.statusCode(), "");
        }
        return new OllamaChatResult(
                root.optString("model", requestedModel),
                content,
                intMetadata(root, "prompt_eval_count"),
                intMetadata(root, "eval_count"),
                root.optString("done_reason", ""),
                longMetadata(root, "total_duration"),
                longMetadata(root, "load_duration"),
                longMetadata(root, "prompt_eval_duration"),
                longMetadata(root, "eval_duration"),
                clientRequestDurationNanos,
                0);
    }

    private void notifyChatRequest(OllamaChatRequestSnapshot snapshot)
    {
        try
        {
            chatRequestObserver.accept(snapshot);
        }
        catch (RuntimeException ignored)
        {
            // Diagnostics must never prevent a model request.
        }
    }

    private OllamaChatResult executeStreaming(String baseHost, String path, String requestedModel, String payload,
            AssistantPrefillFilter prefillFilter)
            throws IOException, InterruptedException
    {
        String endpoint = OllamaEndpoint.resolve(baseHost, path).toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .timeout(CHAT_RESPONSE_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        long requestStartedNanos = System.nanoTime();
        HttpResponse<InputStream> response = sendStream(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            try (InputStream body = response.body())
            {
                throw httpError(path, endpoint, response.statusCode(), readErrorBody(body));
            }
        }
        if (response.body() == null)
        {
            throw new OllamaException("Ollama returned an empty response body for " + path, endpoint, -1, "");
        }

        StringBuilder content = new StringBuilder();
        JSONObject terminalEvent = null;
        InputStream responseBody = response.body();
        try (responseBody;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(responseBody, StandardCharsets.UTF_8)))
        {
            int lineNumber = 0;
            while (true)
            {
                String line = readLineWithTimeout(reader, responseBody, endpoint);
                if (line == null)
                {
                    break;
                }
                lineNumber++;
                if (line.isBlank())
                {
                    continue;
                }

                ChatStreamEvent event = parseChatStreamEvent(line, lineNumber, endpoint);
                String generated = prefillFilter.accept(event.content());
                content.append(generated);
                if (event.done())
                {
                    terminalEvent = event.json();
                    break;
                }
            }
        }

        if (terminalEvent == null)
        {
            throw new OllamaException("Ollama stream for " + path + " ended before a terminal done response",
                    endpoint, -1, "");
        }

        content.append(prefillFilter.finish());
        long clientRequestDurationNanos = elapsedNanos(requestStartedNanos);
        return new OllamaChatResult(
                terminalEvent.optString("model", requestedModel),
                content.toString(),
                intMetadata(terminalEvent, "prompt_eval_count"),
                intMetadata(terminalEvent, "eval_count"),
                terminalEvent.optString("done_reason", ""),
                longMetadata(terminalEvent, "total_duration"),
                longMetadata(terminalEvent, "load_duration"),
                longMetadata(terminalEvent, "prompt_eval_duration"),
                longMetadata(terminalEvent, "eval_duration"),
                clientRequestDurationNanos,
                prefillFilter.removedCharacters());
    }

    private static long elapsedNanos(long startedNanos)
    {
        return Math.max(0L, System.nanoTime() - startedNanos);
    }

    String buildChatPayload(List<ChatMessage> messages, GenerationSettings settings)
    {
        return buildChatPayloadFromNormalized(normalizeChatMessages(messages), settings);
    }

    String buildChatPayload(List<ChatMessage> messages, GenerationSettings settings, boolean stream)
    {
        return buildChatPayloadFromNormalized(normalizeChatMessages(messages), settings, stream);
    }

    private String buildChatPayloadFromNormalized(List<ChatMessage> normalizedMessages, GenerationSettings settings)
    {
        return buildChatPayloadFromNormalized(normalizedMessages, settings, true);
    }

    private String buildChatPayloadFromNormalized(List<ChatMessage> normalizedMessages, GenerationSettings settings,
            boolean stream)
    {
        JSONObject payload = new JSONObject();
        payload.put("model", requireModel(settings.modelName()));

        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : normalizedMessages)
        {
            JSONObject serialized = new JSONObject();
            serialized.put("role", message.role());
            serialized.put("content", message.content());
            messageArray.put(serialized);
        }
        payload.put("messages", messageArray);

        // Thinking is deliberately disabled for narrative generation. It consumes
        // num_predict, disrupts partial-sentence continuation, and can replace prose
        // with incomplete reasoning. GPT-OSS models that require thinking remain an
        // acknowledged compatibility edge case to revisit later.
        payload.put("think", false);
        payload.put("stream", stream);
        payload.put("keep_alive", settings.ollamaKeepAliveMinutes() + "m");
        payload.put("options", buildOptions(settings));
        return payload.toString();
    }

    private static List<String> exactPrefixVariants(String value)
    {
        String original = value == null ? "" : value;
        String trimmed = original.trim();
        String normalized = normalizeLineEndings(original);
        String normalizedTrimmed = normalized.trim();
        List<String> variants = new ArrayList<>(4);
        addPrefixVariant(variants, original);
        addPrefixVariant(variants, trimmed);
        addPrefixVariant(variants, normalized);
        addPrefixVariant(variants, normalizedTrimmed);
        variants.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return variants;
    }

    private static void addPrefixVariant(List<String> variants, String value)
    {
        if (value != null && !value.isEmpty() && !variants.contains(value))
        {
            variants.add(value);
        }
    }

    private static String normalizeLineEndings(String value)
    {
        return value.replace("\r\n", "\n").replace('\r', '\n');
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

    private static JSONObject buildOptions(GenerationSettings settings)
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
        if (settings.typicalPEnabled())
        {
            options.put("typical_p", settings.typicalP());
        }
        if (settings.presencePenaltyEnabled())
        {
            options.put("presence_penalty", settings.presencePenalty());
        }
        if (settings.frequencyPenaltyEnabled())
        {
            options.put("frequency_penalty", settings.frequencyPenalty());
        }
        if (settings.repeatLastNEnabled())
        {
            options.put("repeat_last_n", settings.repeatLastN());
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
            throw new OllamaException("Invalid Ollama /api/tags response: missing models array",
                    "/api/tags", -1, "");
        }

        List<String> names = new ArrayList<>(models.length());
        for (int i = 0; i < models.length(); i++)
        {
            JSONObject entry = models.optJSONObject(i);
            if (entry == null)
            {
                throw new OllamaException("Invalid Ollama /api/tags response: models[" + i + "] is not an object",
                        "/api/tags", -1, "");
            }
            Object nameValue = entry.opt("name");
            if (!(nameValue instanceof String name) || name.isBlank())
            {
                throw new OllamaException("Invalid Ollama /api/tags response: models[" + i + "] has no name",
                        "/api/tags", -1, "");
            }
            names.add(name.trim());
        }
        return List.copyOf(names);
    }

    private static OllamaModelDetails parseModelDetails(String requestedModel, String body) throws IOException
    {
        JSONObject root = parseObject(body, "Ollama /api/show response");
        JSONObject modelInfo = root.optJSONObject("model_info");
        int contextLength = findContextLength(modelInfo);

        List<String> capabilities = new ArrayList<>();
        JSONArray capabilityValues = root.optJSONArray("capabilities");
        if (capabilityValues != null)
        {
            for (int i = 0; i < capabilityValues.length(); i++)
            {
                Object value = capabilityValues.opt(i);
                if (value instanceof String text && !text.isBlank())
                {
                    capabilities.add(text);
                }
            }
        }

        JSONObject details = root.optJSONObject("details");
        return new OllamaModelDetails(
                requestedModel,
                contextLength,
                capabilities,
                details == null ? "" : details.optString("family", ""),
                details == null ? "" : details.optString("parameter_size", ""),
                details == null ? "" : details.optString("quantization_level", ""));
    }

    private static int findContextLength(JSONObject modelInfo)
    {
        if (modelInfo == null)
        {
            return -1;
        }

        String architecture = modelInfo.optString("general.architecture", "").trim();
        if (!architecture.isBlank())
        {
            int exact = positiveInt(modelInfo.opt(architecture + ".context_length"));
            if (exact > 0)
            {
                return exact;
            }
        }

        int fallback = -1;
        for (String key : modelInfo.keySet())
        {
            if (!key.endsWith(".context_length"))
            {
                continue;
            }
            int candidate = positiveInt(modelInfo.opt(key));
            if (candidate <= 0)
            {
                continue;
            }
            if (!key.contains(".vision.") && !key.startsWith("clip."))
            {
                return candidate;
            }
            fallback = Math.max(fallback, candidate);
        }
        return fallback;
    }

    private static ChatStreamEvent parseChatStreamEvent(String line, int lineNumber, String endpoint)
            throws IOException
    {
        JSONObject event = parseObject(line, "Ollama stream line " + lineNumber);
        String error = extractErrorMessage(event);
        if (!error.isBlank())
        {
            throw new OllamaException("Ollama stream error on line " + lineNumber + ": " + error,
                    endpoint, -1, truncate(error, MAX_ERROR_MESSAGE_CHARS));
        }

        Object doneValue = event.opt("done");
        if (!(doneValue instanceof Boolean done))
        {
            throw invalidStream(lineNumber, "missing boolean done field", endpoint);
        }

        String content = "";
        JSONObject message = event.optJSONObject("message");
        if (message == null)
        {
            if (!done)
            {
                throw invalidStream(lineNumber, "missing message object", endpoint);
            }
        }
        else
        {
            Object contentValue = message.opt("content");
            if (contentValue == null || contentValue == JSONObject.NULL)
            {
                if (!done)
                {
                    throw invalidStream(lineNumber, "missing message content", endpoint);
                }
            }
            else if (contentValue instanceof String text)
            {
                content = text;
            }
            else
            {
                throw invalidStream(lineNumber, "message content is not text", endpoint);
            }
        }
        return new ChatStreamEvent(content, done, event);
    }

    private static String readLineWithTimeout(BufferedReader reader, InputStream responseBody, String path)
            throws IOException, InterruptedException
    {
        FutureTask<String> read = new FutureTask<>(reader::readLine);
        Thread thread = Thread.ofVirtual().name("llamaquill-ollama-stream-read").start(read);
        try
        {
            return read.get(STREAM_IDLE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            closeQuietly(responseBody);
            read.cancel(true);
            throw new OllamaException("Ollama stopped sending data for "
                    + STREAM_IDLE_TIMEOUT.toMinutes() + " minutes", path, e);
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io)
            {
                throw io;
            }
            throw new OllamaException("Failed while reading the Ollama response stream", path, cause);
        }
        catch (InterruptedException e)
        {
            closeQuietly(responseBody);
            read.cancel(true);
            thread.interrupt();
            throw e;
        }
    }

    private static void closeQuietly(InputStream stream)
    {
        try
        {
            stream.close();
        }
        catch (IOException ignored)
        {
            // Preserve the timeout or interruption that caused cleanup.
        }
    }

    private static JSONObject parseObject(String body, String context) throws IOException
    {
        try
        {
            return new JSONObject(body == null ? "" : body);
        }
        catch (JSONException e)
        {
            throw new OllamaException("Invalid " + context + " JSON: " + e.getMessage(), context, e);
        }
    }

    private static void requireSuccess(String path, String endpoint, int statusCode, String body) throws IOException
    {
        if (statusCode < 200 || statusCode >= 300)
        {
            throw httpError(path, endpoint, statusCode, body);
        }
    }

    private static OllamaException httpError(String path, String endpoint, int statusCode, String body)
    {
        String detail = extractErrorMessage(body);
        String safeDetail = truncate(detail, MAX_ERROR_MESSAGE_CHARS);
        String suffix = safeDetail.isBlank() ? "" : ": " + safeDetail;
        String message = "Ollama returned status " + statusCode + " for " + path + suffix;
        OllamaContextLimitException contextLimitError = contextLimitError(
                message, path, endpoint, statusCode, safeDetail, body);
        return contextLimitError == null
                ? new OllamaException(message, endpoint, statusCode, safeDetail)
                : contextLimitError;
    }

    private static OllamaContextLimitException contextLimitError(String message, String path, String endpoint,
            int statusCode, String detail, String body)
    {
        if (statusCode != 400 || !"/api/chat".equals(path) || body == null || body.isBlank())
        {
            return null;
        }
        JSONObject root = parseErrorObject(body);
        if (root != null)
        {
            JSONObject error = root.optJSONObject("error");
            if (error != null && "exceed_context_size_error".equals(error.optString("type")))
            {
                return measuredContextLimitError(
                        message, endpoint, statusCode, detail,
                        error.optInt("n_prompt_tokens", -1), error.optInt("n_ctx", -1));
            }
        }

        // Some Ollama-compatible servers prefix an otherwise valid JSON body
        // with non-JSON bytes. The UI previously exposed the whole object in
        // that case, and the context recovery path never saw its token counts.
        // Limit this fallback to the exact structured error marker and fields.
        if (!CONTEXT_ERROR_TYPE_PATTERN.matcher(body).find())
        {
            return null;
        }
        return measuredContextLimitError(
                message, endpoint, statusCode, detail,
                matchedPositiveInt(PROMPT_TOKEN_COUNT_PATTERN, body),
                matchedPositiveInt(CONTEXT_TOKEN_COUNT_PATTERN, body));
    }

    private static OllamaContextLimitException measuredContextLimitError(String message, String endpoint,
            int statusCode, String detail, int promptTokens, int contextLimit)
    {
        if (promptTokens <= 0 || contextLimit <= 0 || promptTokens <= contextLimit)
        {
            return null;
        }
        return new OllamaContextLimitException(
                message, endpoint, statusCode, detail, promptTokens, contextLimit);
    }

    private static int matchedPositiveInt(Pattern pattern, String text)
    {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
        {
            return -1;
        }
        try
        {
            long value = Long.parseLong(matcher.group(1));
            return value <= Integer.MAX_VALUE ? (int) value : -1;
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }

    private static OllamaException invalidStream(int lineNumber, String detail, String endpoint)
    {
        return new OllamaException("Invalid Ollama stream line " + lineNumber + ": " + detail,
                endpoint, -1, detail);
    }

    private static String extractErrorMessage(String body)
    {
        if (body == null || body.isBlank())
        {
            return "";
        }
        JSONObject parsed = parseErrorObject(body);
        if (parsed != null)
        {
            return extractErrorMessage(parsed);
        }
        return truncate(body.trim(), MAX_ERROR_MESSAGE_CHARS);
    }

    private static JSONObject parseErrorObject(String body)
    {
        if (body == null || body.isBlank())
        {
            return null;
        }
        String candidate = body.strip();
        if (!candidate.isEmpty() && candidate.charAt(0) == '\uFEFF')
        {
            candidate = candidate.substring(1).stripLeading();
        }
        try
        {
            return new JSONObject(candidate);
        }
        catch (JSONException ignored)
        {
            int objectStart = candidate.indexOf('{');
            int objectEnd = candidate.lastIndexOf('}');
            if (objectStart < 0 || objectEnd <= objectStart)
            {
                return null;
            }
            try
            {
                return new JSONObject(candidate.substring(objectStart, objectEnd + 1));
            }
            catch (JSONException alsoIgnored)
            {
                return null;
            }
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

    private static int intMetadata(JSONObject source, String key)
    {
        Object value = source.opt(key);
        return value instanceof Number number ? Math.max(-1, number.intValue()) : -1;
    }

    private static long longMetadata(JSONObject source, String key)
    {
        Object value = source.opt(key);
        return value instanceof Number number ? Math.max(-1L, number.longValue()) : -1L;
    }

    private static int positiveInt(Object value)
    {
        if (value instanceof Number number)
        {
            long candidate = number.longValue();
            return candidate > 0 && candidate <= Integer.MAX_VALUE ? (int) candidate : -1;
        }
        return -1;
    }

    private static String requireModel(String modelName)
    {
        String candidate = modelName == null ? "" : modelName.trim();
        if (candidate.isBlank())
        {
            throw new IllegalArgumentException("Ollama model name is required.");
        }
        return candidate;
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

    private static final class AssistantPrefillFilter
    {
        private final List<String> prefixes;
        private final Consumer<String> generatedChunkConsumer;
        private final StringBuilder undecided = new StringBuilder();
        private boolean decided;
        private int removedCharacters;

        private AssistantPrefillFilter(List<ChatMessage> messages, Consumer<String> generatedChunkConsumer)
        {
            this.generatedChunkConsumer = generatedChunkConsumer;
            if (messages == null || messages.isEmpty())
            {
                prefixes = List.of();
                decided = true;
                return;
            }
            ChatMessage lastMessage = messages.getLast();
            if (!"assistant".equals(lastMessage.role()) || lastMessage.content().isEmpty())
            {
                prefixes = List.of();
                decided = true;
                return;
            }
            prefixes = exactPrefixVariants(lastMessage.content());
            decided = prefixes.isEmpty();
        }

        private String accept(String chunk)
        {
            if (chunk == null || chunk.isEmpty())
            {
                return "";
            }
            if (decided)
            {
                generatedChunkConsumer.accept(chunk);
                return chunk;
            }

            undecided.append(chunk);
            String pending = undecided.toString();
            for (String prefix : prefixes)
            {
                if (prefix.startsWith(pending))
                {
                    return "";
                }
            }

            for (String prefix : prefixes)
            {
                if (pending.startsWith(prefix))
                {
                    removedCharacters = prefix.length();
                    return release(pending.substring(prefix.length()));
                }
            }
            return release(pending);
        }

        private String finish()
        {
            if (decided)
            {
                return "";
            }
            String pending = undecided.toString();
            for (String prefix : prefixes)
            {
                if (pending.startsWith(prefix))
                {
                    removedCharacters = prefix.length();
                    return release(pending.substring(prefix.length()));
                }
            }
            return release(pending);
        }

        private String release(String generated)
        {
            decided = true;
            undecided.setLength(0);
            if (!generated.isEmpty())
            {
                generatedChunkConsumer.accept(generated);
            }
            return generated;
        }

        private int removedCharacters()
        {
            return removedCharacters;
        }
    }
}
