package com.llamaquill.serviceClients;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ComfyUiClient
{
    public static final String DEFAULT_HOST = "http://localhost:8000";

    public record GeneratedImage(byte[] bytes, String mimeType, String filename, String subfolder, String type)
    {
    }

    public record GenerationResult(String workflowJson, List<GeneratedImage> images)
    {
    }

    private final HttpClient client;
    private String host;

    public ComfyUiClient()
    {
        this(DEFAULT_HOST);
    }

    public ComfyUiClient(String host)
    {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.host = normalizeHost(host);
    }

    public void setHost(String host)
    {
        this.host = normalizeHost(host);
    }

    public String getHost()
    {
        return host;
    }

    private static String normalizeHost(String value)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty())
        {
            normalized = DEFAULT_HOST;
        }
        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public GenerationResult generateImages(String workflowTemplateJson, String promptText, int width, int height, int batchSize)
            throws IOException, InterruptedException
    {
        if (workflowTemplateJson == null || workflowTemplateJson.isBlank())
        {
            throw new IOException("Workflow template is empty");
        }

        long seed = ThreadLocalRandom.current().nextLong(1L, 0x7fff_ffffL);
        String apiPromptJson = applyApiPromptTemplate(workflowTemplateJson, promptText == null ? "" : promptText, seed,
                width, height, batchSize);
        JSONObject apiPrompt = new JSONObject(apiPromptJson);
        String promptId = enqueue(apiPrompt);
        List<ImageRef> refs = waitForImages(promptId, Duration.ofMinutes(3));
        List<GeneratedImage> images = new ArrayList<>(refs.size());
        for (ImageRef ref : refs)
        {
            images.add(fetchImage(ref));
        }
        return new GenerationResult(apiPromptJson, images);
    }

    private String applyApiPromptTemplate(String template, String promptText, long seed, int width, int height, int batchSize)
    {
        String escapedPrompt = JSONObject.quote(promptText);
        if (escapedPrompt.length() >= 2 && escapedPrompt.startsWith("\"") && escapedPrompt.endsWith("\""))
        {
            escapedPrompt = escapedPrompt.substring(1, escapedPrompt.length() - 1);
        }
        int normalizedWidth = Math.max(64, width);
        int normalizedHeight = Math.max(64, height);
        int normalizedBatchSize = Math.max(1, batchSize);
        return template.replace("%{prompt}", escapedPrompt)
                .replace("%{seed}", Long.toString(seed))
                .replace("%{width}", Integer.toString(normalizedWidth))
                .replace("%{height}", Integer.toString(normalizedHeight))
                .replace("%{batch_size}", Integer.toString(normalizedBatchSize));
    }

    private String enqueue(JSONObject apiPrompt) throws IOException, InterruptedException
    {
        JSONObject payload = new JSONObject();
        payload.put("prompt", apiPrompt);
        payload.put("client_id", UUID.randomUUID().toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/prompt"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            String body = response.body() == null ? "" : response.body().trim();
            if (body.length() > 500)
            {
                body = body.substring(0, 500) + "...";
            }
            throw new IOException("ComfyUI returned status " + response.statusCode() + " for /prompt"
                    + (body.isBlank() ? "" : ": " + body));
        }
        JSONObject json = new JSONObject(response.body());
        String promptId = json.optString("prompt_id", "");
        if (promptId.isBlank())
        {
            throw new IOException("ComfyUI did not return prompt_id");
        }
        return promptId;
    }

    private List<ImageRef> waitForImages(String promptId, Duration timeout) throws IOException, InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        IOException last = null;
        while (System.currentTimeMillis() < deadline)
        {
            try
            {
                HistoryPoll poll = tryReadHistoryImages(promptId);
                if (poll.error != null && !poll.error.isBlank())
                {
                    if (poll.statusJson != null && !poll.statusJson.isBlank())
                    {
                        System.out.println("ComfyUI history status for " + promptId + ":");
                        System.out.println(poll.statusJson);
                    }
                    throw new IOException("ComfyUI execution failed: " + poll.error);
                }
                List<ImageRef> refs = poll.refs;
                if (!refs.isEmpty())
                {
                    return refs;
                }
                if (poll.complete)
                {
                    throw new IOException("ComfyUI completed without image outputs");
                }
            }
            catch (IOException e)
            {
                last = e;
            }
            Thread.sleep(750);
        }
        if (last != null)
        {
            throw new IOException("Timed out waiting for ComfyUI images (last error: " + last.getMessage() + ")", last);
        }
        throw new IOException("Timed out waiting for ComfyUI images");
    }

    private HistoryPoll tryReadHistoryImages(String promptId) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/history/" + encode(promptId)))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("ComfyUI returned status " + response.statusCode() + " for /history");
        }

        JSONObject historyRoot = new JSONObject(response.body());
        JSONObject promptHistory = historyRoot.optJSONObject(promptId);
        if (promptHistory == null)
        {
            return new HistoryPoll(List.of(), false, null, null);
        }

        List<ImageRef> refs = collectImageRefs(promptHistory);
        boolean complete = isHistoryComplete(promptHistory);
        String error = extractHistoryError(promptHistory);
        JSONObject status = promptHistory.optJSONObject("status");
        String statusJson = status == null ? null : status.toString(2);
        return new HistoryPoll(refs, complete, error, statusJson);
    }

    private GeneratedImage fetchImage(ImageRef ref) throws IOException, InterruptedException
    {
        String uri = host + "/view?filename=" + encode(ref.filename)
                + "&subfolder=" + encode(ref.subfolder)
                + "&type=" + encode(ref.type);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("ComfyUI returned status " + response.statusCode() + " for /view");
        }
        String mimeType = response.headers().firstValue("Content-Type").orElse("image/png");
        return new GeneratedImage(response.body(), mimeType, ref.filename, ref.subfolder, ref.type);
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static List<ImageRef> collectImageRefs(JSONObject promptHistory)
    {
        List<ImageRef> refs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectImageRefsRecursive(promptHistory, refs, seen);
        return refs;
    }

    private static void collectImageRefsRecursive(Object value, List<ImageRef> refs, Set<String> seen)
    {
        if (value == null || value == JSONObject.NULL)
        {
            return;
        }
        if (value instanceof JSONObject obj)
        {
            String filename = obj.optString("filename", "");
            if (!filename.isBlank())
            {
                String subfolder = obj.optString("subfolder", "");
                String type = obj.optString("type", "output");
                String key = filename + "|" + subfolder + "|" + type;
                if (seen.add(key))
                {
                    refs.add(new ImageRef(filename, subfolder, type));
                }
            }
            for (String key : obj.keySet())
            {
                collectImageRefsRecursive(obj.opt(key), refs, seen);
            }
            return;
        }
        if (value instanceof JSONArray arr)
        {
            for (int i = 0; i < arr.length(); i++)
            {
                collectImageRefsRecursive(arr.opt(i), refs, seen);
            }
        }
    }

    private static boolean isHistoryComplete(JSONObject promptHistory)
    {
        JSONObject status = promptHistory.optJSONObject("status");
        if (status == null)
        {
            return false;
        }
        if (status.has("completed"))
        {
            return status.optBoolean("completed", false);
        }
        String statusStr = status.optString("status_str", "");
        return "success".equalsIgnoreCase(statusStr) || "error".equalsIgnoreCase(statusStr);
    }

    private static String extractHistoryError(JSONObject promptHistory)
    {
        JSONObject status = promptHistory.optJSONObject("status");
        if (status == null)
        {
            return null;
        }
        String statusStr = status.optString("status_str", "");
        if (!"error".equalsIgnoreCase(statusStr))
        {
            return null;
        }
        JSONArray messages = status.optJSONArray("messages");
        if (messages == null || messages.length() == 0)
        {
            return "status=error";
        }
        for (int i = messages.length() - 1; i >= 0; i--)
        {
            Object m = messages.get(i);
            if (!(m instanceof JSONArray pair) || pair.length() < 2)
            {
                continue;
            }
            Object detail = pair.get(1);
            if (detail instanceof JSONObject obj)
            {
                String message = obj.optString("exception_message", "");
                if (message.isBlank())
                {
                    message = obj.optString("error", "");
                }
                if (message.isBlank())
                {
                    message = obj.optString("node_errors", "");
                }
                if (!message.isBlank())
                {
                    return message;
                }
                return obj.toString();
            }
            return String.valueOf(detail);
        }
        return "status=error";
    }

    private record ImageRef(String filename, String subfolder, String type)
    {
    }

    private record HistoryPoll(List<ImageRef> refs, boolean complete, String error, String statusJson)
    {
    }
}
