package com.llamaquill.serviceClients;

import com.llamaquill.model.GenerationSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
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

    private final HttpClient client;
    private String host;
    private String model;
    private Boolean tokenizeSupported;
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
        this.tokenizeSupported = null;
    }

    public void setModel(String model)
    {
        this.model = model;
        this.tokenizeSupported = null;
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
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("Ollama returned status " + response.statusCode());
        }
        return Json.extractStringArray(response.body(), "name");
    }

    public int countTokens(String prompt)
    {
        if (Boolean.FALSE.equals(tokenizeSupported))
        {
            return -1;
        }
        try
        {
            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("prompt", prompt == null ? "" : prompt);
            payload.put("text", prompt == null ? "" : prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/api/tokenize"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404)
            {
                tokenizeSupported = Boolean.FALSE;
                return -1;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                return -1;
            }

            JSONObject json = new JSONObject(response.body());
            if (json.has("tokens"))
            {
                JSONArray tokens = json.optJSONArray("tokens");
                if (tokens != null)
                {
                    tokenizeSupported = Boolean.TRUE;
                    return Math.max(0, tokens.length());
                }
            }
            if (json.has("token_ids"))
            {
                JSONArray tokenIds = json.optJSONArray("token_ids");
                if (tokenIds != null)
                {
                    tokenizeSupported = Boolean.TRUE;
                    return Math.max(0, tokenIds.length());
                }
            }
            if (json.has("count"))
            {
                tokenizeSupported = Boolean.TRUE;
                return Math.max(0, json.optInt("count", 0));
            }
            return -1;
        }
        catch (Exception e)
        {
            return -1;
        }
    }

    public String generate(String prompt, GenerationSettings settings) throws IOException, InterruptedException
    {
        lastPromptEvalCount = -1;
        String payload = buildPayload(prompt, settings);
        //System.out.println("Ollama payload:");
        //System.out.println(payload);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(host + "/api/generate")).timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();

        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("Ollama returned status " + response.statusCode());
        }

        StringBuilder sb = new StringBuilder();
        String doneLine = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String chunk = Json.extractStringField(line, "response");
                if (chunk != null)
                {
                    sb.append(chunk);
                }
                if (Json.isDone(line))
                {
                    doneLine = line;
                    break;
                }
            }
        }
        if (doneLine != null)
        {
            Integer promptEvalCount = Json.extractIntField(doneLine, "prompt_eval_count");
            if (promptEvalCount != null && promptEvalCount > 0)
            {
                lastPromptEvalCount = promptEvalCount;
            }
            System.out.println("Ollama final response:");
            System.out.println(doneLine);
        }
        //System.out.println("Ollama response: \n" + sb);
        return sb.toString();
    }

    private String buildPayload(String prompt, GenerationSettings settings)
    {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(Json.escape(model)).append("\"");
        sb.append(",\"prompt\":\"").append(Json.escape(prompt)).append("\"");
        sb.append(",\"raw\":true");
        sb.append(",\"stream\":true");
        sb.append(",\"options\":{");
        sb.append("\"num_ctx\":").append(settings.contextLimit());
        sb.append(",\"temperature\":").append(settings.temperature());
        sb.append(",\"top_k\":").append(settings.topK());
        sb.append(",\"top_p\":").append(settings.topP());
        sb.append(",\"min_p\":").append(settings.minP());
        sb.append(",\"presence_penalty\":").append(settings.presencePenalty());
        sb.append(",\"frequency_penalty\":").append(settings.frequencyPenalty());
        sb.append(",\"repeat_penalty\":").append(settings.repetitionPenalty());
        sb.append(",\"num_predict\":").append(settings.responseLength());
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private static final class Json
    {
        private Json()
        {
        }

        static String escape(String value)
        {
            if (value == null)
            {
                return "";
            }
            StringBuilder sb = new StringBuilder(value.length() + 16);
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                switch (c)
                {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
                }
            }
            return sb.toString();
        }

        static String extractStringField(String jsonLine, String field)
        {
            String key = "\"" + field + "\"";
            int idx = jsonLine.indexOf(key);
            if (idx < 0)
            {
                return null;
            }
            int colon = jsonLine.indexOf(':', idx + key.length());
            if (colon < 0)
            {
                return null;
            }
            int start = jsonLine.indexOf('"', colon + 1);
            if (start < 0)
            {
                return null;
            }
            int i = start + 1;
            StringBuilder sb = new StringBuilder();
            boolean escaping = false;
            while (i < jsonLine.length())
            {
                char c = jsonLine.charAt(i++);
                if (escaping)
                {
                    switch (c)
                    {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        if (i + 3 < jsonLine.length())
                        {
                            String hex = jsonLine.substring(i, i + 4);
                            try
                            {
                                sb.append((char) Integer.parseInt(hex, 16));
                            }
                            catch (NumberFormatException ignored)
                            {
                                sb.append("\\u").append(hex);
                            }
                            i += 4;
                        }
                    }
                    default -> sb.append(c);
                    }
                    escaping = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaping = true;
                    continue;
                }
                if (c == '"')
                {
                    break;
                }
                sb.append(c);
            }
            return sb.toString();
        }

        static boolean isDone(String jsonLine)
        {
            return jsonLine.contains("\"done\":true");
        }

        static Integer extractIntField(String jsonLine, String field)
        {
            if (jsonLine == null || jsonLine.isBlank() || field == null || field.isBlank())
            {
                return null;
            }
            String key = "\"" + field + "\"";
            int idx = jsonLine.indexOf(key);
            if (idx < 0)
            {
                return null;
            }
            int colon = jsonLine.indexOf(':', idx + key.length());
            if (colon < 0)
            {
                return null;
            }
            int i = colon + 1;
            while (i < jsonLine.length() && Character.isWhitespace(jsonLine.charAt(i)))
            {
                i++;
            }
            int start = i;
            if (i < jsonLine.length() && (jsonLine.charAt(i) == '-' || jsonLine.charAt(i) == '+'))
            {
                i++;
            }
            while (i < jsonLine.length() && Character.isDigit(jsonLine.charAt(i)))
            {
                i++;
            }
            if (i <= start)
            {
                return null;
            }
            try
            {
                return Integer.parseInt(jsonLine.substring(start, i));
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        static List<String> extractStringArray(String json, String fieldName)
        {
            List<String> results = new ArrayList<>();
            if (json == null || json.isEmpty())
            {
                return results;
            }
            String key = "\"" + fieldName + "\"";
            int idx = 0;
            while (idx < json.length())
            {
                int keyIndex = json.indexOf(key, idx);
                if (keyIndex < 0)
                {
                    break;
                }
                int colon = json.indexOf(':', keyIndex + key.length());
                if (colon < 0)
                {
                    break;
                }
                int start = json.indexOf('"', colon + 1);
                if (start < 0)
                {
                    break;
                }
                int i = start + 1;
                StringBuilder sb = new StringBuilder();
                boolean escaping = false;
                while (i < json.length())
                {
                    char c = json.charAt(i++);
                    if (escaping)
                    {
                        switch (c)
                        {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'u' -> {
                            if (i + 3 < json.length())
                            {
                                String hex = json.substring(i, i + 4);
                                try
                                {
                                    sb.append((char) Integer.parseInt(hex, 16));
                                }
                                catch (NumberFormatException ignored)
                                {
                                    sb.append("\\u").append(hex);
                                }
                                i += 4;
                            }
                        }
                        default -> sb.append(c);
                        }
                        escaping = false;
                        continue;
                    }
                    if (c == '\\')
                    {
                        escaping = true;
                        continue;
                    }
                    if (c == '"')
                    {
                        break;
                    }
                    sb.append(c);
                }
                results.add(sb.toString());
                idx = i;
            }
            return results;
        }
    }
}
